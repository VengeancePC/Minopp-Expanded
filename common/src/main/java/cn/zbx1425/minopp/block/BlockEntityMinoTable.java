package cn.zbx1425.minopp.block;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.effect.EffectEvent;
import cn.zbx1425.minopp.effect.EffectEvents;
import cn.zbx1425.minopp.effect.SeatActionTakenEffectEvent;
import cn.zbx1425.minopp.game.ActionMessage;
import cn.zbx1425.minopp.game.ActionReport;
import cn.zbx1425.minopp.game.Card;
import cn.zbx1425.minopp.game.CardGame;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemDataUtils;
import cn.zbx1425.minopp.network.C2SPlayCardPacket;
import cn.zbx1425.minopp.network.S2CActionEphemeralPacket;
import cn.zbx1425.minopp.network.S2CEffectListPacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BlockEntityMinoTable extends BlockEntity {

    public Map<Direction, CardPlayer> players = new HashMap<>();
    public CardGame game = null;
    public ActionMessage state = ActionMessage.NO_GAME;
    public List<Pair<ActionMessage, Long>> clientMessageList = new ArrayList<>();
    public static final Map<UUID, Long> hideHandUntil = new HashMap<>();
    public ItemStack award = ItemStack.EMPTY;
    public boolean demo = false;
    public boolean gameEnd = false;
    public UUID lastPlayedByUuid = null;
    public int lastDiscardSize = 0;
    public Map<UUID, Integer> lastHandSizes = new HashMap<>();

    // game rules
    public Map<String, Boolean> rules = new HashMap<>();

    public boolean getRule(String name, boolean defaultValue) {
        return rules.getOrDefault(name, defaultValue);
}

    public static final String RULE_JUMP_IN = "allowJumpIn";
    public static final String RULE_STACKING = "allowStacking";
    public static final String RULE_SEVEN0 = "allowSeven0";

    public static final List<Direction> PLAYER_ORDER = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    public @Nullable Direction getPlayerDirection(UUID uuid) {
        if (uuid == null)
            return null;
        for (Map.Entry<Direction, CardPlayer> entry : players.entrySet()) {
            if (entry.getValue() != null && uuid.equals(entry.getValue().uuid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static class HandSwapAnimation {
        public final Vec3 fromPos;
        public final Vec3 toPos;
        public final int cardCount;
        public final long startTime;
        public static final long DURATION_MS = 1200;
        public static final float STAGGER = 0.08f;

        public HandSwapAnimation(Vec3 fromPos, Vec3 toPos, int cardCount) {
            this.fromPos = fromPos;
            this.toPos = toPos;
            this.cardCount = cardCount;
            this.startTime = System.currentTimeMillis();
        }

        public float progress() {
            return Math.min(1f, (System.currentTimeMillis() - startTime) / (float) DURATION_MS);
        }

        public float rawProgress() {
            return (System.currentTimeMillis() - startTime) / (float) DURATION_MS;
        }

        public boolean isDone() {
            int stackSize = Math.max(1, Math.min(cardCount, 10));
            return rawProgress() >= 1f + (stackSize - 1) * STAGGER;
        }
    }

    public static final List<HandSwapAnimation> activeAnimations = new ArrayList<>();

    public static Vec3 getSeatLocalPos(Direction dir) {
        return switch (dir) {
            case NORTH -> new Vec3(1.5, 0.94, 0);
            case SOUTH -> new Vec3(1.5, 0.94, 3);
            case WEST -> new Vec3(0, 0.94, 1.5);
            case EAST -> new Vec3(3, 0.94, 1.5);
            default -> new Vec3(1.5, 0.94, 1.5);
        };
    }

    public BlockEntityMinoTable(BlockPos blockPos, BlockState blockState) {
        super(Mino.BLOCK_ENTITY_TYPE_MINO_TABLE.get(), blockPos, blockState);
        for (Direction direction : PLAYER_ORDER) {
            players.put(direction, null);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag compoundTag) {
        super.saveAdditional(compoundTag);
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<Direction, CardPlayer> entry : players.entrySet()) {
            if (entry.getValue() != null) {
                playersTag.put(entry.getKey().getSerializedName(), entry.getValue().toTag());
            }
        }
        compoundTag.put("players", playersTag);
        if (game != null) {
            compoundTag.put("game", game.toTag());
        }
        compoundTag.put("state", state.toTag());
        if (!award.isEmpty())
            compoundTag.put("award", award.save(new CompoundTag()));
        compoundTag.putBoolean("demo", demo);
        CompoundTag rulesTag = new CompoundTag();
        for (Map.Entry<String, Boolean> entry : rules.entrySet()) {
            rulesTag.putBoolean(entry.getKey(), entry.getValue());
        }
        compoundTag.put("rules", rulesTag);
        compoundTag.putBoolean("gameEnd", gameEnd);
        if (lastPlayedByUuid != null)
            compoundTag.putUUID("lastPlayedBy", lastPlayedByUuid);
        compoundTag.putInt("lastDiscardSize", lastDiscardSize);
    }

    @Override
    public void load(CompoundTag compoundTag) {
        super.load(compoundTag);
        CompoundTag playersTag = compoundTag.getCompound("players");
            for (Direction direction : PLAYER_ORDER) {
                if (playersTag.contains(direction.getSerializedName())) {
                    players.put(direction, new CardPlayer(playersTag.getCompound(direction.getSerializedName())));
                } else {
                players.put(direction, null);
            }
        }
        CardGame previousGame = game;
        if (compoundTag.contains("game")) {
            game = new CardGame(compoundTag.getCompound("game"));
            game.tableEntity = this;
        } else {
            game = null;
            
            
        }
        ActionMessage newState = new ActionMessage(compoundTag.getCompound("state"));
        if (!newState.equals(state)) {
            if (previousGame == null && game != null) {
                clientMessageList.clear();
            } else {
                clientMessageList.add(new Pair<>(state, System.currentTimeMillis() + 16000));
            }
            state = newState;
            clientMessageList.removeIf(entry -> entry.getFirst().type() == ActionMessage.Type.FAIL);
        }
        if (compoundTag.contains("award")) {
            award = ItemStack.of(compoundTag.getCompound("award"));
        } else {
            award = ItemStack.EMPTY;
        }
        if (compoundTag.contains("demo", Tag.TAG_BYTE)) {
            demo = compoundTag.getBoolean("demo");
        } else {
            demo = false;
        }
        rules.clear();
        if (compoundTag.contains("rules")) {
            CompoundTag rulesTag = compoundTag.getCompound("rules");
            for (String key : rulesTag.getAllKeys()) {
                rules.put(key, rulesTag.getBoolean(key));
            }
        }
        gameEnd = compoundTag.getBoolean("gameEnd");

        // Default values for missing rules
        rules.putIfAbsent(RULE_JUMP_IN, true);
        rules.putIfAbsent(RULE_STACKING, true);
        rules.putIfAbsent(RULE_SEVEN0, false);

        // used for animation tracking
        lastPlayedByUuid = compoundTag.hasUUID("lastPlayedBy")
                ? compoundTag.getUUID("lastPlayedBy")
                : null;
        int savedLastDiscardSize = compoundTag.getInt("lastDiscardSize");

        // Seed tracking on game start
        if (game != null && previousGame == null) {
            lastDiscardSize = game.discardDeck.size();
            lastHandSizes.clear();
            for (CardPlayer p : game.players) {
                lastHandSizes.put(p.uuid, p.hand.size());
            }
        }

        // Animation detection
        if (level != null && level.isClientSide && game != null && previousGame != null) {

            boolean isHandSwapPlay = game.topCard != null
                    && (game.topCard.number == 0 || game.topCard.number == 7)
                    && game.discardDeck.size() > savedLastDiscardSize
                    && getRule(RULE_SEVEN0, false);

            // 0 card animate
            if (isHandSwapPlay && game.topCard.number == 0
                    && game.topCard.family == Card.Family.NUMBER) {
                List<Direction> occupiedOrder = PLAYER_ORDER.stream()
                        .filter(d -> players.get(d) != null)
                        .collect(java.util.stream.Collectors.toList());
                int occupiedSize = occupiedOrder.size();
                for (int i = 0; i < occupiedSize; i++) {
                    Direction from = occupiedOrder.get(i);
                    Direction to = occupiedOrder.get(game.isAntiClockwise
                            ? (i - 1 + occupiedSize) % occupiedSize
                            : (i + 1) % occupiedSize);
                    activeAnimations.add(new HandSwapAnimation(
                            getSeatLocalPos(from),
                            getSeatLocalPos(to),
                            5));
                }
            }

            // Card played animation - skip if hand swap just happened
            if (!isHandSwapPlay && game.discardDeck.size() > savedLastDiscardSize
                    && lastPlayedByUuid != null) {
                Direction fromDir = getPlayerDirection(lastPlayedByUuid);
                if (fromDir != null) {
                    activeAnimations.add(new HandSwapAnimation(
                            getSeatLocalPos(fromDir),
                            new Vec3(1.5, 0.94, 1.5),
                            1));
                }
            }

            // Draw animation - skip entirely if hand swap just happened
            if (!isHandSwapPlay) {
                for (CardPlayer p : game.players) {
                    if (p.uuid.equals(lastPlayedByUuid))
                        continue;
                    int prev = lastHandSizes.getOrDefault(p.uuid, 0);
                    int curr = p.hand.size();
                    if (curr > prev) {
                        Direction dir = getPlayerDirection(p.uuid);
                        if (dir != null) {
                            activeAnimations.add(new HandSwapAnimation(
                                    new Vec3(0.5, 0.94, 0.5),
                                    getSeatLocalPos(dir),
                                    curr - prev));
                        }
                    }
                }
            }

            lastDiscardSize = game.discardDeck.size();
            lastHandSizes.clear();
            for (CardPlayer p : game.players) {
                lastHandSizes.put(p.uuid, p.hand.size());
            }
        }

        // Reset values at game end
        if (game == null) {
            lastDiscardSize = 0;
            lastPlayedByUuid = null;
            lastHandSizes.clear();
        }

    }

    public List<CardPlayer> getPlayersList() {
        List<CardPlayer> playersList = new ArrayList<>();
        for (Direction direction : PLAYER_ORDER) {
            if (players.get(direction) != null) {
                playersList.add(players.get(direction));
            }
        }
        return playersList;
    }

    public List<Direction> getEmptyDirections() {
        List<Direction> emptyDirections = new ArrayList<>();
        for (Direction direction : PLAYER_ORDER) {
            if (players.get(direction) == null) {
                emptyDirections.add(direction);
            }
        }
        return emptyDirections;
    }

    private static final int PLAYER_RANGE = 20;

    public void joinPlayerToTable(CardPlayer cardPlayer, Vec3 playerPos) {
        if (game != null) return;
        Vec3 centerPos = Vec3.atCenterOf(getBlockPos().offset(1, 0, 1));
        Vec3 playerOffset = playerPos.subtract(centerPos.x, centerPos.y, centerPos.z);
        Direction playerDirection = Direction.fromYRot(Mth.atan2(playerOffset.z, playerOffset.x) * 180 / Math.PI - 90);
        for (Direction checkDir : players.keySet()) {
            if (cardPlayer.equals(players.get(checkDir))) {
                players.put(checkDir, null);
            }
        }
        players.put(playerDirection, cardPlayer);
        sync();
    }

    @SuppressWarnings("unchecked, rawtypes")
    public void startGame(CardPlayer initiator) {
        if (game != null) return;
        List<CardPlayer> playerList = getPlayersList();
        if (playerList.size() < 2) return;

        AABB searchArea = AABB.ofSize(Vec3.atLowerCornerWithOffset(getBlockPos(), 1, 1, 1), PLAYER_RANGE, PLAYER_RANGE, PLAYER_RANGE);
        for (CardPlayer cardPlayer : playerList) {
            boolean playerFound = false;
            for (Entity entity : level.getEntities(null, searchArea)) {
                if (entity instanceof Player mcPlayer) {
                    if (cardPlayer.uuid.equals(mcPlayer.getGameProfile().getId())) {
                        ItemStack handCard = new ItemStack(Mino.ITEM_HAND_CARDS.get());
                        ItemDataUtils.setCardGameBinding(handCard, getBlockPos(), cardPlayer.uuid);
                        if (Inventory.isHotbarSlot(mcPlayer.getInventory().selected)
                                && mcPlayer.getInventory().getSelected().isEmpty()) {
                            mcPlayer.getInventory().setItem(mcPlayer.getInventory().selected, handCard);
                            playerFound = true;
                        } else {
                            boolean addSuccessful = mcPlayer.getInventory().add(handCard);
                            if (!addSuccessful) {
                                ItemEntity itemEntity = mcPlayer.drop(handCard, false);
                                if (itemEntity != null) {
                                    itemEntity.setNoPickUpDelay();
                                    itemEntity.setTarget(mcPlayer.getUUID());
                                }
                            }
                            mcPlayer.displayClientMessage(Component.translatable("game.minopp.play.hand_card_in_inventory"), false);
                            playerFound = true;
                        }
                    }
                } else {
                    if (cardPlayer.uuid.equals(entity.getUUID())) {
                        playerFound = true;
                    }
                }
                if (playerFound) break;
            }
            if (!playerFound) {
                destroyGame(initiator);
                state = ActionReport.builder(initiator).panic(Component.translatable("game.minopp.play.player_unavailable", cardPlayer.name)).state;
                return;
            }
        }

        players.values().forEach(p -> { if (p != null) {
            p.hand.clear();
            p.hasShoutedMino = false;
        } });
        game = new CardGame(getPlayersList());
        game.tableEntity = this;
        state = game.initiate(initiator, 7).state;
        sendSeatActionTakenToAll();
        sync();
    }

    public void destroyGame(CardPlayer initiator) {
        if (game != null) sendSeatActionTakenToAll();
        gameEnd = true;
        game = null;

        for (Player mcPlayer : level.players()) {
            for (ItemStack invItem : mcPlayer.getInventory().items) {
                if (!invItem.is(Mino.ITEM_HAND_CARDS.get())) continue;
                BlockPos tablePos = ItemDataUtils.getBlockPos(invItem);
                if (tablePos != null && tablePos.equals(getBlockPos())) {
                    mcPlayer.getInventory().removeItem(invItem);
                }
            }
        }

        for (CardPlayer cardPlayer : players.values()) {
            if (cardPlayer == null) continue;
            Entity entity = ((ServerLevel)level).getEntity(cardPlayer.uuid);
            if (entity instanceof LivingEntity livingEntity) {
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack stack = livingEntity.getItemInHand(hand);
                    if (stack.is(Mino.ITEM_HAND_CARDS.get())) {
                        livingEntity.setItemInHand(hand, ItemStack.EMPTY);
                    }
                }
            }
        }

        players.values().forEach(p -> { if (p != null) {
            p.hand.clear();
            p.hasShoutedMino = false;
        } });
        state = ActionReport.builder(initiator).gameDestroyed().state;
        sync();
    }
    public void resetSeats(CardPlayer initiator) {
        sendSeatActionTakenToAll();
        players.replaceAll((d, v) -> null);
        state = ActionReport.builder(initiator).panic(Component.translatable("game.minopp.play.seats_reset", initiator.name)).state;
        sync();
    }

    public void handleActionResult(ActionReport result, CardPlayer cardPlayer, ServerPlayer player) {
        if (result != null) {
            if (result.shouldDestroyGame) {
                destroyGame(cardPlayer);
            }
            if (result.state != null) state = result.state;
            for (ActionMessage message : result.messages) {
                switch (message.type()) {
                    case FAIL -> {
                        if (player != null) S2CActionEphemeralPacket.sendS2C(player, getBlockPos(), message);
                    }
                    case MESSAGE_ALL -> sendMessageToAll(message);
                }
            }
            if (!result.effects.isEmpty()) {
                MinecraftServer server = ((ServerLevel)level).getServer();
                BlockPos tableCenterPos = getBlockPos().offset(1, 0, 1);
                for (EffectEvent effect : result.effects) {
                    effect.summonServer((ServerLevel) level, tableCenterPos, this);
                }
                for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                    if (serverPlayer.level().dimension() == level.dimension()) {
                        if (serverPlayer.position().distanceToSqr(Vec3.atCenterOf(tableCenterPos)) <= EffectEvents.EFFECT_RADIUS * EffectEvents.EFFECT_RADIUS) {
                            boolean playerPartOfGame = getPlayersList().stream().anyMatch(p -> p.uuid.equals(serverPlayer.getGameProfile().getId()));
                            S2CEffectListPacket.sendS2C(serverPlayer, result.effects, tableCenterPos, playerPartOfGame);
                        }
                    }
                }
            }

        // Auto-resolve draw penalty for next player if they can't stack
        if (game != null && game.drawCount > 0) {
            CardPlayer penalisedPlayer = game.players.get(game.currentPlayerIndex);
            ActionReport penaltyReport = game.resolveDrawPenalty();
            if (penaltyReport != null) {
                handleActionResult(penaltyReport, penalisedPlayer, null);
            }
        }
        }
            sync();
        }

    private void sendMessageToAll(ActionMessage message) {
        for (CardPlayer player : getPlayersList()) {
            Player mcPlayer = level.getPlayerByUUID(player.uuid);
            if (mcPlayer != null) {
                S2CActionEphemeralPacket.sendS2C((ServerPlayer) mcPlayer, getBlockPos(), message);
            }
        }
    }

    public void sendSeatActionTakenToAll() {
        for (CardPlayer player : getPlayersList()) {
            Player mcPlayer = level.getPlayerByUUID(player.uuid);
            BlockPos tableCenterPos = getBlockPos().offset(1, 0, 1);
            List<EffectEvent> events = List.of(new SeatActionTakenEffectEvent());
            if (mcPlayer != null) {
                S2CEffectListPacket.sendS2C((ServerPlayer) mcPlayer, events, tableCenterPos, true);
            }
        }
    }

    public void sync() {
        setChanged();
        BlockState blockState = level.getBlockState(getBlockPos());
        level.sendBlockUpdated(getBlockPos(), blockState, blockState, 2);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}