package cn.zbx1425.minopp.game;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.effect.GrantRewardEffectEvent;
import cn.zbx1425.minopp.effect.PlayerFireworkEffectEvent;
import cn.zbx1425.minopp.effect.PlayerGlowEffectEvent;
import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

// reference for later adding in rule checks
// if (tableEntity.getRule(BlockEntityMinoTable.RULE_JUMP_IN, true)) {
//     // jump-in logic
// }


public class CardGame {

  private static final ResourceLocation NOTE_BASS =
    new ResourceLocation("minecraft", "block.note_block.bass");

    public List<CardPlayer> players;
    public int currentPlayerIndex;

    public int drawCount;
    public boolean isSkipping;
    public PlayerActionPhase currentPlayerPhase;

    public BlockEntityMinoTable tableEntity;

    public boolean isAntiClockwise;
    public boolean awaitingCutIn;

    public List<Card> deck = new ArrayList<>();
    public List<Card> discardDeck = new ArrayList<>();
    public Card topCard;

    public boolean anyOtherPlayerCanCutIn() {
        for (int i = 0; i < players.size(); i++) {
            if (i == currentPlayerIndex)
                continue;
            CardPlayer other = players.get(i);
            if (tableEntity.getRule(BlockEntityMinoTable.RULE_JUMP_IN, true) && other.hand.stream()
                    .anyMatch(c -> c.getEquivFamily() == topCard.getEquivFamily() &&
                            c.getEquivSuit() == topCard.getEquivSuit() &&
                            c.number == topCard.number)) {
                return true;
            }
        }
        return false;
    }

    private void cardsRotate(int steps, ActionReport report) {
        int size = players.size();
        if (size <= 1)
            return;

        List<List<Card>> oldHands = new ArrayList<>();
        for (CardPlayer p : players) {
            oldHands.add(new ArrayList<>(p.hand));
            p.hand.clear();
        }

        for (int i = 0; i < size; i++) {
            int targetIndex;
            
            if (isAntiClockwise) {
                targetIndex = (i - steps + size) % size;
            } else {
                targetIndex = (i + steps) % size;
            }
            players.get(targetIndex).hand.addAll(oldHands.get(i));
            
        }
     
    }

    public CardGame(List<CardPlayer> players) {
        this.players = players;
    }

    public ActionReport initiate(CardPlayer cardPlayer, int initialCardCount) {
        if (players.size() < 2) return ActionReport.NO_GAME;
        currentPlayerIndex = new Random().nextInt(players.size());
        drawCount = 0;
        isSkipping = false;
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        isAntiClockwise = false;
        deck = Card.createDeck();
        Collections.shuffle(deck);
        for (int i = 0; i < initialCardCount; i++) {
            for (CardPlayer player : players) {
                player.hand.add(deck.remove(deck.size() - 1));
            }
        }
        Card tobeTopCard = deck.remove(deck.size() - 1);
        while (tobeTopCard.family != Card.Family.NUMBER || tobeTopCard.suit == Card.Suit.WILD) {
            deck.add(tobeTopCard);
            Collections.shuffle(deck);
            tobeTopCard = deck.remove(deck.size() - 1);
        }
        topCard = tobeTopCard;
        return ActionReport.builder(this, cardPlayer)
                .sound(Mino.id("game.play"), 0)
                .sound(NOTE_BASS, 500, players.get(currentPlayerIndex))
                .gameStarted();
    }

    public ActionReport playCard(CardPlayer cardPlayer, Card card, Card.Suit wildSelection, boolean shout) {
        ActionReport report = ActionReport.builder(this, cardPlayer);
        int playerIndex = players.indexOf(cardPlayer);

        // hard early check for jump in to stop players playing during the cut-in window
        if (awaitingCutIn && !(topCard.equals(card) && tableEntity.getRule(BlockEntityMinoTable.RULE_JUMP_IN, true))) {
            return report.fail(Component.translatable("game.minopp.play.not_match_cut"));
        }

        if (playerIndex == -1)
            return report.fail(Component.translatable("game.minopp.play.no_player"));
        if (!cardPlayer.hand.contains(card))
            return report.fail(Component.translatable("game.minopp.play.not_your_card"));
        boolean isCut = false;
        // Cut
        if (awaitingCutIn && playerIndex != currentPlayerIndex) {
            if (!tableEntity.getRule(BlockEntityMinoTable.RULE_JUMP_IN, true)) {
                return report.fail(Component.translatable("game.minopp.play.not_your_turn"));
            }
            isCut = true;
        } else {
            if (awaitingCutIn || playerIndex != currentPlayerIndex)
                return report.fail(Component.translatable("game.minopp.play.not_your_turn"));
        }
        // If there's a draw penalty, player must stack with matching draw card
        if (drawCount > 0) {
            boolean isMatchingDraw = card.family == Card.Family.DRAW && card.number == topCard.number;
            if (!tableEntity.getRule(BlockEntityMinoTable.RULE_STACKING, true) || !isMatchingDraw) {
                return report.fail(Component.translatable("game.minopp.play.must_stack_or_draw"));
            }
        }
        if (!card.canPlayOn(topCard))
            return report.fail(Component.translatable("game.minopp.play.invalid_card"));

        if (isCut)
            currentPlayerIndex = playerIndex;
        doDiscardCard(cardPlayer, card, report);
        if (cardPlayer.hand.isEmpty()) {
            report.effect(new PlayerGlowEffectEvent(cardPlayer.uuid, 6 * 20));
            report.effect(new GrantRewardEffectEvent(cardPlayer.uuid));
            for (int i = 0; i < 5; i++) {
                report.effect(new PlayerFireworkEffectEvent(i * 1000 + 500, cardPlayer.uuid));
            }
            return report.gameWon();
        }
        if (card.suit == Card.Suit.WILD) {
            topCard = topCard.withEquivSuit(wildSelection);
        }
        switch (card.family) {
            case SKIP -> isSkipping = true;
            case REVERSE -> {
                if (players.size() == 2) {
                    isSkipping = true;
                } else {
                    isAntiClockwise = !isAntiClockwise;
                }
            }
            case DRAW -> drawCount -= card.number;
        }
        if (shout) {
            report.combineWith(shoutMino(cardPlayer));
        }

        if (card.number == 0 && !cardPlayer.hand.isEmpty()) {
            for (CardPlayer p : players) {
                report.sound(Mino.id("game.hand_change"), 500, p);
            }
            cardsRotate(1, report);
        }
        advanceTurn(report);
        return isCut ? report.cut() : report.played();
    }

public ActionReport playNoCard(CardPlayer cardPlayer) {
    ActionReport report = ActionReport.builder(this, cardPlayer);
    int playerIndex = players.indexOf(cardPlayer);
    if (playerIndex == -1) return report.fail(Component.translatable("game.minopp.play.no_player"));
    if (playerIndex != currentPlayerIndex || awaitingCutIn) return report.fail(Component.translatable("game.minopp.play.not_your_turn"));

    if (currentPlayerPhase == PlayerActionPhase.DISCARD_HAND) {
        int drawCount = this.drawCount == 0 ? 1 : this.drawCount;
        if (!doDrawCard(cardPlayer, drawCount, report)) {
            return report.panic(Component.translatable("game.minopp.play.deck_depleted"));
        }
        if (this.drawCount > 0) {
            this.topCard = topCard.withEquivFamily(Card.Family.NUMBER);
            this.drawCount = 0;
        }
        report.sound(NOTE_BASS, 500 * (drawCount > 1 ? drawCount + 1 : 1), cardPlayer);
        advanceTurn(report); // immediately advance, no play after draw
        return report.drew(drawCount);
    } else if (currentPlayerPhase == PlayerActionPhase.DISCARD_DRAWN) {
        report.sound(Mino.id("game.pass"), 0);
        advanceTurn(report);
    }

    return report.playedNoCard(false);
}

    public ActionReport shoutMino(CardPlayer realPlayer) {
        ActionReport report = ActionReport.builder(this, realPlayer);
        if (!realPlayer.hasShoutedMino) {
            if (realPlayer.hand.size() <= 1) {
                realPlayer.hasShoutedMino = true;
                report.sound(Mino.id("game.mino_shout"), 0);
                return report.messageAll(Component.translatable("game.minopp.play.mino_shout", realPlayer.name));
            } else {
                if (!doDrawCard(realPlayer, 2, report)) {
                    return report.panic(Component.translatable("game.minopp.play.deck_depleted"));
                }
                realPlayer.hasShoutedMino = true; // Avoid penalty again and again
                report.sound(Mino.id("game.mino_shout"), 0);
                report.sound(Mino.id("game.mino_shout_invalid"), 500);
                return report.messageAll(Component.translatable("game.minopp.play.mino_shout_invalid", realPlayer.name));
            }
        }
        return null;
    }

    public ActionReport doubtMino(CardPlayer srcPlayer, UUID targetPlayerWithoutHand) {
        ActionReport report = ActionReport.builder(this, srcPlayer);
        CardPlayer targetPlayer = deAmputate(targetPlayerWithoutHand);
        if (targetPlayer == null) return report.fail(Component.translatable("game.minopp.play.no_player"));
        if (players.get(currentPlayerIndex).equals(targetPlayer)) {
            return report.fail(Component.translatable("game.minopp.play.doubt_target_playing"));
        } else if (srcPlayer.equals(targetPlayer)) {
            return report.fail(Component.translatable("game.minopp.play.doubt_target_self"));
        } else if (targetPlayer.hasShoutedMino) {
            return report.fail(Component.translatable("game.minopp.play.doubt_target_shouted"));
        } else if (targetPlayer.hand.size() > 1) {
            return report.fail(Component.translatable("game.minopp.play.doubt_target_hand"));
        } else {
            if (!doDrawCard(targetPlayer, 2, report)) {
                return report.panic(Component.translatable("game.minopp.play.deck_depleted"));
            }
            targetPlayer.hasShoutedMino = true;
            report.sound(Mino.id("game.doubt_success"), 0);
            return report.messageAll(Component.translatable("game.minopp.play.doubt_success", srcPlayer.name, targetPlayer.name));
        }
    }

public ActionReport resolveDrawPenalty() {
    if (drawCount <= 0) return null; 
    CardPlayer currentPlayer = players.get(currentPlayerIndex);

    // Checks if current top card is a draw card and player has matching one
    boolean canStack = tableEntity.getRule(BlockEntityMinoTable.RULE_STACKING, true) &&
            topCard.family == Card.Family.DRAW &&
            currentPlayer.hand.stream().anyMatch(c ->
                c.family == Card.Family.DRAW && c.number == topCard.number);

    if (canStack) return null;

    ActionReport report = ActionReport.builder(this, currentPlayer);
    int penalty = drawCount;
    if (!doDrawCard(currentPlayer, penalty, report)) {
    return report.panic(Component.translatable("game.minopp.play.deck_depleted"));
    }
    topCard = topCard.withEquivFamily(Card.Family.NUMBER);
    drawCount = 0;
    advanceTurn(report);
    return report;
}

    public void doDiscardCard(CardPlayer player, Card card, ActionReport report) {
        discardDeck.add(topCard.eraseEquiv());
        topCard = card;
        player.hand.remove(card);
        report.sound(Mino.id("game.play"), 0);
    }

    public boolean doDrawCard(CardPlayer cardPlayer, int drawCount, ActionReport report) {
        if (deck.size() < drawCount) {
            Collections.shuffle(discardDeck);
            deck.addAll(discardDeck);
            discardDeck.clear();
        }
        if (deck.size() < drawCount) {
            return false;
        }
        for (int i = 0; i < drawCount; i++) {
            cardPlayer.hand.add(deck.remove(deck.size() - 1));
            report.sound(Mino.id("game.draw"), 500 * i);
            if (drawCount > 1) {
                report.sound(Mino.id("game.draw"), 500 * i + 200);
            }
        }
        return true;
    }


public void advanceTurn(ActionReport report) {
    currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
    if (isSkipping) currentPlayerIndex = (currentPlayerIndex + (isAntiClockwise ? -1 : 1)) % players.size();
    currentPlayerIndex = (currentPlayerIndex + (isAntiClockwise ? -1 : 1)) % players.size();
    if (currentPlayerIndex < 0) currentPlayerIndex += players.size();
    isSkipping = false;

    // trigger for jump-in, if true then it locks for 5 seconds to allow jump-ins
    if (anyOtherPlayerCanCutIn()) {
        awaitingCutIn = true;
        TaskScheduler.Holder.INSTANCE.schedule(100, () -> {
            awaitingCutIn = false;
        });
        return;
    }

    CardPlayer currentPlayer = players.get(currentPlayerIndex);
    currentPlayer.hasShoutedMino = false;

    if (topCard.number != 0) {
    report.sound(NOTE_BASS, 500, currentPlayer);
    }

    // Auto-draw if next player has penalty and can't stack
    if (drawCount < 0) {
        boolean canStack = currentPlayer.hand.stream().anyMatch(c ->
                c.family == Card.Family.DRAW && c.number == topCard.number);
        if (!canStack) {
            int penalty = -drawCount;
            doDrawCard(currentPlayer, penalty, report);
            topCard = topCard.withEquivFamily(Card.Family.NUMBER);
            drawCount = 0;
            // Now advance again since their turn is forfeit
            currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
            if (isSkipping) currentPlayerIndex = (currentPlayerIndex + (isAntiClockwise ? -1 : 1)) % players.size();
            currentPlayerIndex = (currentPlayerIndex + (isAntiClockwise ? -1 : 1)) % players.size();
            if (currentPlayerIndex < 0)
                currentPlayerIndex += players.size();
            isSkipping = false;
            CardPlayer nextPlayer = players.get(currentPlayerIndex);
            nextPlayer.hasShoutedMino = false;
            if (topCard.number != 0) {
                report.sound(NOTE_BASS, 1000, currentPlayer);
            }
        }

    }
}

public CardPlayer deAmputate(CardPlayer playerWithoutHand) {
    return players.stream().filter(p -> p.equals(playerWithoutHand)).findFirst().orElse(null);
}

public CardPlayer deAmputate(UUID uuid) {
    return players.stream().filter(p -> p.uuid.equals(uuid)).findFirst().orElse(null);
}
    public enum PlayerActionPhase {
        DISCARD_HAND,
        DISCARD_DRAWN,
    }

    public CardGame(CompoundTag tag) {
        currentPlayerIndex = tag.getInt("currentPlayer");
        drawCount = tag.getInt("drawCount");
        isSkipping = tag.getBoolean("isSkipping");
        awaitingCutIn = tag.getBoolean("awaitingCutIn");
        currentPlayerPhase = PlayerActionPhase.valueOf(tag.getString("currentPlayerPhase"));
        isAntiClockwise = tag.getBoolean("isAntiClockwise");
        deck = new ArrayList<>(tag.getList("deck", CompoundTag.TAG_COMPOUND).stream().map(t -> new Card((CompoundTag) t)).toList());
        discardDeck = new ArrayList<>(tag.getList("discardDeck", CompoundTag.TAG_COMPOUND).stream().map(t -> new Card((CompoundTag) t)).toList());
        topCard = new Card(tag.getCompound("topCard"));
        players = new ArrayList<>(tag.getList("players", CompoundTag.TAG_COMPOUND).stream().map(t -> new CardPlayer((CompoundTag)t)).toList());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("currentPlayer", currentPlayerIndex);
        tag.putInt("drawCount", drawCount);
        tag.putBoolean("isSkipping", isSkipping);
        tag.putBoolean("awaitingCutIn", awaitingCutIn);
        tag.putString("currentPlayerPhase", currentPlayerPhase.name());
        tag.putBoolean("isAntiClockwise", isAntiClockwise);
        ListTag deckTag = new ListTag();
        deckTag.addAll(deck.stream().map(Card::toTag).toList());
        tag.put("deck", deckTag);
        ListTag discardDeckTag = new ListTag();
        discardDeckTag.addAll(discardDeck.stream().map(Card::toTag).toList());
        tag.put("discardDeck", discardDeckTag);
        tag.put("topCard", topCard.toTag());
        ListTag playersTag = new ListTag();
        playersTag.addAll(players.stream().map(CardPlayer::toTag).toList());
        tag.put("players", playersTag);
        return tag;
    }
}
