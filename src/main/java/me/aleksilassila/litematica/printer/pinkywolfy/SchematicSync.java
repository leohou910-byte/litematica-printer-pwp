package me.aleksilassila.litematica.printer.pinkywolfy;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.PlayerLook;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.aleksilassila.litematica.printer.printer.zxy.utils.HighlightBlockRenderer;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

//#if MC < 11900
//$$ import net.minecraft.network.chat.TranslatableComponent;
//#endif

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
        if (blockEntity instanceof ShulkerBoxBlockEntity shulkerEntity) {
            Direction facing = blockState.getValue(FACING);

            // 檢查是否有實體或方塊阻擋蓋子彈出空間
            boolean isBlocked =
                    //#if MC > 12103
                    !client.level.noCollision(Shulker.getProgressDeltaAabb(1.0F, facing, 0.0F, 0.5F, pos.getBottomCenter()).deflate(1.0E-6));
                    //#elseif MC <= 12103 && MC > 12004
                    //$$ !client.level.noCollision(Shulker.getProgressDeltaAabb(1.0F, facing, 0.0F, 0.5F).move(pos).deflate(1.0E-6));
                    //#elseif MC <= 12004
                    //$$ !client.level.noCollision(Shulker.getProgressDeltaAabb(facing, 0.0f, 0.5f).move(pos).deflate(1.0E-6));
                    //#endif

            // 如果蓋子已經是開的（例如正要關上），通常不需要判定為阻擋
            if (isBlocked && shulkerEntity.getAnimationStatus() == ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
                return new ContainerResult(false, "界伏盒開啟方向有阻擋");
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

    /**
     * 強制玩家視線鎖定目標方塊並執行點擊操作以開啟容器。
     *
     * @param pos 目標方塊的座標 (BlockPos)
     */
    public static void openContainer(BlockPos pos) {
        if (client.player == null || client.gameMode == null || pos == null) return;

        // 若已開啟其他非背包 UI，先關閉
        if (!client.player.containerMenu.equals(client.player.inventoryMenu)) {
            client.player.closeContainer();
        }

        // 2. 視線計算
        Vec3 targetVec = Vec3.atCenterOf(pos);
        PlayerLook targetLook = getPlayerLook(targetVec);

        // 檢查 getPlayerLook 是否因為 player 為 null 而回傳了 null
        if (targetLook != null) {
            ActionManager.INSTANCE.setLook(targetLook);
        }

        // 3. 執行點擊動作
        ActionManager.INSTANCE.queueClick(
                pos,
                Direction.UP,
                new Vec3(0.5, 0.5, 0.5),
                false
        );
    }

    private static PlayerLook getPlayerLook(Vec3 targetVec) {
        if (client.player == null || targetVec == null) return null;

        double dx = targetVec.x - client.player.getX();
        // 使用 getEyeY() 而不是 getY()，這樣旋轉角度（Pitch）才會準確指向方塊
        double dy = targetVec.y - client.player.getEyeY();
        double dz = targetVec.z - client.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distance)));

        return new PlayerLook(yaw, pitch);
    }

    // 填充並回傳缺少物品
    public static Map<Item, Integer> fillContainerAndReturnMissing(NonNullList<ItemStack> schematicContainerItemsNonNullList) {
        Map<Item, Integer> misItems = new HashMap<>();

        if (client.player == null || client.gameMode == null) return misItems;

        AbstractContainerMenu sc = client.player.containerMenu;

        Container targetContainer = sc.slots.get(0).container;
        int containerSize = targetContainer.getContainerSize();

        for (Slot slot : sc.slots) {
            // 過濾掉不屬於這個箱子的格位
            if (slot.container != targetContainer) continue;

            // 獲取該格在容器內部的原始索引
            int containerSlotIndex = slot.getContainerSlot();

            // 確保不會超出藍圖數據的範圍
            if (containerSlotIndex >= schematicContainerItemsNonNullList.size()) continue;

            ItemStack itemInSlot = slot.getItem(); // 容器裡的物品
            ItemStack schematicStack = schematicContainerItemsNonNullList.get(containerSlotIndex); // 藍圖要求的物品
            int currNum = itemInSlot.getCount();
            int tarNum = schematicStack.getCount();
            boolean same = ItemStack.isSameItemSameComponents(itemInSlot, schematicStack);
            if (same && currNum == tarNum) continue; // 數量物品相同時 不和背包交互

            if (same) {
                // 相同但有多
                while (currNum > tarNum) {
                    client.gameMode.handleInventoryMouseClick(sc.containerId, slot.index, 0, ClickType.THROW, client.player);
                    currNum--;
                }
            } else {
                // 不同直接扔出
                client.gameMode.handleInventoryMouseClick(sc.containerId, slot.index, 1, ClickType.THROW, client.player);
                currNum = 0;
            }

            //背包交互
            for (int j = containerSize; j < sc.slots.size(); j++) {
                // 補充完畢跳出
                if (currNum == tarNum) break;

                // 不符合條件跳過
                ItemStack playerItem = sc.slots.get(j).getItem();
                boolean same2 = ItemStack.isSameItemSameComponents(schematicStack, playerItem);
                if (playerItem.isEmpty() || !same2) continue;

                // 取德物品數量
                int playerItemCount = playerItem.getCount();

                // 拿取背包物品，不管如何都要先拿起
                client.gameMode.handleInventoryMouseClick(sc.containerId, j, 0, ClickType.PICKUP, client.player);

                if ((tarNum - currNum) >= playerItemCount) { // 可直接全部移入

                    // 把手上的全部放到箱子
                    client.gameMode.handleInventoryMouseClick(sc.containerId, slot.index, 0, ClickType.PICKUP, client.player);
                    currNum += playerItemCount;

                } else { // 需要一個一個移入

                    // 一個一個移入
                    for (; currNum < tarNum && playerItemCount > 0; playerItemCount--) {
                        client.gameMode.handleInventoryMouseClick(sc.containerId, slot.index, 1, ClickType.PICKUP, client.player);
                        currNum++;
                    }
                }

                // 如果有多，放回背包
                if (!sc.getCarried().isEmpty()) {
                    client.gameMode.handleInventoryMouseClick(sc.containerId, j, 0, ClickType.PICKUP, client.player);
                }
            }

            if (tarNum > currNum) {
                Item type = schematicContainerItemsNonNullList.get(slot.index).getItem();
                misItems.put(type, misItems.getOrDefault(type, 0) + (tarNum - currNum));
            }
        }

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
                if (schematicSyncList.isEmpty() && tempBlockPos == null) {
                    schematicSyncState = SchematicSyncState.IDLE;
                    client.gui.setOverlayMessage(Component.nullToEmpty("藍圖容器同步完成"), false);
                    return;
                }

                // 尋找目標容器
                if (tempBlockPos != null) {
                    // 優先使用沒確認的方塊座標
                    if (PlayerUtils.canInteracted(tempBlockPos)) {
                        blockPos = tempBlockPos;
                    }
                } else {
                    // 尋找第一個可觸碰的方塊
                    blockPos = null;
                    for (BlockPos pos : schematicSyncList) {
                        if (PlayerUtils.canInteracted(pos)) {
                            blockPos = pos;
                            break;
                        }
                    }
                }

                if (blockPos == null) { // 範圍內無符合容器
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

                // 開啟容器
                if (openRetryTimer <= 0) { // 超時歸零執行重新開啟
                    openRetryTimer = Configs.Core.RETRY_THRESHOLD.getIntegerValue();
                    client.gui.setOverlayMessage(Component.nullToEmpty("嘗試開啟容器..."), false);
                    openContainer(blockPos);
                }
            }

            case FILLING_CONTAINER -> {
                if (client.player == null ) return;

                // 確保現在開著的確實是容器，不是玩家自己的背包
                if (client.player.containerMenu.equals(client.player.inventoryMenu)) {
// 這邊會判斷錯，先直接重新判斷
//                    // 超時計時器
//                    if (openRetryTimer > 0) {
//                        return;
//                    }
//
//                    // 超時判定為失敗
//                    client.gui.setOverlayMessage(Component.nullToEmpty("開啟容器超時"), false);
//                    schematicSyncList.remove(blockPos);
//                    highlightTargetPosList.remove(blockPos);
//                    highlightErrorPosList.add(blockPos);
                    schematicSyncState = SchematicSyncState.FIND_AND_CHECK_CONTAINER;
                    return;
                }

                // 重製計時器
                openRetryTimer = 0;

                // 執行填充邏輯
                NonNullList<ItemStack> schematicContainerItemsNonNullList = getContainerItemsFromSchematic(blockPos);
                missingItems = fillContainerAndReturnMissing(schematicContainerItemsNonNullList);

                // 關閉箱子
                client.player.closeContainer();

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

                // 冷卻重設
                schematicSyncCooldown = Configs.Core.SYNC_INVENTORY_RATE.getIntegerValue();
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
                            return Component.translatable(e.getKey().getDescriptionId()).getString() + " x" + e.getValue();
                            //#else
                            //$$ return new TranslatableComponent(e.getKey().getDescriptionId()).getString() + " x" + e.getValue();
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

        // 超時計算
        if (openRetryTimer > 0) {
            openRetryTimer--;
        }

        // 冷卻時間
        if (schematicSyncCooldown > 1) {
            schematicSyncCooldown--;
            return;
        }

        // 執行操作
        schematicSyncInv();
    }

    public static void stopSync() {
        schematicSyncState = SchematicSyncState.IDLE;

        highlightTargetPosList.clear();
        highlightMissingPosList.clear();
        highlightErrorPosList.clear();

        schematicSyncList.clear();
        tempBlockPos = null;
    }
}
