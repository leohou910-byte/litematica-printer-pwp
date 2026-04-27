package me.aleksilassila.litematica.printer.PinkyWolfy;

import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ContainerMaterialListScreen extends GuiMaterialList {

    public ContainerMaterialListScreen(MaterialListBase materialList) {
        // 既然父類別沒有無參建構子，你必須在這裡把參數傳給它
        super(materialList);
    }

    @Override
    @NotNull
    public Component getTitle() {
        // 透過 Getter 獲取，而不是直接訪問變數
        return Component.literal(this.getMaterialList().getTitle());
    }
}