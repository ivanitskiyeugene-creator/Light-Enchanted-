package dev.lightenchanted.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class LightEnchantedConfig {
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

    public static final class Client {
        public final ForgeConfigSpec.EnumValue<RayQuality> rayQuality;
        public final ForgeConfigSpec.BooleanValue enableDynamicShadows;
        public final ForgeConfigSpec.BooleanValue enableVolumetricDust;
        public final ForgeConfigSpec.BooleanValue enableLensFlares;
        public final ForgeConfigSpec.BooleanValue enableColorStaining;
        public final ForgeConfigSpec.IntValue maxBeamRenderDistance;
        public final ForgeConfigSpec.DoubleValue beamBrightnessMultiplier;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Light Enchanted fallback renderer settings. The optional Oculus shaderpack has its own Low / Balanced / Cinematic presets.").push("graphics");

            rayQuality = builder
                    .comment("Fallback Java beam-occlusion quality. This renderer remains available even with the optional Oculus shaderpack so configured beam colour, shape and gameplay behavior are never lost:\nLOW = 49 rays\nMEDIUM = 193 rays\nULTRA = 433 rays")
                    .defineEnum("rayQuality", RayQuality.MEDIUM);

            enableDynamicShadows = builder
                    .comment("Enable Java fallback beam occlusion from fans, fences, and obstacles. The selected Oculus pack additionally uses GPU shadow maps and screen-space tracing for the world.")
                    .define("enableDynamicShadows", true);

            enableVolumetricDust = builder
                    .comment("Enable floating atmospheric dust motes inside light beams")
                    .define("enableVolumetricDust", true);

            enableLensFlares = builder
                    .comment("Enable camera lens flares when looking directly into intense light sources")
                    .define("enableLensFlares", true);

            enableColorStaining = builder
                    .comment("Enable light rays changing color when passing through stained glass")
                    .define("enableColorStaining", true);

            maxBeamRenderDistance = builder
                    .comment("Maximum distance in blocks to render light beams")
                    .defineInRange("maxBeamRenderDistance", 96, 16, 256);

            beamBrightnessMultiplier = builder
                    .comment("Global multiplier for beam volumetric opacity and ground photon brightness")
                    .defineInRange("beamBrightnessMultiplier", 1.0, 0.1, 2.5);

            builder.pop();
        }
    }

    public static final class Common {
        public final ForgeConfigSpec.IntValue maxBeamReach;
        public final ForgeConfigSpec.IntValue maxMirrorBounces;
        public final ForgeConfigSpec.DoubleValue photoreceptorSensitivity;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Light Enchanted Common & Gameplay Settings").push("gameplay");

            maxBeamReach = builder
                    .comment("Maximum distance in blocks a light beam can travel")
                    .defineInRange("maxBeamReach", 64, 8, 128);

            maxMirrorBounces = builder
                    .comment("Maximum number of optical reflections through mirrors")
                    .defineInRange("maxMirrorBounces", 3, 1, 8);

            photoreceptorSensitivity = builder
                    .comment("Sensitivity threshold for photoreceptor sensor activation")
                    .defineInRange("photoreceptorSensitivity", 0.05, 0.01, 1.0);

            builder.pop();
        }
    }

    public enum RayQuality {
        LOW(4, 12, 49),
        MEDIUM(8, 24, 193),
        ULTRA(12, 36, 433);

        public final int rings;
        public final int raysPerRing;
        public final int totalRays;

        RayQuality(int rings, int raysPerRing, int totalRays) {
            this.rings = rings;
            this.raysPerRing = raysPerRing;
            this.totalRays = totalRays;
        }
    }

    private LightEnchantedConfig() {}
}
