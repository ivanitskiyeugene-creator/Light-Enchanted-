package dev.zerosevennine.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Scp079Config {
    public static class Common {
        public final ForgeConfigSpec.IntValue generatorsRequiredForRecontainment;
        public final ForgeConfigSpec.DoubleValue apRegenPerGenerator;
        public final ForgeConfigSpec.IntValue generatorBootTimeTicks;
        public final ForgeConfigSpec.DoubleValue doorToggleApCost;
        public final ForgeConfigSpec.DoubleValue doorLockApCost;
        public final ForgeConfigSpec.DoubleValue teslaOverchargeApCost;
        public final ForgeConfigSpec.DoubleValue blackoutApCost;
        public final ForgeConfigSpec.DoubleValue lockdownApCost;
        public final ForgeConfigSpec.BooleanValue disableScpBlink;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("generators");

            generatorsRequiredForRecontainment = builder
                    .comment("Number of overcharged generators required to recontain (terminate) SCP-079. Default: 3.")
                    .defineInRange("generatorsRequiredForRecontainment", 3, 1, 10);

            apRegenPerGenerator = builder
                    .comment("Bonus AP/sec granted to SCP-079 permanently for each overcharged generator.")
                    .defineInRange("apRegenPerGenerator", 1.2, 0.0, 10.0);

            generatorBootTimeTicks = builder
                    .comment("Time in ticks for a generator to boot up and overcharge (20 ticks = 1 second, 1200 ticks = 60s).")
                    .defineInRange("generatorBootTimeTicks", 1200, 100, 24000);

            builder.pop();

            builder.push("abilities_ap_cost");

            doorToggleApCost = builder
                    .comment("AP cost to toggle a door.")
                    .defineInRange("doorToggleApCost", 5.0, 0.0, 100.0);

            doorLockApCost = builder
                    .comment("AP cost to lock a door.")
                    .defineInRange("doorLockApCost", 15.0, 0.0, 100.0);

            teslaOverchargeApCost = builder
                    .comment("AP cost to overcharge a Tesla gate.")
                    .defineInRange("teslaOverchargeApCost", 35.0, 0.0, 100.0);

            blackoutApCost = builder
                    .comment("AP cost to trigger room blackout (Tier 2+).")
                    .defineInRange("blackoutApCost", 40.0, 0.0, 100.0);

            lockdownApCost = builder
                    .comment("AP cost to trigger room full lockdown (Tier 3+).")
                    .defineInRange("lockdownApCost", 80.0, 0.0, 100.0);

            builder.pop();

            builder.push("compatibility");

            disableScpBlink = builder
                    .comment("Completely disable all blinking mechanics (overlays, timers, blackout blindness, sounds) from SCP mods (such as SCP: Modern, SCP: Lockdown, etc.) globally. Default: true.")
                    .define("disableScpBlink", true);

            builder.pop();

            builder.push("visuals");

            enableCctvShader = builder
                    .comment("Enable the SCP-079 CCTV monitor presentation: world camera post-process plus matching HUD and tactical-map composite. Default: true.")
                    .define("enableCctvShader", true);

            monitorInterfaceStrength = builder
                    .comment("Strength of the matching CRT/monitor composite on the SCP-079 HUD and tactical map. Default: 0.72.")
                    .defineInRange("monitorInterfaceStrength", 0.72, 0.15, 1.0);

            builder.pop();
        }

        public final ForgeConfigSpec.BooleanValue enableCctvShader;
        public final ForgeConfigSpec.DoubleValue monitorInterfaceStrength;
    }

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }
}
