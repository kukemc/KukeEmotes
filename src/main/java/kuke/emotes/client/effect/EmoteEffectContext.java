package kuke.emotes.client.effect;

import kuke.emotes.bobj.BOBJArmature;
import kuke.emotes.bobj.BOBJBone;
import kuke.emotes.client.EmoteSession;
import kuke.emotes.math.Matrix4f;
import kuke.emotes.math.Vector4f;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What an emote effect gets to work with during a render frame: the posed skeleton, the player it
 * belongs to, and a way to turn a point on a bone into world coordinates.
 */
public final class EmoteEffectContext {

    private static final float MODEL_SCALE = 0.9375F;
    private static final float DEG_TO_RAD = (float) Math.PI / 180F;

    public final Player player;
    public final ClientLevel level;
    public final BOBJArmature armature;
    public final EmoteSession session;
    public final float bodyRot;
    public final float partialTick;

    private final Vector4f result = new Vector4f();
    private final Matrix4f rotate = new Matrix4f();

    public EmoteEffectContext(Player player, ClientLevel level, BOBJArmature armature, EmoteSession session,
            float bodyRot, float partialTick) {
        this.player = player;
        this.level = level;
        this.armature = armature;
        this.session = session;
        this.bodyRot = bodyRot;
        this.partialTick = partialTick;
    }

    /**
     * World position of a point expressed in a bone's local space — this is how upstream anchors
     * tears to the head and popcorn to the hand.
     */
    public Vec3 bonePosition(String boneName, float x, float y, float z) {
        BOBJBone bone = this.armature.bones.get(boneName);

        if (bone == null) {
            return this.player.position();
        }

        this.result.set(x, y, z, 1);
        bone.mat.transform(this.result);

        this.rotate.rotY((180F - this.bodyRot + 180F) * DEG_TO_RAD);
        this.rotate.transform(this.result);
        this.result.scale(MODEL_SCALE);

        return new Vec3(
            this.result.x + Mth.lerp(this.partialTick, this.player.xo, this.player.getX()),
            this.result.y + Mth.lerp(this.partialTick, this.player.yo, this.player.getY()),
            this.result.z + Mth.lerp(this.partialTick, this.player.zo, this.player.getZ()));
    }

    public void particle(ParticleOptions particle, Vec3 position, double dx, double dy, double dz) {
        this.level.addParticle(particle, position.x, position.y, position.z, dx, dy, dz);
    }
}
