package cn.zbx1425.minopp.forge.config;

import net.minecraftforge.common.ForgeConfigSpec;
import cn.zbx1425.minopp.forge.config.ForgeClientConfig;

public class ForgeClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue GUI_X_POSITION;
    public static final ForgeConfigSpec.IntValue GUI_Y_POSITION;

    static {
        BUILDER.push("display");

        GUI_X_POSITION = BUILDER
            .comment("X position of the game GUI. Negative goes left, positive goes right. Default is 20.")
            .defineInRange("guiXPosition", 20, Integer.MIN_VALUE, Integer.MAX_VALUE);

        GUI_Y_POSITION = BUILDER
            .comment("Y position of the game GUI. Negative goes up, positive goes down. Default is 60.")
            .defineInRange("guiYPosition", 60, Integer.MIN_VALUE, Integer.MAX_VALUE);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}