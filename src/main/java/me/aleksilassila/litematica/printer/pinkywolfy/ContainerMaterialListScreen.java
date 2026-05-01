package me.aleksilassila.litematica.printer.pinkywolfy;

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
        //#if MC >= 11900
        return Component.literal(this.getMaterialList().getTitle());
        //#else
        //$$ return new net.minecraft.network.chat.TextComponent(this.getMaterialList().getTitle());
        //#endif
    }
}