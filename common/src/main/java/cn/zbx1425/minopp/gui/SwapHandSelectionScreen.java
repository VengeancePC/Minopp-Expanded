package cn.zbx1425.minopp.gui;

import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.game.Card;
import cn.zbx1425.minopp.game.CardGame;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.network.C2SPlayCardPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class SwapHandSelectionScreen extends Screen {

    private final BlockPos gamePos;
    private final CardPlayer player;
    private final boolean shout;
    private final Card handCard;

    private Direction frontDir = null;
    private Direction leftDir = null;
    private Direction rightDir = null;

    private int selectedSlot = -1;

    // card dimensions
    private static final int CARD_W = 58;
    private static final int CARD_H = 96;
    private static final int CARD_GAP = 14;
    private static final int TAB_H = 14;
    private static final int SELECTED_LIFT = 8;

    // head
    private static final int HEAD_SIZE = 20;

    // pips
    private static final int PIP_W = 7;
    private static final int PIP_H = 10;
    private static final int PIP_GAP = 2;
    private static final int PIP_COLS = 4;
    private static final int PIP_CAP = 8;

    public SwapHandSelectionScreen(BlockPos gamePos, CardPlayer player, Card handCard, boolean shout, CardGame game) {
        super(Component.translatable("gui.minopp.swap_card_selection.title"));
        this.gamePos = gamePos;
        this.player = player;
        this.shout = shout;
        this.handCard = handCard;
    }

    private static ResourceLocation getPlayerSkin(BlockEntityMinoTable tableEntity, Direction direction) {
        CardPlayer p = tableEntity.players.get(direction);
        if (p == null)
            return null;
        Player levelPlayer = Minecraft.getInstance().level.getPlayerByUUID(p.uuid);
        if (levelPlayer instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer) {
            return clientPlayer.getSkinTextureLocation();
        }
        return DefaultPlayerSkin.getDefaultSkin(p.uuid);
    }

    private void calculatePlayerDirections(BlockEntityMinoTable tableEntity) {
        Direction myDir = tableEntity.getPlayerDirection(player.uuid);
        if (myDir != null) {
            int idx = BlockEntityMinoTable.PLAYER_ORDER.indexOf(myDir);
            int size = BlockEntityMinoTable.PLAYER_ORDER.size();
            this.frontDir = BlockEntityMinoTable.PLAYER_ORDER.get((idx + 2) % size);
            this.leftDir = BlockEntityMinoTable.PLAYER_ORDER.get((idx + 1) % size);
            this.rightDir = BlockEntityMinoTable.PLAYER_ORDER.get((idx + 3) % size);
        } else {
            this.frontDir = this.leftDir = this.rightDir = null;
        }
    }

    private Direction[] getValidDirs(BlockEntityMinoTable tableEntity) {
        Direction[] all = { leftDir, frontDir, rightDir };
        int count = 0;
        for (Direction d : all)
            if (d != null && tableEntity.players.get(d) != null)
                count++;
        Direction[] valid = new Direction[count];
        int i = 0;
        for (Direction d : all)
            if (d != null && tableEntity.players.get(d) != null)
                valid[i++] = d;
        return valid;
    }

    private int cardLeft(int slot, int cx, int count) {
        int totalW = count * CARD_W + (count - 1) * CARD_GAP;
        return cx - totalW / 2 + slot * (CARD_W + CARD_GAP);
    }

    private int cardTop(int cy, boolean lifted) {
        return cy - (CARD_H + TAB_H) / 2 - (lifted ? SELECTED_LIFT : 0);
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        if (!(minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity))
            return;
        calculatePlayerDirections(tableEntity);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            Direction[] dirs = getValidDirs(tableEntity);
            int cx = width / 2;
            int cy = height / 2;

            // card slot clicks
            for (int i = 0; i < dirs.length; i++) {
                boolean lifted = selectedSlot == i;
                int x = cardLeft(i, cx, dirs.length);
                int y = cardTop(cy, lifted);
                if (mouseX >= x && mouseX <= x + CARD_W
                        && mouseY >= y && mouseY <= y + CARD_H + TAB_H) {
                    selectedSlot = i;
                    return true;
                }
            }

            // confirm and cancel buttons
            int btnY = cy + (CARD_H + TAB_H) / 2 + 10;
            int btnW = 80;
            int confirmX = cx - btnW / 2;
            int btnH = 14;

            if (mouseX >= confirmX && mouseX <= confirmX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH) {
                if (selectedSlot != -1) {
                    Direction[] dirs2 = getValidDirs(tableEntity);
                    if (selectedSlot < dirs2.length) {
                        CardPlayer target = tableEntity.players.get(dirs2[selectedSlot]);
                        if (target != null) {
                            Direction fromDir = tableEntity.getPlayerDirection(player.uuid);
                            Direction toDir = tableEntity.getPlayerDirection(target.uuid);
                            if (fromDir != null && toDir != null) {
                                CardPlayer fromP = tableEntity.game.deAmputate(player.uuid);
                                CardPlayer toP = tableEntity.game.deAmputate(target.uuid);
                                BlockEntityMinoTable.hideHandUntil.put(player.uuid,
                                        System.currentTimeMillis()
                                                + BlockEntityMinoTable.HandSwapAnimation.DURATION_MS);
                                int fromCount = fromP != null ? fromP.hand.size() : 5;
                                int toCount = toP != null ? toP.hand.size() : 5;
                                BlockEntityMinoTable.activeAnimations.add(new BlockEntityMinoTable.HandSwapAnimation(
                                        BlockEntityMinoTable.getSeatLocalPos(fromDir),
                                        BlockEntityMinoTable.getSeatLocalPos(toDir), fromCount));
                                BlockEntityMinoTable.activeAnimations.add(new BlockEntityMinoTable.HandSwapAnimation(
                                        BlockEntityMinoTable.getSeatLocalPos(toDir),
                                        BlockEntityMinoTable.getSeatLocalPos(fromDir), toCount));
                            }
                            C2SPlayCardPacket.Client.sendSwapHandC2S(gamePos, player, handCard, null, shout,
                                    target.uuid);
                            onClose();
                            return true;
                        }
                    }
                }
                return true;
            }

            int cancelY = btnY + btnH + 6;
            if (mouseX >= confirmX && mouseX <= confirmX + btnW
                    && mouseY >= cancelY && mouseY <= cancelY + btnH) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {


        if (!(minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity)) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        calculatePlayerDirections(tableEntity);

        int cx = width / 2;
        int cy = height / 2;

        // title
        int titleY = cy - (CARD_H + TAB_H) / 2 - SELECTED_LIFT - 28;
        int titlePadX = 8;
        int titlePadY = 4;
        int titleBgX1 = cx - font.width(title) / 2 - titlePadX;
        int titleBgX2 = cx + font.width(title) / 2 + titlePadX;
        int titleBgY1 = titleY - titlePadY;
        int titleBgY2 = titleY + font.lineHeight + titlePadY;
        guiGraphics.fill(titleBgX1, titleBgY1, titleBgX2, titleBgY2, 0xFF3a3a3a);
        guiGraphics.fill(titleBgX1 + 1, titleBgY1 + 1, titleBgX2 - 1, titleBgY2 - 1, 0xFF1e1e1e);
        guiGraphics.drawCenteredString(font, title, cx, titleY, 0xFFCCCCCC);

        // divider
        int stackCy = titleY + font.lineHeight + 12;
        int cw = 8, ch = 11;
        for (int s = 2; s >= 0; s--) {
            int sx = cx - cw / 2 + s * 2;
            int sy = stackCy - ch / 2 - s * 1;
            int cardBorder = s == 0 ? 0xFF666666 : 0xFF444444;
            int cardFill = s == 0 ? 0xFF2a2a2a : 0xFF1e1e1e;
            guiGraphics.fill(sx, sy, sx + cw, sy + ch, cardBorder);
            guiGraphics.fill(sx + 1, sy + 1, sx + cw - 1, sy + ch - 1, cardFill);
        }
        guiGraphics.fill(cx - 55, stackCy, cx - 10, stackCy + 1, 0xFF444444);
        guiGraphics.fill(cx + 10, stackCy, cx + 55, stackCy + 1, 0xFF444444);

        Direction[] dirs = getValidDirs(tableEntity);

        for (int i = 0; i < dirs.length; i++) {
            CardPlayer target = tableEntity.players.get(dirs[i]);
            if (target == null)
                continue;

            boolean selected = selectedSlot == i;
            boolean hovered = !selected
                    && mouseX >= cardLeft(i, cx, dirs.length)
                    && mouseX <= cardLeft(i, cx, dirs.length) + CARD_W
                    && mouseY >= cardTop(cy, false)
                    && mouseY <= cardTop(cy, false) + CARD_H + TAB_H;
            boolean lifted = selected || hovered;

            int x = cardLeft(i, cx, dirs.length);
            int y = cardTop(cy, lifted);

            // shadow
            guiGraphics.fill(x + 3, y + 3, x + CARD_W + 3, y + CARD_H + TAB_H + 3, 0x55000000);

            // card border
            int borderCol = selected ? 0xFFc9a84c : (hovered ? 0xFF666666 : 0xFF3a3a3a);
            guiGraphics.fill(x, y, x + CARD_W, y + CARD_H, borderCol);
            // card fill
            guiGraphics.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, 0xFF1e1e1e);

            // inner border
            guiGraphics.fill(x + 4, y + 4, x + CARD_W - 4, y + 5, 0xFF2a2a2a);
            guiGraphics.fill(x + 4, y + CARD_H - 5, x + CARD_W - 4, y + CARD_H - 4, 0xFF2a2a2a);
            guiGraphics.fill(x + 4, y + 4, x + 5, y + CARD_H - 4, 0xFF2a2a2a);
            guiGraphics.fill(x + CARD_W - 5, y + 4, x + CARD_W - 4, y + CARD_H - 4, 0xFF2a2a2a);

            // head
            int headX = x + CARD_W / 2 - HEAD_SIZE / 2;
            int headY = y + 8;
            ResourceLocation skin = getPlayerSkin(tableEntity, dirs[i]);
            if (skin != null) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(headX, headY, 0);
                float scale = HEAD_SIZE / 8.0f;
                guiGraphics.pose().scale(scale, scale, 1.0f);
                PlayerHeadRenderer.draw(guiGraphics, skin, 0, 0, 8);
                guiGraphics.pose().popPose();
            } else {
                guiGraphics.fill(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, 0xFF444444);
            }

            // name
            int nameY = headY + HEAD_SIZE + 3;
            String name = target.name.length() > 10 ? target.name.substring(0, 9) + "." : target.name;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x + CARD_W / 2, nameY, 0);
            guiGraphics.pose().scale(0.75f, 0.75f, 1.0f);
            guiGraphics.drawCenteredString(font, Component.literal(name), 0, 0, 0xFFCCCCCC);
            guiGraphics.pose().popPose();

            // pips
            CardPlayer gamePlayer = tableEntity.game != null
                    ? tableEntity.game.deAmputate(target.uuid)
                    : null;
            int handSize = gamePlayer != null ? gamePlayer.hand.size() : 0;
            int pipCount = Math.min(handSize, PIP_CAP);
            int pipAreaTop = nameY + font.lineHeight + 4;
            int pipColor = selected ? 0xFFc9a84c : 0xFF555555;

            for (int p = 0; p < pipCount; p++) {
                int col = p % PIP_COLS;
                int row = p / PIP_COLS;
                int pipsInThisRow = Math.min(PIP_COLS, pipCount - row * PIP_COLS);
                int rowW = pipsInThisRow * PIP_W + (pipsInThisRow - 1) * PIP_GAP;
                int rowStartX = x + CARD_W / 2 - rowW / 2;
                int px = rowStartX + col * (PIP_W + PIP_GAP);
                int py = pipAreaTop + row * (PIP_H + PIP_GAP);
                guiGraphics.fill(px, py, px + PIP_W, py + PIP_H, pipColor);
            }

            if (handSize > PIP_CAP) {
                int rows = (pipCount + PIP_COLS - 1) / PIP_COLS;
                int overflowY = pipAreaTop + rows * (PIP_H + PIP_GAP) + 1;
                guiGraphics.drawCenteredString(font,
                        Component.literal("+" + (handSize - PIP_CAP)),
                        x + CARD_W / 2, overflowY, 0xFF555555);
            }

            // tab
            int tabY = y + CARD_H;
            int tabBg = selected ? 0xFFc9a84c : 0xFF2a2a2a;
            int tabBorder = selected ? 0xFFc9a84c : 0xFF3a3a3a;
            guiGraphics.fill(x, tabY, x + CARD_W, tabY + TAB_H, tabBorder);
            guiGraphics.fill(x + 1, tabY + 1, x + CARD_W - 1, tabY + TAB_H - 1, tabBg);
            String tabText = selected ? "Selected" : "Swap";
            int tabTextCol = selected ? 0xFF1a1a1a : 0xFF666666;
            guiGraphics.drawCenteredString(font, Component.literal(tabText),
                    x + CARD_W / 2, tabY + TAB_H / 2 - font.lineHeight / 2, tabTextCol);
        }

        // bottom buttons
        int btnY = cy + (CARD_H + TAB_H) / 2 + 10;
        int btnW = 80;
        int btnH = 14;
        int confirmX = cx - btnW / 2;
        boolean confirmHovered = mouseX >= confirmX && mouseX <= confirmX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean confirmActive = selectedSlot != -1;
        int confirmBorder = confirmActive ? (confirmHovered ? 0xFFc9a84c : 0xFF9a7d2e) : 0xFF3a3a3a;
        int confirmFill = confirmActive ? (confirmHovered ? 0xFF9a7d2e : 0xFF2a2a2a) : 0xFF1e1e1e;
        int confirmText = confirmActive ? 0xFFc9a84c : 0xFF444444;
        guiGraphics.fill(confirmX, btnY, confirmX + btnW, btnY + btnH, confirmBorder);
        guiGraphics.fill(confirmX + 1, btnY + 1, confirmX + btnW - 1, btnY + btnH - 1, confirmFill);
        guiGraphics.drawCenteredString(font, Component.literal("Confirm Swap"),
                cx, btnY + btnH / 2 - font.lineHeight / 2, confirmText);

        int cancelY = btnY + btnH + 6;
        int cancelX = cx - btnW / 2;
        boolean cancelHovered = mouseX >= cancelX && mouseX <= cancelX + btnW
                && mouseY >= cancelY && mouseY <= cancelY + btnH;
        guiGraphics.fill(cancelX, cancelY, cancelX + btnW, cancelY + btnH,
                cancelHovered ? 0xFF555555 : 0xFF3a3a3a);
        guiGraphics.fill(cancelX + 1, cancelY + 1, cancelX + btnW - 1, cancelY + btnH - 1, 0xFF1e1e1e);
        guiGraphics.drawCenteredString(font, Component.literal("Cancel"),
                cx, cancelY + btnH / 2 - font.lineHeight / 2,
                cancelHovered ? 0xFFAAAAAA : 0xFF666666);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}