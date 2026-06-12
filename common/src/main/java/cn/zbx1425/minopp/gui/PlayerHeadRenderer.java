package cn.zbx1425.minopp.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class PlayerHeadRenderer {
    public static void draw(GuiGraphics guiGraphics, ResourceLocation skin, int x, int y, int size) {
        RenderSystem.setShaderTexture(0, skin);
        guiGraphics.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
    }
}