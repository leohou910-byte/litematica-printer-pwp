package me.aleksilassila.litematica.printer.PinkyWolfy;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.aleksilassila.litematica.printer.printer.zxy.utils.HighlightBlockRenderer;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

import static net.minecraft.world.level.block.ShulkerBoxBlock.FACING;

public class SchematicSync {
    private static final Minecraft client = Minecraft.getInstance();

    // === 功能類 ===
    public record ContainerResult(boolean canOpen, String message) {
    }

    // 回傳選區內要填充的容器List
    public static LinkedList<BlockPos> getSelectionAreaContainerList() {
        LinkedList<BlockPos> containerPositions = new LinkedList<>();
        if (client.level == null) return containerPositions;

        // 取得所有選區
        AreaSelection areaSelection = DataManager.getSelectionManager().getCurrentSelection();
        if (areaSelection == null) return containerPositions;
        List<Box> boxes = areaSelection.getAllSubRegionBoxes();

        // 取得與藍圖相同容器方塊
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return containerPositions;

        for (Box box : boxes) {
            if (box.getPos1() == null || box.getPos2() == null) continue;

            // 加入此選區中的箱子
            for (BlockPos pos : new PrinterBox(box.getPos1(), box.getPos2())) {
                // --- 過濾條件 ---
                // 1.是否為箱子
                if (!InventoryUtils.isInventory(client.level, pos)) continue;

                // 2.是否與藍圖相同
                BlockState schematicBlockState = schematicWorld.getBlockState(pos);
                BlockState realblockState = client.level.getBlockState(pos);
                if (!schematicBlockState.is(realblockState.getBlock())) continue;

                // --- 加入該容器座標 ---
                containerPositions.add(pos);
            }
        }

        return containerPositions;
    }

    // 是否能開啟容器
    public static ContainerResult canContainerOpen(BlockPos pos) {
        if (pos == null || client.level == null) {
            return new ContainerResult(false, "地圖尚未載入或座標無效");
        }
        BlockState blockState = client.level.getBlockState(pos);
        BlockEntity blockEntity = client.level.getBlockEntity(pos);
        boolean getMenuProvider = !(blockState.getMenuProvider(client.level, pos) == null);

        // 是否能獲得容器選單
        if (!getMenuProvider) {
            return new ContainerResult(false, "無法獲取容器選單");
        }

        // 界伏盒特殊處理
        if (blockEntity instanceof ShulkerBoxBlockEntity) {
            // 取得界伏盒開啟時會佔用的 AABB 範圍
            AABB shulkerOpenAabb =
                    //#if MC > 12103
                    Shulker.getProgressDeltaAabb(1.0F, blockState.getValue(FACING), 0.0F, 0.5F, pos.getBottomCenter()).deflate(1.0E-6);
                    //#elseif MC <= 12103 && MC > 12004
                    //$$ Shulker.getProgressDeltaAabb(1.0F, blockState.getValue(FACING), 0.0F, 0.5F).move(pos).deflate(1.0E-6);
                    //#elseif MC <= 12004
                    //$$ Shulker.getProgressDeltaAabb(blockState.getValue(FACING), 0.0f, 0.5f).move(pos).deflate(1.0E-6);
                    //#endif

            // 檢測該範圍內是否有除了自己以外的方塊碰撞
            Iterable<VoxelShape> collisions = client.level.getBlockCollisions(null, shulkerOpenAabb); // 使用 getBlockCollisions 獲取範圍內所有方塊的形狀
            for (VoxelShape shape : collisions) {
                // 如果那一格不是空氣（且不是可穿過的方塊），才判定為阻擋
                if (!shape.isEmpty()) {
                    // 如果撞到的方塊座標就是界伏盒本身所在的座標，代表這是界伏盒自己的蓋子
                    AABB shapeBounds = shape.bounds();
                    //#if MC >= 11900
                    BlockPos collisionPos = BlockPos.containing(shapeBounds.getCenter());
                    //#else
                    //$$ BlockPos collisionPos = new BlockPos(shapeBounds.getCenter());
                    //#endif
                    if (collisionPos.equals(pos)) {
                        continue;
                    }
                    return new ContainerResult(false, "界伏盒開啟方向有其他方塊阻擋");
                }
            }
        }

        return new ContainerResult(true, "");
    }

    // 獲取藍圖容器物品
    public static NonNullList<ItemStack> getContainerItemsFromSchematic(BlockPos worldPos) {
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return NonNullList.create();

        BlockEntity blockEntity = schematicWorld.getBlockEntity(worldPos);
        if (blockEntity instanceof Container container) {
            int size = container.getContainerSize();
            NonNullList<ItemStack> schematicContainerList = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < size; i++) {
                schematicContainerList.set(i, container.getItem(i).copy());
            }
            return schematicContainerList;
        }
        return NonNullList.create();
    }

    // 開啟容器
    public static void openContainer(BlockPos pos){
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        new Action().queueAction(pos, Direction.DOWN, false, player);
    }

    // 填充並回傳缺少物品
    public static Map<Item, Integer> fillContainerAndReturnMissing(NonNullList<ItemStack> schematicContainerItemsNonNullList) {
        Map<Item, Integer> misItems = new HashMap<>();

        if (client.player == null || client.gameMode == null) return misItems;
        AbstractContainerMenu sc = client.player.containerMenu;

        int containerActualSize = sc.slots.getFirst().container.getContainerSize();
        int size = Math.min(schematicContainerItemsNonNullList.size(), containerActualSize);

        for (int i = 0; i < size; i++) {
            ItemStack item1 = sc.slots.get(i).getItem();
            ItemStack item2 = schematicContainerItemsNonNullList.get(i).copy();
            int currNum = item1.getCount();
            int tarNum = item2.getCount();
            boolean same = ItemStack.isSameItemSameComponents(item1, item2);
            if (same && currNum == tarNum) continue; // 數量物品相同時 不和背包交互

            if (same) {
                //有多
                while (currNum > tarNum) {
                    client.gameMode.handleInventoryMouseClick(sc.containerId, i, 0, ClickType.THROW, client.player);
                    currNum--;
                }
            } else {
                //不同直接扔出
                client.gameMode.handleInventoryMouseClick(sc.containerId, i, 1, ClickType.THROW, client.player);
                currNum = 0;
            }

            //背包交互
            for (int j = containerActualSize; j < sc.slots.size(); j++) {
                // 補充完畢直接跳過
                if (currNum == tarNum) break;

                ItemStack playerItem = sc.slots.get(j).getItem();
                boolean same2 = ItemStack.isSameItemSameComponents(item2, playerItem);
                if (playerItem.isEmpty() || !same2) continue;

                // 拿取物品
                int playerItemCount = playerItem.getCount();
                // 拿取背包物品，不管如何都要先拿起
                client.gameMode.handleInventoryMouseClick(sc.containerId, j, 0, ClickType.PICKUP, client.player);
                if (tarNum - currNum >= playerItemCount) { // 可直接全部移入
                    // 把手上的全部放到箱子
                    client.gameMode.handleInventoryMouseClick(sc.containerId, i, 0, ClickType.PICKUP, client.player);
                    currNum += playerItemCount;
                    // 如果有多 放回背包
                    if (!sc.getCarried().isEmpty()) {
                        client.gameMode.handleInventoryMouseClick(sc.containerId, j, 0, ClickType.PICKUP, client.player);
                    }
                } else { // 需要一個一個移入
                    // 一個一個移入
                    for (; currNum < tarNum && playerItemCount > 0; playerItemCount--) {
                        client.gameMode.handleInventoryMouseClick(sc.containerId, i, 1, ClickType.PICKUP, client.player);
                        currNum++;
                    }
                    // 放回背包
                    client.gameMode.handleInventoryMouseClick(sc.containerId, j, 0, ClickType.PICKUP, client.player);
                }
            }

            if (tarNum > currNum) {
                Item type = schematicContainerItemsNonNullList.get(i).getItem();
                misItems.put(type, misItems.getOrDefault(type, 0) + (tarNum - currNum));
            }
        }

        client.player.closeContainer();

        return misItems;
    }

    public static boolean haveAnyItemInInv(Map<Item, Integer> misItems) {
        if(client.player == null) return false;
        AbstractContainerMenu sc = client.player.containerMenu;
        if (!sc.equals(client.player.inventoryMenu)) return false;

        for (int i = 0; i < sc.slots.size(); i++) {
            ItemStack playerItem = sc.slots.get(i).getItem();
            if (!playerItem.isEmpty() && misItems.containsKey(playerItem.getItem())) {
                return true;
            }
        }

        return false;
    }

    // === 主要程式 ===
    public enum SchematicSyncState {
        IDLE,                      // 閒置中
        FIND_AND_CHECK_CONTAINER, // 尋找最近的箱子 並檢查箱子
        FILLING_CONTAINER,         // 正在填充物品
        WAITING_ITEMS              // 等待背包中有相符物品
    }
    public static List<BlockPos> schematicSyncList = new ArrayList<>();
    public static Map<Item, Integer> missingItems = new HashMap<>();
    public static Set<BlockPos> highlightTargetPosList = new LinkedHashSet<>();
    public static Set<BlockPos> highlightMissingPosList = new LinkedHashSet<>();
    public static Set<BlockPos> highlightErrorPosList = new LinkedHashSet<>();
    public static SchematicSyncState schematicSyncState = SchematicSyncState.IDLE;
    public static int schematicSyncCooldown = 0;
    public static int openRetryTimer = 0; // 用於計算超時

    private static BlockPos blockPos = null;
    private static BlockPos tempBlockPos = null;

    // 顏色初始渲染
    private static void getReadyColor() {
        String schematicSyncTarget = "schematicSyncTarget";
        String schematicSyncMissing = "schematicSyncMissing";
        String schematicSyncError = "schematicSyncError";

        HighlightBlockRenderer.createHighlightBlockList(schematicSyncTarget, Configs.Core.SYNC_INVENTORY_COLOR);
        HighlightBlockRenderer.createHighlightBlockList(schematicSyncMissing, Configs.Core.SCHEMATIC_SYNC_INVENTORY_MISSING_COLOR);
        HighlightBlockRenderer.createHighlightBlockList(schematicSyncError, Configs.Core.SCHEMATIC_SYNC_INVENTORY_ERROR_COLOR);

        highlightTargetPosList = HighlightBlockRenderer.getHighlightBlockPosList(schematicSyncTarget);
        highlightMissingPosList = HighlightBlockRenderer.getHighlightBlockPosList(schematicSyncMissing);
        highlightErrorPosList = HighlightBlockRenderer.getHighlightBlockPosList(schematicSyncError);


        highlightTargetPosList.clear();
        highlightMissingPosList.clear();
        highlightErrorPosList.clear();
    }

    // 開始單獨藍圖容器同步
    public static void startSingleSchematicSyncInventory() {
        getReadyColor();
        client.gui.setOverlayMessage(Component.nullToEmpty("單獨藍圖容器同步"), false);
        HitResult hitResult = client.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;

        blockPos = ((BlockHitResult) hitResult).getBlockPos();

        if (!InventoryUtils.isInventory(client.level, blockPos)) {
            client.gui.setOverlayMessage(Component.nullToEmpty("這不是容器"), false);
            return;
        }

        schematicSyncList.clear();
        schematicSyncList.add(blockPos);
        highlightTargetPosList.add(blockPos);
        schematicSyncState = SchematicSyncState.FIND_AND_CHECK_CONTAINER;
    }

    // 開始多藍圖容器同步
    public static void startMultipleSchematicSyncInventory() {
        if (schematicSyncList.isEmpty()) {
            getReadyColor();
            client.gui.setOverlayMessage(Component.nullToEmpty("選區藍圖容器同步"), false);

            schematicSyncList.clear();
            schematicSyncList.addAll(getSelectionAreaContainerList());
            highlightTargetPosList.addAll(schematicSyncList);
            schematicSyncState = SchematicSyncState.FIND_AND_CHECK_CONTAINER;
        } else {
            client.gui.setOverlayMessage(Component.nullToEmpty("藍圖容器同步取消"), false);
            stopSync();
        }
    }

    // 藍圖容器同步
    public static void schematicSyncInv() {
        switch (schematicSyncState) {
            case FIND_AND_CHECK_CONTAINER -> {
                // 已完成全部清單
                if (schematicSyncList.isEmpty()) {
                    schematicSyncState = SchematicSyncState.IDLE;
                    client.gui.setOverlayMessage(Component.nullToEmpty("藍圖容器同步完成"), false);
                    return;
                }

                if (tempBlockPos != null && ConfigUtils.canInteracted(tempBlockPos)) {
                    // 優先使用沒確認的方塊座標
                    blockPos = tempBlockPos;
                } else {
                    // 尋找第一個可觸碰的方塊
                    blockPos = null;
                    for (BlockPos pos : schematicSyncList) {
                        if (ConfigUtils.canInteracted(pos)) {
                            blockPos = pos;
                            break;
                        }
                    }
                }

                if (blockPos == null) {
                    client.gui.setOverlayMessage(Component.nullToEmpty("距離過遠"), false);
                    return;
                }

                // 檢查容器狀態是否能開啟
                ContainerResult result = canContainerOpen(blockPos);
                if (!result.canOpen) {
                    client.gui.setOverlayMessage(Component.nullToEmpty(result.message()), false);
                    schematicSyncList.remove(blockPos);
                    highlightTargetPosList.remove(blockPos);
                    highlightErrorPosList.add(blockPos);
                    return;
                }

                if (openRetryTimer <= 0) { // 超時 2 秒執行重新開啟
                    openContainer(blockPos);
                    openRetryTimer = Configs.Core.RETRY_THRESHOLD.getIntegerValue();
                    client.gui.setOverlayMessage(Component.nullToEmpty("嘗試開啟容器..."), false);
                }
            }

            case FILLING_CONTAINER -> {
                // 確保現在開著的確實是容器，且不是玩家自己的背包
                if (client.player == null ) return;

                if (client.player.containerMenu.equals(client.player.inventoryMenu)) {
                    return;
                }

                // 執行填充邏輯
                NonNullList<ItemStack> schematicContainerItemsNonNullList = getContainerItemsFromSchematic(blockPos);
                missingItems = fillContainerAndReturnMissing(schematicContainerItemsNonNullList);

                // 根據不同情況，渲染箱子顏色，切換到不同狀態機
                schematicSyncList.remove(blockPos);
                highlightTargetPosList.remove(blockPos);
                if (missingItems.isEmpty()) { // 同步完成可以進入下一個
                    tempBlockPos = null;
                    schematicSyncState = SchematicSyncState.FIND_AND_CHECK_CONTAINER;
                } else { // 需要等待物品
                    tempBlockPos = blockPos;
                    highlightMissingPosList.add(blockPos);
                    schematicSyncState = SchematicSyncState.WAITING_ITEMS;
                }
            }

            case WAITING_ITEMS -> {
                if (haveAnyItemInInv(missingItems)) {
                    highlightMissingPosList.remove(tempBlockPos);
                    highlightTargetPosList.add(tempBlockPos);
                    schematicSyncState = SchematicSyncState.FIND_AND_CHECK_CONTAINER;
                    return;
                }

                List<String> names = missingItems.entrySet().stream()
                        .map(e -> {
                            //#if MC >= 11900
                            return net.minecraft.network.chat.Component.translatable(e.getKey().getDescriptionId()).getString() + " x" + e.getValue();
                            //#else
                            //$$ return new net.minecraft.network.chat.TranslatableComponent(e.getKey().getDescriptionId()).getString() + " x" + e.getValue();
                            //#endif
                        })
                        //#if MC >= 11900
                        .toList();
                //#else
                //$$ .collect(java.util.stream.Collectors.toList());
                //#endif

                String display;
                if (names.size() > 3) {
                    display = String.join(", ", names.subList(0, 3)) + " 等 " + names.size() + " 種物品";
                } else {
                    display = String.join(", ", names);
                }

                client.gui.setOverlayMessage(Component.nullToEmpty("缺少物品: " + display), false);
            }

            default -> {}
        }
    }

    public static void tick() {
        // 閒置狀態直接跳出
        if (schematicSyncState == SchematicSyncState.IDLE) return;

        if (openRetryTimer > 0) {
            openRetryTimer--;
        }

        // 每 tick 冷卻 -1
        if (schematicSyncCooldown > 1) {
            schematicSyncCooldown--;
        } else {
            if (schematicSyncState != SchematicSyncState.FIND_AND_CHECK_CONTAINER) {
                schematicSyncCooldown = Configs.Core.SYNC_INVENTORY_RATE.getIntegerValue();
            }
            schematicSyncInv();
        }
    }

    public static void stopSync() {
        schematicSyncState = SchematicSyncState.IDLE;
        highlightTargetPosList.clear();
        highlightMissingPosList.clear();
        highlightErrorPosList.clear();
        schematicSyncList.clear();
    }
}
