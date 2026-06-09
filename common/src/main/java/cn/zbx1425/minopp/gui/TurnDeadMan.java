package cn.zbx1425.minopp.gui;

import cn.zbx1425.minopp.MinoClient;
import cn.zbx1425.minopp.game.CardGame;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class TurnDeadMan {

    public static double deadManElapsedTicks;

    private static final double ALARM_DELAY = 8 * 20;

    public static void pedal() {
        deadManElapsedTicks = Math.min(0, deadManElapsedTicks);
    }

    public static void tick(CardGame game, float partialTicks) {
        deadManElapsedTicks += partialTicks;
        LocalPlayer player = Minecraft.getInstance().player;
        CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
        CardPlayer currentPlayer = game.players.get(game.currentPlayerIndex);
        boolean myTurn = cardPlayer.equals(currentPlayer);
        if (!myTurn) deadManElapsedTicks = 0;
    }

    public static void setOutsideGame() {
        MinoClient.globalFovModifier = 1;
        deadManElapsedTicks = 0;
    }

    public static boolean isAlarmActive() {
        return deadManElapsedTicks > ALARM_DELAY;
    }
}
