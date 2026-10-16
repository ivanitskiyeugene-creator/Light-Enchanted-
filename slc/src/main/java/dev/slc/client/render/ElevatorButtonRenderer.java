package dev.slc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.slc.elevator.ElevatorButtonBlock;
import dev.slc.elevator.ElevatorButtonBlockEntity;
import dev.slc.elevator.ElevatorState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Renders the LIVE elevator position diagram on the panel:
 * hollow node squares for every floor, a right-side route line,
 * and the filled solid box that slides to the cabin's actual position.
 * Drawn with unlit position-color quads so it always glows like the SCP:SL reference.
 */
public class ElevatorButtonRenderer implements BlockEntityRenderer<ElevatorButtonBlockEntity> {

    // Block-local panel space in PIXELS divided by 16 (BER pose space = block units 0..1,
    // unlike model JSONs which use 0..16 pixel units). Compact panel: plate 5-11 x 3.5-12.5.
    private static final float CX = 7.0f / 16.0f;           // diagram horizontal center
    private static final float CELL_TOP = 9.9f / 16.0f;     // y of the TOP floor node
    private static final float CELL_BOTTOM = 6.1f / 16.0f;  // y of the BOTTOM floor node
    private static final float HALF = 0.85f / 16.0f;        // node half-size
    private static final float THICK = 0.45f / 16.0f;       // outline thickness
    private static final float LANE_X0 = 8.9f / 16.0f;      // vertical route lane
    private static final float LANE_X1 = 9.25f / 16.0f;
    private static final float Z = 14.36f / 16.0f;          // quad plane (just in front of the screen)
    private static final float STUB_THICK = 0.18f / 16.0f;

    private static final int R = 64, G = 118, B = 204, A = 255;

    public ElevatorButtonRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ElevatorButtonBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        int floors = be.getFloorCount();
        if (!be.hasNetwork() || floors < 2) return;

        Direction facing = be.getBlockState().hasProperty(ElevatorButtonBlock.FACING)
                ? be.getBlockState().getValue(ElevatorButtonBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        poseStack.translate(-0.5, -0.5, -0.5);

        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.debugQuads());

        // Floor node y-coords (floor index 0 = lowest floor -> bottom of the diagram)
        float[] nodeY = new float[floors];
        for (int i = 0; i < floors; i++) {
            nodeY[i] = floors == 1 ? (CELL_TOP + CELL_BOTTOM) / 2.0f
                    : CELL_BOTTOM - i * (CELL_BOTTOM - CELL_TOP) / (float) (floors - 1);
        }

        // Vertical route lane on the right
        quad(vc, m, LANE_X0, nodeY[floors - 1], LANE_X1, nodeY[0]);
        // Horizontal stub into every node + hollow node outline
        for (int i = 0; i < floors; i++) {
            quad(vc, m, CX + HALF, nodeY[i] - STUB_THICK, LANE_X1, nodeY[i] + STUB_THICK);
            hollow(vc, m, CX, nodeY[i]);
        }

        // Filled "where is the lift" box at the cabin's ACTUAL position
        float boxY;
        if (be.getState() == ElevatorState.MOVING) {
            float total = Math.max(1, be.getTravelTotal());
            float ticksLeft = Math.max(0, be.getTimerTicks() - partialTick);
            float progress = Mth.clamp(1.0f - ticksLeft / total, 0.0f, 1.0f);
            int src = Mth.clamp(be.getSrcFloor(), 0, floors - 1);
            int dst = Mth.clamp(be.getDstFloor(), 0, floors - 1);
            boxY = Mth.lerp(progress, nodeY[src], nodeY[dst]);
        } else if (be.getState() == ElevatorState.CLOSING_DOORS) {
            boxY = nodeY[Mth.clamp(be.getSrcFloor(), 0, floors - 1)];
        } else {
            boxY = nodeY[Mth.clamp(be.getCabinFloor(), 0, floors - 1)];
        }
        quad(vc, m, CX - HALF, boxY - HALF, CX + HALF, boxY + HALF);

        poseStack.popPose();
    }

    private static void hollow(VertexConsumer vc, Matrix4f m, float cx, float cy) {
        quad(vc, m, cx - HALF, cy - HALF, cx + HALF, cy - HALF + THICK); // top
        quad(vc, m, cx - HALF, cy + HALF - THICK, cx + HALF, cy + HALF); // bottom
        quad(vc, m, cx - HALF, cy - HALF + THICK, cx - HALF + THICK, cy + HALF - THICK); // left
        quad(vc, m, cx + HALF - THICK, cy - HALF + THICK, cx + HALF, cy + HALF - THICK); // right
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1) {
        vc.vertex(m, x0, y1, Z).color(R, G, B, A).endVertex();
        vc.vertex(m, x1, y1, Z).color(R, G, B, A).endVertex();
        vc.vertex(m, x1, y0, Z).color(R, G, B, A).endVertex();
        vc.vertex(m, x0, y0, Z).color(R, G, B, A).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(ElevatorButtonBlockEntity be) {
        return false;
    }
}
