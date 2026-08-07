package kuke.emotes.bobj;

import kuke.emotes.math.Matrix4f;
import kuke.emotes.math.Vector3f;
import kuke.emotes.math.Vector4f;

/**
 * Bone IK modifier ({@code arm_ik}).
 *
 * <p>Ported verbatim from McHorse's Emoticons (GPL-3.0), including its quirks — most notably that
 * {@code new Matrix4f()} is the <em>zero</em> matrix, which is what a root bone with an IK modifier
 * ends up multiplied by. Upstream flags the IK system as unfinished ("it was never finished :(");
 * only three shipped emotes use it ({@code get_funky_IK}, {@code shimmer_IK}, {@code smug_dance_IK}),
 * and those have non-IK twins. Do not "fix" this without re-checking those three against upstream.
 */
public class BOBJBoneModifier {

    /** Target bone which is used for tracking its position. */
    public BOBJBone target;

    /** How many bones should it affect in the chain? */
    public int chain = 0;

    /** Should the bone move to the position of the target (i.e. stick)? */
    public boolean stick;

    private final Vector4f global = new Vector4f();
    private final Vector4f local = new Vector4f();
    private final Matrix4f inverse = new Matrix4f();

    public BOBJBoneModifier(BOBJBone target, int chain, boolean stick) {
        this.target = target;
        this.chain = chain;
        this.stick = stick;
    }

    /**
     * Apply the IK modifier to the given bone. REQUIRES both the given bone and the target bone to
     * have their matrices computed already.
     */
    public void apply(BOBJBone bone) {
        if (this.chain == 0 || this.target == null) {
            return;
        }

        /* Calculate global position of the target */
        this.global.set(0, 0, 0, 1);
        this.target.mat.transform(this.global);

        this.local.set(0, 0, 0, 1);
        bone.mat.transform(this.local);

        this.local.sub(this.global);
        float distance = this.local.length();

        /* Calculate local vector */
        this.inverse.set(bone.mat);
        this.inverse.invert();
        this.local.set(this.global);
        this.inverse.transform(this.local);

        /* Attempt doing look at */
        Vector3f forward = new Vector3f(this.local.x, this.local.y, this.local.z);
        forward.normalize();

        this.local.set(0, 0, 1, 1);
        this.target.mat.transform(this.local);

        Vector3f right = new Vector3f(0, 1, 0);
        right.normalize();
        right.cross(forward, right);
        right.normalize();
        Vector3f up = new Vector3f(0, 0, 0);
        up.cross(right, forward);
        up.normalize();

        /* Orient */
        this.inverse.setIdentity();
        this.inverse.m00 = right.x;
        this.inverse.m10 = right.y;
        this.inverse.m20 = right.z;
        this.inverse.m01 = forward.x;
        this.inverse.m11 = forward.y;
        this.inverse.m21 = forward.z;
        this.inverse.m02 = up.x;
        this.inverse.m12 = up.y;
        this.inverse.m22 = up.z;

        /* Move the bone exactly length away from the target bone */
        if (this.stick) {
            this.local.set(0, distance - bone.length, 0, 1);
            this.inverse.transform(this.local);
            this.inverse.m03 = this.local.x;
            this.inverse.m13 = this.local.y;
            this.inverse.m23 = this.local.z;
        }

        Matrix4f m = new Matrix4f();
        bone.mat.set(bone.relBoneMat);
        bone.applyTransformations();
        bone.mat.mul(this.inverse);

        if (bone.parentBone != null) {
            m = new Matrix4f(bone.parentBone.mat);
        }

        m.mul(bone.mat);
        bone.mat.set(m);
    }
}
