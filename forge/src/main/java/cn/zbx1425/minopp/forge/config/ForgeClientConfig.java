package cn.zbx1425.minopp.forge.config;

import net.minecraftforge.common.ForgeConfigSpec;
import cn.zbx1425.minopp.forge.config.ForgeClientConfig;

public class ForgeClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue GUI_X_POSITION;
    public static final ForgeConfigSpec.IntValue GUI_Y_POSITION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GAME_HISTORY_UI;

    static {
        BUILDER.push("display");
        GUI_X_POSITION = BUILDER
            .comment("X position of the game GUI. Negative goes left, positive goes right. Default is 20.")
            .defineInRange("guiXPosition", 20, Integer.MIN_VALUE, Integer.MAX_VALUE);

        GUI_Y_POSITION = BUILDER
            .comment("Y position of the game GUI. Negative goes up, positive goes down. Default is 60.")
            .defineInRange("guiYPosition", 60, Integer.MIN_VALUE, Integer.MAX_VALUE);
        
        ENABLE_GAME_HISTORY_UI = BUILDER
            .comment("For a more realistic experience, you can choose to disable the game GUI and any on screen reminders (drawing from the deck, turn reminders), the only element that will be displayed is the top card text & jump-in reminders. Default is true (enabled).")
            .define("enableGameHistoryGUI", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}