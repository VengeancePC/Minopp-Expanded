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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class SwapHandSelectionScreen extends Screen {
    private final BlockPos gamePos;
    private final CardPlayer player;
    private final boolean shout;
    private final Card handCard;

    private Direction frontDir = null;
    private Direction leftDir = null;
    private Direction rightDir = null;

    public SwapHandSelectionScreen(BlockPos gamePos, CardPlayer player, Card handCard, boolean shout, CardGame game) {
        super(Component.translatable("gui.minopp.swap_card_selection.title"));
        this.gamePos = gamePos;
        this.player = player;
        this.shout = shout;
        this.handCard = handCard;
    }

    private static ResourceLocation getPlayerSkin(BlockEntityMinoTable tableEntity, Direction direction) {
        CardPlayer player = tableEntity.players.get(direction);
        if (player == null)
            return null;

        Player levelPlayer = Minecraft.getInstance().level.getPlayerByUUID(player.uuid);
        if (levelPlayer instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer) {
            return clientPlayer.getSkinTextureLocation();
        }
        return DefaultPlayerSkin.getDefaultSkin(player.uuid);
    }

    int SQUARE_SIZE = 30;
    int HEAD_SIZE = 20;
    int SIDE_GAP = 70;
    int FRONT_GAP = 30;
    int SIDE_DROP = 30;
    int SHADOW_OFFSET = 2;

    private int slotX(String slot, int cx) {
        return switch (slot) {
            case "left" -> cx - SIDE_GAP;
            case "right" -> cx + SIDE_GAP;
            default -> cx;
        };
    }

    private int slotY(String slot, int cy) {
        return switch (slot) {
            case "left", "right" -> cy - FRONT_GAP + SIDE_DROP;
            default -> cy - FRONT_GAP;
        };
    }

    private void calculatePlayerDirections(BlockEntityMinoTable tableEntity) {
        Direction myDirection = tableEntity.getPlayerDirection(player.uuid);
        if (myDirection != null) {
            int myIndex = BlockEntityMinoTable.PLAYER_ORDER.indexOf(myDirection);
            int size = BlockEntityMinoTable.PLAYER_ORDER.size();

            this.frontDir = BlockEntityMinoTable.PLAYER_ORDER.get((myIndex + 2) % size);
            this.leftDir = BlockEntityMinoTable.PLAYER_ORDER.get((myIndex + 1) % size);
            this.rightDir = BlockEntityMinoTable.PLAYER_ORDER.get((myIndex + 3) % size);
        } else {
            this.frontDir = null;
            this.leftDir = null;
            this.rightDir = null;
        }
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int cy = height / 2;

        if (minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            calculatePlayerDirections(tableEntity);

            if (frontDir != null) {
                String[] slots = { "front", "left", "right" };
                Direction[] dirs = { frontDir, leftDir, rightDir };

                for (int i = 0; i < slots.length; i++) {
                    String slot = slots[i];
                    Direction targetDir = dirs[i];
                    CardPlayer target = tableEntity.players.get(targetDir);
                    if (target == null)
                        continue;

                    int dx = slotX(slot, cx);
                    int dy = slotY(slot, cy);
                    final UUID targetUuid = target.uuid;

                    addRenderableWidget(Button.builder(Component.empty(), e -> {
                        C2SPlayCardPacket.Client.sendSwapHandC2S(gamePos, player, handCard, null, shout, targetUuid);
                        onClose();
                    }).pos(dx - SQUARE_SIZE / 2, dy - SQUARE_SIZE / 2).size(SQUARE_SIZE, SQUARE_SIZE).build());
                }
            }
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), e -> onClose())
                .pos(cx - 30, cy + FRONT_GAP / 2).size(60, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;

        if (minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            if (frontDir != null) {
                String[] slots = { "front", "left", "right" };
                Direction[] dirs = { frontDir, leftDir, rightDir };

                for (int i = 0; i < slots.length; i++) {
                    String slot = slots[i];
                    Direction targetDir = dirs[i];
                    if (tableEntity.players.get(targetDir) == null)
                        continue;

                    int dx = slotX(slot, cx);
                    int dy = slotY(slot, cy);

                    drawSquare(guiGraphics, dx + SHADOW_OFFSET, dy + SHADOW_OFFSET, SQUARE_SIZE, 0x80000000);
                    drawSquare(guiGraphics, dx, dy, SQUARE_SIZE, 0xFF313031);
                }
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            if (frontDir != null) {
                String[] slots = { "front", "left", "right" };
                Direction[] dirs = { frontDir, leftDir, rightDir };

                for (int i = 0; i < slots.length; i++) {
                    String slot = slots[i];
                    Direction targetDir = dirs[i];
                    CardPlayer target = tableEntity.players.get(targetDir);
                    if (target == null)
                        continue;

                    int dx = slotX(slot, cx);
                    int dy = slotY(slot, cy);

                    ResourceLocation skin = getPlayerSkin(tableEntity, targetDir);
                    if (skin != null) {
                        guiGraphics.pose().pushPose();

                        // Shift matrix to position the head element correctly
                        guiGraphics.pose().translate(dx - HEAD_SIZE / 2, dy - HEAD_SIZE / 2, 0);

                        // Performs a 2D float scaling operation to ensure the texture width/height maps
                        // correctly
                        float scale = (float) HEAD_SIZE / 8.0F;
                        guiGraphics.pose().scale(scale, scale, 1.0f);

                        // Passes 8.0f as floats via a cast to trigger the correct 1.20.1 blit texture
                        // signature
                        PlayerHeadRenderer.draw(guiGraphics, skin, 0, 0, (int) 8.0f);

                        guiGraphics.pose().popPose();
                    }
                }
            }
        }
    }

    private void drawSquare(GuiGraphics guiGraphics, int cx, int cy, int size, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, cy, 0);
        guiGraphics.fill(-size / 2, -size / 2, size / 2, size / 2, color);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
