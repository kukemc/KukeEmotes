package kuke.emotes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import kuke.emotes.KukeEmotes;
import kuke.emotes.bobj.BOBJArmature;
import kuke.emotes.bobj.BOBJBone;
import kuke.emotes.client.EmoteAssets;
import kuke.emotes.client.EmoteSession;
import kuke.emotes.client.model.EmoteMesh;
import kuke.emotes.client.model.EmoteModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws an emoting player as the BOBJ model instead of the vanilla one.
 *
 * <p>The transform chain reproduces upstream's: pose the armature (head look first, then the emote
 * animation on top of it, so an emote that animates the head wins), skin the meshes on the CPU,
 * then rotate to the player's body yaw and scale to the model's authored scale. The pose stack
 * arrives already translated to the entity's feet by {@code EntityRenderDispatcher}, which is where
 * {@code RenderPlayerEvent.Pre} fires, so there is no translation to do here.
 *
 * <p>Every failure path returns false, and the caller then lets the vanilla render run — an emote
 * that cannot draw must never leave an invisible player behind.
 */
public final class EmoteRenderer {

    private static final float DEG_TO_RAD = (float) Math.PI / 180F;

    private EmoteRenderer() {
    }

    public static boolean render(PlayerRenderState state, EmoteSession session, PoseStack poseStack,
            MultiBufferSource buffers, int light) {
        if (!EmoteAssets.INSTANCE.isReady() || state.isInvisible) {
            return false;
        }

        EmoteModel model = EmoteAssets.INSTANCE.model(state.skin.model());

        if (model == null || model.body() == null) {
            return false;
        }

        try {
            poseArmature(model, state, session);

            poseStack.pushPose();
            /* Upstream: glRotatef(180 - (yaw - 180)) — the BOBJ model faces the opposite way from
             * the vanilla one, so this is not the usual 180 - yaw. */
            poseStack.mulPose(Axis.YP.rotationDegrees(180F - (state.bodyRot - 180F)));
            poseStack.scale(model.scale, model.scale, model.scale);

            PoseStack.Pose pose = poseStack.last();
            int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(state.hasRedOverlay));

            EmoteMesh body = model.body();
            body.updateMesh();

            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(state.skin.texture()));
            body.emit(pose, consumer, light, overlay, 1F, 1F, 1F, 1F);

            poseStack.popPose();

            return true;
        } catch (Throwable t) {
            KukeEmotes.LOGGER.error("Emote render failed for {}; falling back to the vanilla player", state.name, t);

            return false;
        }
    }

    private static void poseArmature(EmoteModel model, PlayerRenderState state, EmoteSession session) {
        BOBJArmature armature = model.armature;

        armature.resetBones();

        BOBJBone head = armature.bones.get(model.headBone);

        if (head != null) {
            /* PlayerRenderState.yRot is already head-yaw relative to the body. */
            head.rotateX = state.xRot * DEG_TO_RAD;
            head.rotateY = -state.yRot * DEG_TO_RAD;
        }

        session.playback.apply(armature, state.partialTick);
        armature.setupMatrices();
    }
}
