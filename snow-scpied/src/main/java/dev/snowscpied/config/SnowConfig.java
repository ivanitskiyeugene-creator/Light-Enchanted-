package dev.snowscpied.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class SnowConfig {
    public static class Client {
        public final ForgeConfigSpec.BooleanValue enableDeformation;
        public final ForgeConfigSpec.BooleanValue enableFootprints;
        public final ForgeConfigSpec.BooleanValue enableBloodStains;
        public final ForgeConfigSpec.BooleanValue enableUniversalSnowOverlay;
        public final ForgeConfigSpec.IntValue footprintLifetimeTicks;
        public final ForgeConfigSpec.IntValue maxFootprints;
        public final ForgeConfigSpec.IntValue maxBloodStains;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("general_client");

            enableDeformation = builder
                    .comment("Enable dynamic snow physical depression under entities.")
                    .define("enableDeformation", true);

            enableFootprints = builder
                    .comment("Enable dynamic footprints (boots, claws, hooves) on snow.")
                    .define("enableFootprints", true);

            enableBloodStains = builder
                    .comment("Enable blood and fluid splatters on snow surfaces.")
                    .define("enableBloodStains", true);

            enableUniversalSnowOverlay = builder
                    .comment("Enable universal procedural snow mantle on any custom 3D mod block.")
                    .define("enableUniversalSnowOverlay", true);

            footprintLifetimeTicks = builder
                    .comment("Lifetime of footprints before fading/melting (in ticks, 20 ticks = 1 sec).")
                    .defineInRange("footprintLifetimeTicks", 1200, 100, 24000);

            maxFootprints = builder
                    .comment("Maximum active footprints tracked simultaneously.")
                    .defineInRange("maxFootprints", 512, 64, 4096);

            maxBloodStains = builder
                    .comment("Maximum active blood stains on snow.")
                    .defineInRange("maxBloodStains", 512, 64, 4096);

            builder.pop();
        }
    }

    public static class Common {
        public final ForgeConfigSpec.BooleanValue snowSlowdown;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("general_common");
            snowSlowdown = builder
                    .comment("Enable realistic movement slowdown in deep snow.")
                    .define("snowSlowdown", true);
            builder.pop();
        }
    }

    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        Pair<Client, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();

        Pair<Common, ForgeConfigSpec> commonPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();
    }
}
