package dev.slc.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class SlcConfig {
    public static class Common {
        public final ForgeConfigSpec.IntValue lobbyCountdownSeconds;
        public final ForgeConfigSpec.IntValue decontamTimeSeconds;
        public final ForgeConfigSpec.IntValue warheadDetonationSeconds;
        public final ForgeConfigSpec.BooleanValue allowFriendlyFire;
        public final ForgeConfigSpec.IntValue respawnWaveIntervalSeconds;
        public final ForgeConfigSpec.IntValue startingTickets;
        public final ForgeConfigSpec.IntValue mtfEscapeTicketBonus;
        public final ForgeConfigSpec.IntValue chaosEscapeTicketBonus;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("round");

            lobbyCountdownSeconds = builder
                    .comment("Countdown timer in seconds before the round starts.")
                    .defineInRange("lobbyCountdownSeconds", 15, 5, 120);

            decontamTimeSeconds = builder
                    .comment("Time in seconds before LCZ gas decontamination activates.")
                    .defineInRange("decontamTimeSeconds", 660, 60, 3600);

            warheadDetonationSeconds = builder
                    .comment("Alpha Warhead countdown duration in seconds.")
                    .defineInRange("warheadDetonationSeconds", 90, 30, 300);

            allowFriendlyFire = builder
                    .comment("Whether teammates can damage each other (e.g. MTF vs MTF). Default: false.")
                    .define("allowFriendlyFire", false);

            builder.pop();

            builder.push("respawn_waves");

            respawnWaveIntervalSeconds = builder
                    .comment("Interval in seconds between MTF / Chaos Insurgency respawn waves. Default: 360 (6 minutes).")
                    .defineInRange("respawnWaveIntervalSeconds", 360, 60, 3600);

            startingTickets = builder
                    .comment("Respawn tickets each faction (MTF and Chaos Insurgency) starts the round with.")
                    .defineInRange("startingTickets", 18, 0, 200);

            mtfEscapeTicketBonus = builder
                    .comment("MTF tickets granted for a Scientist escaping (free or converted).")
                    .defineInRange("mtfEscapeTicketBonus", 2, 0, 50);

            chaosEscapeTicketBonus = builder
                    .comment("Chaos tickets granted for a Class-D escaping (free or converted).")
                    .defineInRange("chaosEscapeTicketBonus", 2, 0, 50);

            builder.pop();
        }
    }

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }
}
