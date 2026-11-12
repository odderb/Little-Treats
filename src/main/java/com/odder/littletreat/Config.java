package com.odder.littletreat;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue EFFECT_MARGIN = BUILDER.defineInRange("margin", 4, 0, 50);
    public static final ModConfigSpec.BooleanValue DISABLE_VANILLA_HUNGER = BUILDER.define("disableVanillaHunger", true);
    public static final ModConfigSpec.IntValue ALLOWED_EFFECT_COUNT = BUILDER.defineInRange("allowedEffectCount", 3, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue TICKS_TO_REGEN = BUILDER.defineInRange("ticksToRegen", 30 * 20, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue TICKS_PER_REGEN = BUILDER.defineInRange("ticksPerRegen", 80, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue TICKS_PER_NUTRITION = BUILDER.defineInRange("ticksPerNutrition", 90 * 20, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue GLOBAL_TIME_MULTIPLIER = BUILDER.defineInRange("globalTimeMultiplier", 1.0, 0.0, Double.MAX_VALUE);
    public static final ModConfigSpec.BooleanValue IGNORE_POTION_RENDERING = BUILDER.define("ignorePotionRendering", false);
    public static final ModConfigSpec.DoubleValue LOW_FOOD_WARNING_PERCENT = BUILDER.defineInRange("lowFoodWarningPercent", 0.333, 0.0, 1.0);

    static final ModConfigSpec SPEC = BUILDER.build();
}
