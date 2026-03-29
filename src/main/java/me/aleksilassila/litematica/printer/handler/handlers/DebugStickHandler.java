package me.aleksilassila.litematica.printer.handler.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import net.minecraft.world.level.block.Block;

import me.aleksilassila.litematica.printer.config.Configs;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
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
        if (player.getMainHandItem().is(Items.DEBUG_STICK) || InventoryUtils.switchToItems(player,new Item[] { Items.DEBUG_STICK })) {
            BlockState currentState = level.getBlockState(blockPos);
            BlockState targetState = getSameBlockBlockStates(currentState.getBlock(),this.blockStates);
            if (targetState != null && !targetState.equals(currentState)) {
                // 1.找出要更改的狀態(偵測到的第一個)
                Property<?> differentState = getDifferentProperty(currentState, targetState);
                // 2.除錯棒更改目標方塊狀態
                Property<?> debugStickProperty = getDebugStickProperty(player, currentState);
                // 3.比較目標狀態與除錯棒狀態 正確再調整方塊狀態
                if (differentState != null) {
                    if(!differentState.equals(debugStickProperty) && client.gameMode != null) {
                        client.gameMode.startDestroyBlock(blockPos, getPlayerPlacementDirection());
                    }
                    new Action().queueAction(blockPos, getPlayerPlacementDirection(), false, player);
                }
            }
            return;
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

    private BlockState getSameBlockBlockStates(Block block, List<BlockState> blockStates) {
        for (BlockState targetState : this.blockStates) {
            if (targetState.is(targetState.getBlock())) {
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

    private Property<?> getDebugStickProperty(LocalPlayer player, BlockState currentState) {
        Property<?> currentProp = null;

        //#if MC >= 12005
        // 1.20.5+ (1.21.x) 使用 Data Components
        DebugStickState debugStickState = player.getMainHandItem().get(DataComponents.DEBUG_STICK_STATE);
        if (debugStickState != null) {
            currentProp = debugStickState.properties().get(BuiltInRegistries.BLOCK.wrapAsHolder(currentState.getBlock()));
        }
        //#else
        //$$ // 1.18.2 - 1.20.4 使用 NBT
        //$$ CompoundTag nbt = player.getMainHandItem().getTag();
        //$$ if (nbt != null && nbt.contains("DebugProperty", 10)) {
        //$$     CompoundTag debugProp = nbt.getCompound("DebugProperty");
        //$$     String blockKey = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();
        //$$     if (debugProp.contains(blockKey)) {
        //$$         String propName = debugProp.getString(blockKey);
        //$$         for (Property<?> p : currentState.getProperties()) {
        //$$             if (p.getName().equals(propName)) {
        //$$                 currentProp = p;
        //$$                 break;
        //$$             }
        //$$         }
        //$$     }
        //$$ }
        //#endif

        // 如果 NBT 沒紀錄，默認第一個屬性
        if (currentProp == null && !currentState.getProperties().isEmpty()) {
            return currentState.getProperties().iterator().next();
        }
        return currentProp;
    }
}


// ========================================================================================================================
//    private List<Item> toolItemList = Collections.singletonList(Items.DEBUG_STICK);
//    private List<String> targetBlockStateStringList = new ArrayList<>();
//    private List<BlockState> targetBlockStateList = new ArrayList<>();
//    private @Nullable BlockPos blockPos;
//
//    @Override
//    public PrintModeType getPrintModeType() {
//        return PrintModeType.DEBUG_STICK;
//    }
//
//    @Override
//    public ConfigBoolean getCurrentConfig() {
//        return Configs.DebugStick.DEBUG_STICK;
//    }
//
//    @Override
//    public boolean canIterationTest(Printer printer, ClientLevel level, LocalPlayer player, BlockPos pos) {
//        if (pos != null) {
//            if (!PrinterUtils.isPositionInSelectionRange(player, pos, Configs.DebugStick.DEBUG_STICK_SELECTION_TYPE)) {
//                return false;
//            }
//            if (isPlaceCooldown(pos)) {
//                return false;
//            }
//        }
//
//        // 除錯方塊狀態
//        List<String> targetBlockStateStringList = Configs.DebugStick.DEBUG_STICK_BLOCK_STATE_LIST.getStrings();
//        if (!targetBlockStateStringList.equals(this.targetBlockStateStringList)) {
//            this.targetBlockStateStringList = new ArrayList<>(targetBlockStateStringList);
//            if (targetBlockStateStringList.isEmpty()) {
//                return false;
//            }
//            targetBlockStateList.clear();
//            for (String targetBlockState : targetBlockStateStringList) {
//                BlockState tbs = stringToBlockState(targetBlockState);
//                if (tbs == null) continue;
//                targetBlockStateList.add(tbs);
//            }
//        }
//        return true;
//    }
//
//    @Override
//    public void tick(Printer printer, @NotNull Minecraft client, @NotNull ClientLevel level, @NotNull LocalPlayer player) {
//        if (isOpenHandler || switchItem() || BreakManager.hasTargets()) {
//            return;
//        }
//        if (!canIterationTest(printer, level, client.player, blockPos)) {
//            return;
//        }
//        boolean limitedPlaceBlockPerTick = Configs.Placement.PLACE_BLOCKS_PER_TICK.getIntegerValue() != 0;
//        int placeBlocksPerTick = Configs.Placement.PLACE_BLOCKS_PER_TICK.getIntegerValue();
//        while ((blockPos = getBoxBlockPos()) != null) {
//            if (isPlaceCooldown(blockPos)) {
//                continue;
//            }
//            if (!PrinterUtils.isPositionInSelectionRange(player, blockPos, Configs.DebugStick.DEBUG_STICK_SELECTION_TYPE)) {
//                continue;
//            }
//            if (!player.getMainHandItem().is(Items.DEBUG_STICK) && !printer.switchToItems(player, getToolItemsArray())) {
//                continue;
//            }
//            Printer.getInstance().placeCooldownList.put(blockPos, Configs.Placement.PLACE_COOLDOWN.getIntegerValue()); // 冷卻計時器
//
//            BlockState currentState = level.getBlockState(blockPos); // 目標方塊狀態
//            for (BlockState targetState : this.targetBlockStateList) {
//                if (currentState.getBlock() == targetState.getBlock() && !currentState.equals(targetState)) {
//                    // 1.找出要更改的狀態(偵測到的第一個)
//                    Property<?> differentState = getDifferentProperty(currentState,targetState);
//                    // 2.除錯棒更改目標方塊狀態
//                    Property<?> debugStickProperty = getDebugStickProperty(player, currentState);
//                    // 2.比較目標狀態與除錯棒狀態 正確再調整方塊狀態
//                    if (!differentState.equals(debugStickProperty)) {
//                        client.gameMode.startDestroyBlock(blockPos, Direction.UP);
//                    } else {
//                        new PlacementGuide.Action()
//                                .setLookDirection(PlaceUtils.getFillModeFacing().getOpposite())
//                                .queueAction(printer.queue, blockPos, PlaceUtils.getFillModeFacing(), false);
//                        printer.queue.sendQueue(player);
//                    }
//                    if (limitedPlaceBlockPerTick) placeBlocksPerTick--;
//                    setPlaceCooldown(blockPos);
//                    break;
//                }
//            }
//            if (limitedPlaceBlockPerTick && placeBlocksPerTick == 0) break;
//        }
//    }
//
//    private Property<?> getDebugStickProperty(LocalPlayer player, BlockState currentState) {
//        Property<?> currentProp = null;
//
//        //#if MC >= 12005
//        // 1.20.5+ (1.21.x) 使用 Data Components
//        DebugStickState debugStickState = player.getMainHandItem().get(DataComponents.DEBUG_STICK_STATE);
//        if (debugStickState != null) {
//            currentProp = debugStickState.properties().get(BuiltInRegistries.BLOCK.wrapAsHolder(currentState.getBlock()));
//        }
//        //#else
//        //$$ // 1.18.2 - 1.20.4 使用 NBT
//        //$$ CompoundTag nbt = player.getMainHandItem().getTag();
//        //$$ if (nbt != null && nbt.contains("DebugProperty", 10)) {
//        //$$     CompoundTag debugProp = nbt.getCompound("DebugProperty");
//        //$$     String blockKey = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();
//        //$$     if (debugProp.contains(blockKey)) {
//        //$$         String propName = debugProp.getString(blockKey);
//        //$$         for (Property<?> p : currentState.getProperties()) {
//        //$$             if (p.getName().equals(propName)) {
//        //$$                 currentProp = p;
//        //$$                 break;
//        //$$             }
//        //$$         }
//        //$$     }
//        //$$ }
//        //#endif
//
//        // 如果 NBT 沒紀錄，默認第一個屬性
//        if (currentProp == null && !currentState.getProperties().isEmpty()) {
//            return currentState.getProperties().iterator().next();
//        }
//        return currentProp;
//    }
//
//    // 比較目前的狀態和目標狀態
//    private Property<?> getDifferentProperty(BlockState current, BlockState target) {
//        for (Property<?> property : current.getProperties()) {
//            if (!current.getValue(property).equals(target.getValue(property))) {
//                return property; // 抓到不對直接回傳
//            }
//        }
//        return null;
//    }
//
//    public Item[] getToolItemsArray() {
//        return toolItemList.toArray(new Item[0]);
//    }
//}
