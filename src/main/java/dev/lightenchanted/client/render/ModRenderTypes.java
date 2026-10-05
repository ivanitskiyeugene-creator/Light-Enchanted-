package dev.lightenchanted.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Custom render types for the beam.
 *
 * The beam uses *normalized additive* blending (SRC_ALPHA, ONE) instead of
 * the vanilla translucent pass: overlapping quads stack their light, so the
 * core looks hotter and the edges die out softly. Face culling is disabled,
 * which lets shapes render from both sides with single-winding quads.
 */
public final class ModRenderTypes extends RenderType {
    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                           boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    public static final TransparencyStateShard ADDITIVE_TRANSPARENCY = new TransparencyStateShard(
            "lightenchanted_additive_transparency",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE);
            },
            () -> {
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
            });

    private static final Function<ResourceLocation, RenderType> BEAM = Util.memoize(texture ->
            create("lightenchanted_beam",
                    DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 256, false, true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType beam(ResourceLocation texture) {
        return BEAM.apply(texture);
    }
}
