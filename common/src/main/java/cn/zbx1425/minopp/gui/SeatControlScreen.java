package cn.zbx1425.minopp.gui;

import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import cn.zbx1425.minopp.network.C2SSeatControlPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

public class SeatControlScreen extends Screen {

    private final BlockPos gamePos;

    public SeatControlScreen(BlockPos gamePos) {
        super(Component.translatable("gui.minopp.seats.title"));
        this.gamePos = gamePos;
    }

    private static ResourceLocation getPlayerSkin(BlockEntityMinoTable tableEntity, Direction direction) {
    CardPlayer player = tableEntity.players.get(direction);
    if (player == null) return null;

    Player levelPlayer = Minecraft.getInstance().level.getPlayerByUUID(player.uuid);
    if (levelPlayer instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer) {
        return clientPlayer.getSkinTextureLocation();
    }
    return DefaultPlayerSkin.getDefaultSkin(player.uuid);
}
    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("minecraft", "textures/block/spruce_planks.png");
    int LARGE_BTN_WIDTH = 70;
    int LARGE_BTN_HEIGHT = 20;
    int MARGIN = 8;

    int PANEL_HEIGHT = MARGIN + 9 + MARGIN
            + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT + MARGIN + 9 + MARGIN
            + MARGIN + LARGE_BTN_HEIGHT + MARGIN;
    int PANEL_WIDTH = 260;

        private Button stopButton, startButton, leaveButton;

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        int xOff = (width - PANEL_WIDTH) / 2;
        int yOff = (height - PANEL_HEIGHT) / 2;

        stopButton = Button.builder(Component.translatable("gui.minopp.seats.stop"), e -> {
            C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, false);
            onClose();
        }).pos(xOff + MARGIN, yOff + PANEL_HEIGHT - MARGIN - LARGE_BTN_HEIGHT).size(LARGE_BTN_WIDTH, LARGE_BTN_HEIGHT).build();
        stopButton.active = false;
        addRenderableWidget(stopButton);

        startButton = Button.builder(Component.translatable("gui.minopp.seats.start"), e -> {
            C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, true);
            onClose();
        }).pos(xOff + MARGIN + LARGE_BTN_WIDTH + MARGIN, yOff + PANEL_HEIGHT - MARGIN - LARGE_BTN_HEIGHT).size(LARGE_BTN_WIDTH, LARGE_BTN_HEIGHT).build();
        startButton.active = false;
        addRenderableWidget(startButton);
        
        leaveButton = Button.builder(Component.translatable("gui.minopp.seats.reset"), e -> {
            C2SSeatControlPacket.Client.sendResetSeatsC2S(gamePos);
            onClose();
        }).pos( xOff + PANEL_WIDTH - MARGIN - LARGE_BTN_WIDTH, yOff + PANEL_HEIGHT - MARGIN - LARGE_BTN_HEIGHT).size(LARGE_BTN_WIDTH, LARGE_BTN_HEIGHT).build();        
        leaveButton.active = false;
        addRenderableWidget(leaveButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        if (minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            startButton.active = tableEntity.game == null && tableEntity.getPlayersList().size() >= 2;
            stopButton.active = tableEntity.game != null;
            CardPlayer cardPlayer = ItemHandCards.getCardPlayer(minecraft.player);
            leaveButton.active = tableEntity.game == null && tableEntity.getPlayersList().contains(cardPlayer);

            int xOff = (width - PANEL_WIDTH) / 2;
            int yOff = (height - PANEL_HEIGHT) / 2;
            guiGraphics.fill(xOff + MARGIN, yOff + MARGIN, xOff + PANEL_WIDTH + MARGIN, yOff + PANEL_HEIGHT + MARGIN,
                    0x99000000);
            // Tiled block texture background
            guiGraphics.fill(xOff - 1, yOff - 1, xOff + PANEL_WIDTH + 1, yOff + PANEL_HEIGHT + 1, 0xCC000000);
            guiGraphics.blit(PANEL_TEXTURE, xOff, yOff, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 16, 16);
            // Dark accent strip for button row
            guiGraphics.fill(xOff, yOff + PANEL_HEIGHT - LARGE_BTN_HEIGHT, xOff + PANEL_WIDTH, yOff + PANEL_HEIGHT,
                    0xCC000000);
            // Title backdrop
            guiGraphics.fill(xOff, yOff + MARGIN - 6, xOff + PANEL_WIDTH, yOff + MARGIN + 9 + 6, 0x99000000);
            guiGraphics.drawCenteredString(font, title, width / 2, yOff + MARGIN, 0xFFAAAAAA);
            // middle square    
            guiGraphics.fill(width / 2 - LARGE_BTN_HEIGHT / 2, yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN,
                    width / 2 + LARGE_BTN_HEIGHT / 2, yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT,
                    0xFF3E2723);

            // North Player
            {
                String name = getPlayerName(tableEntity, Direction.NORTH);
                int y = yOff + MARGIN + 9 + MARGIN + MARGIN;
                ResourceLocation skin = getPlayerSkin(tableEntity, Direction.NORTH);
                int headSize = 9;
                if (skin != null) {
                    int totalWidth = font.width(name) + headSize + 4;
                    int startX = width / 2 - totalWidth / 2;
                    PlayerHeadRenderer.draw(guiGraphics, skin, startX, y - 1, headSize);
                    guiGraphics.drawString(font, name, startX + headSize + 4, y, 0xFFAAAAAA);
                } else {
                    guiGraphics.drawCenteredString(font, name, width / 2, y, 0xFFAAAAAA);
                }
            }
            // West Player
            {
                String name = getPlayerName(tableEntity, Direction.WEST);
                int y = yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT / 2 - 9 / 2;
                ResourceLocation skin = getPlayerSkin(tableEntity, Direction.WEST);
                int headSize = 9;
                int nameX = width / 2 - MARGIN - LARGE_BTN_HEIGHT / 2 - font.width(name) - (skin != null ? headSize + 4 : 0);
                if (skin != null) {
                    PlayerHeadRenderer.draw(guiGraphics, skin, nameX - headSize - 4, y - 1, headSize);
                }
                guiGraphics.drawString(font, name, nameX, y, 0xFFAAAAAA);
            }
            // East Player
            {
                String name = getPlayerName(tableEntity, Direction.EAST);
                int y = yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT / 2 - 9 / 2;
                ResourceLocation skin = getPlayerSkin(tableEntity, Direction.EAST);
                int headSize = 9;
                int x = width / 2 + MARGIN + LARGE_BTN_HEIGHT / 2;
                if (skin != null) {
                    PlayerHeadRenderer.draw(guiGraphics, skin, x, y - 1, headSize);
                }
                int nameX = x + (skin != null ? headSize + 4 : 0);
                guiGraphics.drawString(font, name, nameX, y, 0xFFAAAAAA);
            }
            // South Player
            {
                String name = getPlayerName(tableEntity, Direction.SOUTH);
                int y = yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT + MARGIN;
                ResourceLocation skin = getPlayerSkin(tableEntity, Direction.SOUTH);
                int headSize = 9;
                if (skin != null) {
                    int totalWidth = font.width(name) + headSize + 4;
                    int startX = width / 2 - totalWidth / 2;
                    PlayerHeadRenderer.draw(guiGraphics, skin, startX, y - 1, headSize);
                    guiGraphics.drawString(font, name, startX + headSize + 4, y, 0xFFAAAAAA);
                } else {
                    guiGraphics.drawCenteredString(font, name, width / 2, y, 0xFFAAAAAA);
                }
            }

        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static String getPlayerName(BlockEntityMinoTable tableEntity, Direction direction) {
        return tableEntity.players.get(direction) == null ? "-" : tableEntity.players.get(direction).name;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
