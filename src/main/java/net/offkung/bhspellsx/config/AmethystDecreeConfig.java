package net.offkung.bhspellsx.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config for amethyst_decree — every tunable number the spell uses (radius, damage,
 * durations, tick interval), so they're editable in the generated
 * config/bhspellsx-common.toml without a rebuild. No precedent for a config file exists
 * elsewhere in bhspells/bhspellsx (verified against the decompiled 1.3.0 reference and this
 * repo before adding this); ForgeConfigSpec is the standard mechanism for this in Forge, kept
 * to a single small file rather than a shared framework. MERGE: at merge time these values
 * either fold into a bhspells-wide config (if one exists there by then) or move as-is —
 * ask the IT lead rather than guessing.
 */
public class AmethystDecreeConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue RADIUS;
    public static final ForgeConfigSpec.DoubleValue BURST_DAMAGE;
    public static final ForgeConfigSpec.IntValue ROOT_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue STUN_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue DEBUFF_DURATION_TICKS;
    public static final ForgeConfigSpec.DoubleValue DOT_DAMAGE_PER_TICK;
    public static final ForgeConfigSpec.IntValue DOT_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue DOT_TICK_COUNT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("amethyst_decree");

        RADIUS = builder
                .comment("Radius (blocks) around the caster affected on cast completion.")
                .defineInRange("radius", 6.75, 0.0, 64.0);
        BURST_DAMAGE = builder
                .comment("Immediate fixed damage dealt to each target on hit. Does not scale with spell power/level/gear.")
                .defineInRange("burst_damage", 30.0, 0.0, 1000.0);
        ROOT_DURATION_TICKS = builder
                .comment("Duration (ticks) of the efn:stop hard root applied to each target.")
                .defineInRange("root_duration_ticks", 60, 0, 72000);
        STUN_DURATION_TICKS = builder
                .comment("Duration (ticks) of the cataclysm:stun applied alongside the root.")
                .defineInRange("stun_duration_ticks", 60, 0, 72000);
        DEBUFF_DURATION_TICKS = builder
                .comment("Duration (ticks) of the Slowness II / Weakness I / Mining Fatigue II debuffs.")
                .defineInRange("debuff_duration_ticks", 100, 0, 72000);
        DOT_DAMAGE_PER_TICK = builder
                .comment("Fixed damage dealt per damage-over-time tick. Does not scale with spell power/level/gear.")
                .defineInRange("dot_damage_per_tick", 5.0, 0.0, 1000.0);
        DOT_INTERVAL_TICKS = builder
                .comment("Ticks between each damage-over-time application.")
                .defineInRange("dot_interval_ticks", 20, 1, 72000);
        DOT_TICK_COUNT = builder
                .comment("Number of damage-over-time applications (total DoT duration = dot_interval_ticks * dot_tick_count).")
                .defineInRange("dot_tick_count", 4, 0, 100);

        builder.pop();
        SPEC = builder.build();
    }
}
