package cn.zbx1425.minopp.render;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.block.BlockMinoTable;
import cn.zbx1425.minopp.game.Card;
import cn.zbx1425.minopp.platform.RegistryObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.Random;

public class BlockEntityMinoTableRenderer implements BlockEntityRenderer<BlockEntityMinoTable> {

    private static final RegistryObject<ItemStack> HAND_CARDS_MODEL_PLACEHOLDER = new RegistryObject<>(() -> new ItemStack(Mino.ITEM_HAND_CARDS_MODEL_PLACEHOLDER.get()));

    private ItemRenderer itemRenderer;
    

    public BlockEntityMinoTableRenderer(BlockEntityRendererProvider.Context ctx) {
        itemRenderer = ctx.getItemRenderer();
    }
    @Override
    public void render(BlockEntityMinoTable blockEntity, float partialTick, PoseStack poseStack,
                    MultiBufferSource multiBufferSource, int packedLight, int packedOverlay) {
            BlockEntityMinoTable.activeAnimations.removeIf(BlockEntityMinoTable.HandSwapAnimation::isDone);
            if (blockEntity.game == null) return;

            BakedModel model = itemRenderer.getModel(HAND_CARDS_MODEL_PLACEHOLDER.get(), null, null, 0);

        // if (BlockMinoTable.Client.isCursorHittingPile()) {
        //     LevelRenderer.renderLineBox(poseStack, multiBufferSource.getBuffer(RenderType.lines()),
        //             BlockMinoTable.Client.getPileAabb(blockEntity), 1, 1, 0, 1f);
        // }

        // Draw deck pile (face-down cards stacked at core +0.5)
            poseStack.pushPose();
            poseStack.translate(0.5, 0.94, 0.5);
            poseStack.scale(0.5f, 0.4f, 0.5f);
        poseStack.mulPose(Axis.XP.rotation(-(float)Math.PI / 2));
            Random deckRandom = new Random(1);
            for (int ci = 0; ci < Math.ceil(blockEntity.game.deck.size() / 5f); ci++) {
                    poseStack.pushPose();
        poseStack.translate(deckRandom.nextFloat() * 0.1 - 0.05, deckRandom.nextFloat() * 0.1 - 0.05, ci * (1f / 48f));
                    itemRenderer.render(HAND_CARDS_MODEL_PLACEHOLDER.get(), ItemDisplayContext.FIXED, false,
                                    poseStack, multiBufferSource, packedLight, packedOverlay, model);
                    poseStack.popPose();
            }
            poseStack.popPose();
        // Draw discard pile (face-up cards stacked in center)
            poseStack.pushPose();
            poseStack.translate(1.5, 0.94, 1.5);
            poseStack.scale(0.3f, 0.3f, 0.3f);
            poseStack.mulPose(Axis.XP.rotation(-(float) Math.PI / 2));
        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.entityCutout(Mino.id("textures/gui/deck.png")));
            Random discardRandom = new Random(1);

            int discardSize = blockEntity.game.discardDeck.size();
        int maxVisualCards = 20; // max cards rendered (excluding top card)
            int startIndex = Math.max(0, discardSize - maxVisualCards);

            for (int s = 0; s < startIndex; s++) {
        discardRandom.nextFloat(); discardRandom.nextFloat(); discardRandom.nextFloat();
            }

            for (int ci = startIndex; ci <= discardSize; ci++) {
                    poseStack.pushPose();
        //  controls card variation in the middle pile
                    float offsetX = discardRandom.nextFloat() * 0.3f - 0.15f;
                    float offsetY = discardRandom.nextFloat() * 0.3f - 0.15f;
                    float cardYaw = discardRandom.nextFloat() * 0.8f - 0.4f;

        // Visual stack index (0 = bottom of visible portion)
                    int visualIndex = ci - startIndex;

                    if (ci == discardSize) {
                            offsetX = 0;
                            offsetY = 0;
                            cardYaw = 0;
                            poseStack.translate(offsetX, offsetY, visualIndex * (1f / 56f) + 0.01f);
                    } else {
                            poseStack.translate(offsetX, offsetY, visualIndex * (1f / 56f));
                    }
                    poseStack.mulPose(Axis.ZP.rotation(cardYaw));

                    Card card = ci == discardSize ? blockEntity.game.topCard : blockEntity.game.discardDeck.get(ci);
                    float cardU = switch (card.family) {
                            case NUMBER -> Math.abs(card.number) * 16;
                            case SKIP -> 160;
                            case DRAW -> 176;
                            case REVERSE -> 192;
                    } / 256f;
                    float cardV = card.suit.ordinal() * 25 / 128f;
                    float cardUW = 16 / 256f;
                    float cardVH = 25 / 128f;
                    int color = (ci == blockEntity.game.discardDeck.size()) ? 0xFFFFFFFF : 0xFFBBBBBB;

                    Matrix4f matrix = poseStack.last().pose();

        // Border quad
                    vertexConsumer.vertex(matrix, -0.52f, 0.8f, 0).color(0xFFFFFFFF)
                                    .uv(cardU, cardV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                                    .normal(0, 0, 1).endVertex();
                    vertexConsumer.vertex(matrix, -0.52f, -0.8f, 0).color(0xFFFFFFFF)
                                    .uv(cardU, cardV + cardVH).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                                    .normal(0, 0, 1).endVertex();
                    vertexConsumer.vertex(matrix, 0.52f, -0.8f, 0).color(0xFFFFFFFF)
                .uv(cardU + cardUW, cardV + cardVH).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                                    .normal(0, 0, 1).endVertex();
                    vertexConsumer.vertex(matrix, 0.52f, 0.8f, 0).color(0xFFFFFFFF)
                                    .uv(cardU + cardUW, cardV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                                    .normal(0, 0, 1).endVertex();

        // Face quad
                    poseStack.pushPose();
                    poseStack.translate(0, 0, 1f / 64f);
                    Matrix4f matrixFace = poseStack.last().pose();
                    vertexConsumer.vertex(matrixFace, -0.5f, 0.78f, 0).color(color)
                                    .uv(cardU, cardV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                                    .normal(0, 0, 1).endVertex();
                    vertexConsumer.vertex(matrixFace, -0.5f, -0.78f, 0).color(color)
                                    .uv(cardU, cardV + cardVH).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                                    .normal(0, 0, 1).endVertex();
                    vertexConsumer.vertex(matrixFace, 0.5f, -0.78f, 0).color(color)
                .uv(cardU + cardUW, cardV + cardVH).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                                    .normal(0, 0, 1).endVertex();
                    vertexConsumer.vertex(matrixFace, 0.5f, 0.78f, 0).color(color)
                                    .uv(cardU + cardUW, cardV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
                                    .normal(0, 0, 1).endVertex();
                    poseStack.popPose();

                    if (ci == blockEntity.game.discardDeck.size()) {
                            Font font = Minecraft.getInstance().font;
                            poseStack.pushPose();
                            poseStack.mulPose(Axis.XP.rotation((float) Math.PI / 2));
                            poseStack.mulPose(Axis.YP.rotation(-cardYaw));
                            poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
                            poseStack.translate(0, 0.5f, 0);
                            poseStack.mulPose(Axis.ZP.rotation((float) Math.PI));
                            poseStack.scale(0.03F, 0.03F, 0.03F);
                            Matrix4f matrix4f = poseStack.last().pose();
                            float g = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
                            int k = (int) (g * 255.0F) << 24;
                            Component component = (card.suit == Card.Suit.WILD)
                        ? card.getDisplayName().copy().append(Component.translatable("game.minopp.card.suit." + card.getEquivSuit().name().toLowerCase()))
                                            : card.getDisplayName();
                            float h = (float) (-font.width(component) / 2);
                font.drawInBatch(component, h, 0, 553648127, false, matrix4f, multiBufferSource, Font.DisplayMode.SEE_THROUGH, k, LightTexture.FULL_BRIGHT);
                font.drawInBatch(component, h, 0, -1, false, matrix4f, multiBufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                            poseStack.popPose();
                    }

                    poseStack.popPose();
            }
            poseStack.popPose();

            
            for (BlockEntityMinoTable.HandSwapAnimation anim : BlockEntityMinoTable.activeAnimations) {
                int stackSize = Math.max(1, Math.min(anim.cardCount, 10));
                double dx = anim.toPos.x - anim.fromPos.x;
                double dz = anim.toPos.z - anim.fromPos.z;
                float yaw = (float) Mth.atan2(dz, dx);
                for (int ci = 0; ci < stackSize; ci++) {
                    float cardRaw = anim.rawProgress() - ci * BlockEntityMinoTable.HandSwapAnimation.STAGGER;
                    float cardT = easeInOut(Math.max(0f, Math.min(1f, cardRaw)));
                    double cx = Mth.lerp(cardT, anim.fromPos.x, anim.toPos.x);
                    double cy = Mth.lerp(cardT, anim.fromPos.y, anim.toPos.y) + Math.sin(cardT * Math.PI) * 0.5;
                    double cz = Mth.lerp(cardT, anim.fromPos.z, anim.toPos.z);
                    poseStack.pushPose();
                    poseStack.translate(cx, cy, cz);
                    poseStack.scale(0.5f, 0.5f, 0.5f);
                    poseStack.mulPose(Axis.YP.rotation(-yaw + (float) Math.PI / 2));
                    poseStack.mulPose(Axis.XP.rotation(-(float) Math.PI / 2));
                    poseStack.translate(0, 0, ci * (1f / 48f));
                    itemRenderer.render(HAND_CARDS_MODEL_PLACEHOLDER.get(), ItemDisplayContext.FIXED, false,
                            poseStack, multiBufferSource, packedLight, packedOverlay, model);
                    poseStack.popPose();
                }
            }
    }

    @Override
    public boolean shouldRenderOffScreen(BlockEntityMinoTable blockEntity) {
        return true;
    }

    private float easeInOut(float t) {
            return t < 0.5f ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2;
    }
}