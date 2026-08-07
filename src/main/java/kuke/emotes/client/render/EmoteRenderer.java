package kuke.emotes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import kuke.emotes.KukeEmotes;
import kuke.emotes.bobj.BOBJArmature;
import kuke.emotes.bobj.BOBJBone;
import kuke.emotes.client.EmoteAssets;
import kuke.emotes.client.EmoteSession;
import kuke.emotes.client.effect.EmoteEffectContext;
import kuke.emotes.client.model.EmoteMesh;
import kuke.emotes.client.model.EmoteModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

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
 *
 * <p><b>Armour and weapons are deliberately not drawn.</b> Cancelling the vanilla render drops the
 * armour and item-in-hand layers with it, and that is the intended look: the player puts their kit
 * away to perform. The only things in hand are the emote's own props (the popcorn bucket, the
 * rock/paper/scissors sign).
 */
public final class EmoteRenderer {

    private static final float DEG_TO_RAD = (float) Math.PI / 180F;

    private static final ResourceLocation POPCORN_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(KukeEmotes.MOD_ID, "textures/popcorn.png");

    /** Scratch buffer for handing a bone matrix to the pose stack, column-major as GL wants it. */
    private static final float[] COLUMN_MAJOR = new float[16];

    private static final ItemStackRenderState PROP_RENDER_STATE = new ItemStackRenderState();

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
            runEffects(model, state, session);

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

            renderProps(model, session, pose, buffers, light, overlay);
            renderHeldProp(model, session, poseStack, buffers, light);

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

    /**
     * Particle bursts and prop timing. Runs from the render path because bone positions only exist
     * once the skeleton is posed, but {@link EmoteSession#runEffect} gates it to one call per
     * animation tick.
     */
    private static void runEffects(EmoteModel model, PlayerRenderState state, EmoteSession session) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        if (!(minecraft.level.getEntity(state.id) instanceof Player player)) {
            return;
        }

        session.runEffect(
            new EmoteEffectContext(player, minecraft.level, model.armature, session, state.bodyRot,
                state.partialTick),
            (int) session.playback.getTick(state.partialTick));
    }

    /** Prop meshes skinned to the same skeleton — currently just the popcorn bucket. */
    private static void renderProps(EmoteModel model, EmoteSession session, PoseStack.Pose pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (!session.isPropVisible(EmoteModel.POPCORN)) {
            return;
        }

        EmoteMesh popcorn = model.mesh(EmoteModel.POPCORN);

        if (popcorn == null) {
            return;
        }

        popcorn.updateMesh();
        popcorn.emit(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(POPCORN_TEXTURE)),
            light, overlay, 1F, 1F, 1F, 1F);
    }

    /** An item the emote itself puts in the hand (rock/paper/scissors), scaled in and out by it. */
    private static void renderHeldProp(EmoteModel model, EmoteSession session, PoseStack poseStack,
            MultiBufferSource buffers, int light) {
        ItemStack stack = session.prop();
        float scale = session.propScale();

        if (stack.isEmpty() || scale <= 0F) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        BOBJBone hand = model.armature.bones.get(EmoteModel.RIGHT_HAND_BONE);

        if (hand == null) {
            return;
        }

        poseStack.pushPose();
        multiplyByBone(poseStack, hand);

        /* Hand attachment from upstream's default_simple.json: rx -90, ry 180, y 0.1, z -0.1 */
        poseStack.translate(0F, 0.1F, -0.1F);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180F));

        minecraft.getItemModelResolver().updateForTopItem(PROP_RENDER_STATE, stack,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, minecraft.level, null, 0);
        PROP_RENDER_STATE.render(poseStack, buffers, light, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    /**
     * Multiply the pose stack by a bone's model-space matrix. The BOBJ matrices are row-major
     * (vecmath order), JOML is column-major — hence the transpose while copying.
     */
    private static void multiplyByBone(PoseStack poseStack, BOBJBone bone) {
        kuke.emotes.math.Matrix4f m = bone.mat;

        COLUMN_MAJOR[0] = m.m00; COLUMN_MAJOR[1] = m.m10; COLUMN_MAJOR[2] = m.m20; COLUMN_MAJOR[3] = m.m30;
        COLUMN_MAJOR[4] = m.m01; COLUMN_MAJOR[5] = m.m11; COLUMN_MAJOR[6] = m.m21; COLUMN_MAJOR[7] = m.m31;
        COLUMN_MAJOR[8] = m.m02; COLUMN_MAJOR[9] = m.m12; COLUMN_MAJOR[10] = m.m22; COLUMN_MAJOR[11] = m.m32;
        COLUMN_MAJOR[12] = m.m03; COLUMN_MAJOR[13] = m.m13; COLUMN_MAJOR[14] = m.m23; COLUMN_MAJOR[15] = m.m33;

        poseStack.mulPose(new org.joml.Matrix4f().set(COLUMN_MAJOR));
    }
}
