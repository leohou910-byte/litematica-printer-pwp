package me.aleksilassila.litematica.printer.PinkyWolfy;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class ContainerMaterialList extends MaterialListBase {

    @Override
    public String getName() {
        return "container_sync_summary";
    }

    @Override
    public String getTitle() {
        return "容器物品同步清單";
    }

    @Override
    public void reCreateMaterialList() {
        Object2IntOpenHashMap<Item> totals = new Object2IntOpenHashMap<>();
        List<BlockPos> containerPositions = SchematicSync.getSelectionAreaContainerList();

        // 1. 統計邏輯
        for (BlockPos pos : containerPositions) {
            List<ItemStack> items = SchematicSync.getContainerItemsFromSchematic(pos);
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    totals.addTo(stack.getItem(), stack.getCount());
                }
            }
        }

        // 2. 轉換邏輯 (使用提取出的方法)
        List<MaterialListEntry> entries = new ArrayList<>();
        for (var entry : totals.object2IntEntrySet()) {
            entries.add(createEntryFromItem(entry.getKey(), entry.getIntValue()));
        }

        this.setMaterialListEntries(entries);
    }

    private static final Minecraft client = Minecraft.getInstance();

    /**
     * 將統計結果封裝為 Litematica 的條目物件
     */
    private MaterialListEntry createEntryFromItem(Item item, int totalNeed) {
        ItemStack iconStack = new ItemStack(item);

        // 1. 取得玩家身上實際有的數量
        int available = getPlayerInventoryCount(item);

        // 2. 計算還差多少 (如果身上有的比需要的多，則為 0)
        int missing = Math.max(0, totalNeed - available);

        // 3. 錯誤數量 (通常容器同步用不到，維持 0)
        int mismatched = 0;

        return new MaterialListEntry(
                iconStack,  // 物品
                totalNeed,  // 藍圖/容器要求的總數
                missing,    // 還差多少
                mismatched, // 錯誤數
                available   // 我現在有的數量
        );
    }

    private int getPlayerInventoryCount(Item targetItem) {
        // 使用 getContainerSize() 獲取總槽位數 (通常是 36 個主背包槽位)
        if (client.player == null) return 0;
        int count = 0;
        for (ItemStack stack : client.player.getInventory()) {
            if (!stack.isEmpty() && stack.getItem() == targetItem) {
                count += stack.getCount();
            }
        }
        return count;
    }
}