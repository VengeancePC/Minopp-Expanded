package cn.zbx1425.minopp.gui;

import java.util.ArrayList;
import java.util.List;

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


    // all gamerules which can be toggled at before a game
    private boolean settingsOpen = false;
    private Button settingsButton;
    private boolean validationCheck;

    private final List<Button> ruleButtons = new ArrayList<>();

// helper to add a rule row
private void addRuleRow(int sideX, int rowY, String ruleKey, String labelKey, BlockEntityMinoTable te) {
    int checkSize = 10;
    Button btn = Button.builder(Component.empty(), b -> {
        boolean current = te.getRule(ruleKey, true);

        te.rules.put(ruleKey, !current); // immediate local update

        C2SSeatControlPacket.Client.sendRuleC2S(
                gamePos,
                ruleKey,
                !current
        );
    })
    .pos(sideX + 6 - 1, rowY - 1)
    .size(checkSize + 2, checkSize + 2)
    .build();

    addRenderableWidget(btn);
    ruleButtons.add(btn);
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

    int PANEL_HEIGHT = MARGIN + 6 + MARGIN
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


        // fix: adds validation check to clear table names on GUI open, removing stale values
        if (!validationCheck) {
            validationCheck = true;
        if (minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable te && te.game == null) {
        C2SSeatControlPacket.Client.sendValidateSeatsC2S(gamePos);
        }};

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
        }).pos(xOff + PANEL_WIDTH - MARGIN - LARGE_BTN_WIDTH, yOff + PANEL_HEIGHT - MARGIN - LARGE_BTN_HEIGHT)
                .size(LARGE_BTN_WIDTH, LARGE_BTN_HEIGHT).build();
        leaveButton.active = false;
        addRenderableWidget(leaveButton);

        settingsButton = Button.builder(Component.literal(settingsOpen ? "<" : ">"), b -> {
            settingsOpen = !settingsOpen;
            init();
        })
                .pos(xOff + PANEL_WIDTH - 20, yOff + 4)
                .size(16, 16)
                .build();
        addRenderableWidget(settingsButton);

        ruleButtons.clear();
        if (settingsOpen && minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable te) {
            int sideX = xOff + PANEL_WIDTH + 8;
            int sideY = yOff;
            int rowHeight = 14;
            int headerHeight = 9 + 12;

            String[][] rulesList = {
                    { BlockEntityMinoTable.RULE_JUMP_IN, "Allow Jump-In" },
                    { BlockEntityMinoTable.RULE_STACKING, "Allow Stacking" }
            };

            for (int i = 0; i < rulesList.length; i++) {
                int rowY = sideY + headerHeight + 8 + i * rowHeight;
                addRuleRow(sideX, rowY, rulesList[i][0], rulesList[i][1], te);
            }
        }
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
                    width / 2 + LARGE_BTN_HEIGHT / 2,
                    yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT,
                    0xFF3E2723);

            if (settingsOpen) {
                guiGraphics.fill(
                        settingsButton.getX() - 1,
                        settingsButton.getY() - 1,
                        settingsButton.getX() + settingsButton.getWidth() + 1,
                        settingsButton.getY() + settingsButton.getHeight() + 1,
                        0xFF4CAF50);
            }

            if (settingsOpen && minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable te) {
                int sideX = xOff + PANEL_WIDTH + 8;
                int sideY = yOff;
                int checkSize = 10;
                int rowHeight = 14;
                int headerHeight = 9 + 12;

                String[][] rulesList = {
                        { BlockEntityMinoTable.RULE_JUMP_IN, "Allow Jump-In" },
                        { BlockEntityMinoTable.RULE_STACKING, "Allow Stacking" }
                };

                int sideWidth = 140;
                int sideHeight = PANEL_HEIGHT;

                guiGraphics.fill(sideX - 1, sideY - 1, sideX + sideWidth + 1, sideY + sideHeight + 1, 0xCC000000);
                guiGraphics.fill(sideX, sideY, sideX + sideWidth, sideY + sideHeight, 0xFF313031);
                guiGraphics.fill(sideX, sideY - 6, sideX + sideWidth, sideY + 9 + 6, 0x99000000);
                guiGraphics.drawString(font, "Settings", sideX + 6, sideY + 6, 0xFFFFFFFF);

                for (int i = 0; i < rulesList.length; i++) {
                    String ruleKey = rulesList[i][0];
                    String label = rulesList[i][1];
                    int rowY = sideY + headerHeight + 8 + i * rowHeight;
                    boolean enabled = te.getRule(ruleKey, true);
                    if (enabled) {
                        guiGraphics.fill(
                                sideX + 6 - 2,
                                rowY - 2,
                                sideX + 6 + checkSize + 2,
                                rowY + checkSize + 2,
                                0xFF4CAF50);
                    }
                    guiGraphics.drawString(font, label, sideX + 22, rowY + 1, 0xFFFFFFFF);
                }

                for (Button b : ruleButtons) {
                    b.active = te.game == null;
                }
            }

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
