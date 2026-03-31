package me.aleksilassila.litematica.printer.handler.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import net.minecraft.world.level.block.Block;

import me.aleksilassila.litematica.printer.config.Configs;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

//#if MC >= 12005
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DebugStickState;
//#else
//$$ import net.minecraft.nbt.CompoundTag;
//#endif

public class DebugStickHandler extends ClientPlayerTickHandler {
    public final static String NAME = "debugStick";

    private List<String> debugStickBlockStates = new ArrayList<>();
    private List<BlockState> blockStates = new ArrayList<>();

    public DebugStickHandler() {
        super(NAME, PrintModeType.DEBUGSTICK, Configs.Core.DEBUG_STICK, Configs.DebugStick.DEBUG_STICK_SELECTION_TYPE, true);
    }

    @Override
    protected int getTickInterval() {
        return Configs.Placement.PLACE_INTERVAL.getIntegerValue();
    }

    @Override
    protected int getMaxExecutions() {
        return Configs.Placement.PLACE_BLOCKS_PER_TICK.getIntegerValue();
    }

    @Override
    protected void preprocess() {
        // 除錯方块
        List<String> debugStickBlockStates = Configs.DebugStick.DEBUG_STICK_BLOCK_STATE_LIST.getStrings();
        if (!debugStickBlockStates.equals(this.debugStickBlockStates)) {
            this.debugStickBlockStates = new ArrayList<>(debugStickBlockStates);
            if (!debugStickBlockStates.isEmpty()) {
                blockStates = new ArrayList<>();
                for (String blockStateString : this.debugStickBlockStates) {
                    this.blockStates.add(stringToBlockState(blockStateString));
                }
            }
        }
    }

    @Override
    protected boolean canIterate() {
        return !this.debugStickBlockStates.isEmpty();
    }

    @Override
    protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
        // 取得除錯棒
        if (!player.getMainHandItem().is(Items.DEBUG_STICK) 
                && !InventoryUtils.switchToItems(player,new Item[] { Items.DEBUG_STICK })) return;
        // 點擊方塊
        BlockState currentState = level.getBlockState(blockPos);
        BlockState targetState = getSameBlockBlockStates(currentState.getBlock(), this.blockStates);
        if (targetState != null && !targetState.equals(currentState)) {
            // 1.找出要更改的狀態(偵測到的第一個)
            Property<?> differentState = getDifferentProperty(currentState, targetState);
            // 2.除錯棒更改目標方塊狀態
            Property<?> debugStickProperty = getDebugStickProperty(player, targetState);
            // // 3.比較目標狀態與除錯棒狀態 正確再調整方塊狀態
            if (differentState != null) {
                if(!differentState.equals(debugStickProperty)) {
                    if (client.gameMode == null) return;
                    client.gameMode.startDestroyBlock(blockPos, Direction.DOWN);
                }
                new Action().queueAction(blockPos, Direction.DOWN, false, player);
                if (ActionManager.INSTANCE.sendQueue(player).needWaitModifyLook) {
                    skipIteration.set(true);
                }
                this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
            }
        }
    }

    private BlockState stringToBlockState(String input) {
        try {
            //#if MC >= 12005
            // 1.20.5+ (1.21.x)
            if (client.level != null) {
                return BlockStateParser.parseForBlock(client.level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), input, true).blockState();
            }
            return null;
            //#elseif MC >= 11903
            //$$ // 1.19.3 - 1.20.4
            //$$ var result = BlockStateParser.parseForBlock(client.level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), new com.mojang.brigadier.StringReader(input), true);
            //$$ return result.blockState();

            //#elseif MC >= 11900
            //$$ // 1.19.0 - 1.19.2
            //$$ com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(input);
            //$$ BlockStateParser parser = new net.minecraft.commands.arguments.blocks.BlockStateParser(reader, false);
            //$$ parser.parse();
            //$$ return parser.blockState();

            //#else
            //$$ // 1.18.2
            //$$ com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(input);
            //$$ BlockStateParser parser = new net.minecraft.commands.arguments.blocks.BlockStateParser(reader, false);
            //$$ parser.parse(false);
            //$$ return parser.getState();
            //#endif
        } catch (Exception e) {
            return null;
        }
    }

    // 取的對應方塊的狀態
    private BlockState getSameBlockBlockStates(Block block, List<BlockState> blockStates) {
        for (BlockState targetState : this.blockStates) {
            if (targetState.is(block)) {
                return targetState;
            }
        }
        return null;
    }

    // 比較目前的狀態和目標狀態
    private Property<?> getDifferentProperty(BlockState current, BlockState target) {
        for (Property<?> property : current.getProperties()) {
            if (!current.getValue(property).equals(target.getValue(property))) {
                return property; // 抓到不對直接回傳
            }
        }
        return null;
    }

    private Property<?> getDebugStickProperty(LocalPlayer player, BlockState blockState) {
        Property<?> currentProp = null;

        //#if MC >= 12005
        // 1.20.5+ (1.21.x) 使用 Data Components
        DebugStickState debugStickState = player.getMainHandItem().get(DataComponents.DEBUG_STICK_STATE);
        if (debugStickState != null) {
            currentProp = debugStickState.properties().get(BuiltInRegistries.BLOCK.wrapAsHolder(blockState.getBlock()));
        }
        //#else
        //$$ // 1.18.2 - 1.20.4 使用 NBT
        //$$ CompoundTag nbt = player.getMainHandItem().getTag();
        //$$ if (nbt != null && nbt.contains("DebugProperty", 10)) {
        //$$     CompoundTag debugProp = nbt.getCompound("DebugProperty");
        //$$     String blockKey = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
        //$$     if (debugProp.contains(blockKey)) {
        //$$         String propName = debugProp.getString(blockKey);
        //$$         for (Property<?> p : blockState.getProperties()) {
        //$$             if (p.getName().equals(propName)) {
        //$$                 currentProp = p;
        //$$                 break;
        //$$             }
        //$$         }
        //$$     }
        //$$ }
        //#endif

        // 如果 NBT 沒紀錄，默認第一個屬性
        if (currentProp == null && !blockState.getProperties().isEmpty()) {
            return blockState.getProperties().iterator().next();
        }
        return currentProp;
    }
}