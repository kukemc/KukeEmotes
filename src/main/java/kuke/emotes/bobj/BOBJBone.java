package kuke.emotes.bobj;

import kuke.emotes.math.Matrix4f;
import kuke.emotes.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A single armature bone: its rest pose, its parent link and the animated TRS applied on top.
 *
 * <p>Ported from McHorse's Emoticons (GPL-3.0). The matrix maths is untouched — see
 * {@link kuke.emotes.math.Matrix4f} for why it runs on a vecmath-shaped matrix rather than JOML.
 */
public class BOBJBone {

    /* Meta information */
    public int index;
    public String name;
    public String parent;
    public BOBJBone parentBone;
    public List<BOBJBoneModifier> modifiers;

    /* Debug information */
    public Vector3f head;
    public Vector3f tail;
    public float length;

    /* Transformations */
    public float x;
    public float y;
    public float z;

    public float rotateX;
    public float rotateY;
    public float rotateZ;

    public float scaleX = 1;
    public float scaleY = 1;
    public float scaleZ = 1;

    /**
     * Computed bone matrix which is used for transformations. This matrix isn't multiplied by
     * the inverse bone matrix.
     */
    public Matrix4f mat = new Matrix4f();

    /** Bone matrix (rest pose, model space). */
    public Matrix4f boneMat;

    /** Inverse bone matrix. */
    public Matrix4f invBoneMat = new Matrix4f();

    /** Relative-to-parent bone matrix. */
    public Matrix4f relBoneMat = new Matrix4f();

    /** Scratch matrix used for multiplications. */
    public Matrix4f tempMat = new Matrix4f();

    public BOBJBone(int index, String name, String parent, Vector3f tail, Matrix4f boneMat) {
        this.index = index;
        this.name = name;
        this.parent = parent;
        this.boneMat = boneMat;

        this.head = new Vector3f(boneMat.m03, boneMat.m13, boneMat.m23);
        this.tail = tail;

        Vector3f diff = new Vector3f(this.tail);
        diff.sub(this.head);
        this.length = diff.length();

        this.invBoneMat.set(boneMat);
        this.invBoneMat.invert();

        this.relBoneMat.setIdentity();
    }

    /** Add an IK modifier to the list. */
    public void addModifier(BOBJBoneModifier modifier) {
        if (this.modifiers == null) {
            this.modifiers = new ArrayList<BOBJBoneModifier>();
        }

        this.modifiers.add(modifier);
    }

    public Matrix4f compute() {
        Matrix4f mat = this.computeMatrix(new Matrix4f());

        this.mat.set(mat);
        this.applyModifiers();
        mat.set(this.mat);
        mat.mul(this.invBoneMat);

        return mat;
    }

    public boolean hasModifiers() {
        return this.modifiers != null;
    }

    public void applyModifiers() {
        if (this.hasModifiers()) {
            for (BOBJBoneModifier modifier : this.modifiers) {
                modifier.apply(this);
            }
        }
    }

    public Matrix4f computeMatrix(Matrix4f m) {
        m.setIdentity();

        this.mat.set(this.relBoneMat);
        this.applyTransformations();

        if (this.parentBone != null) {
            m = new Matrix4f(this.parentBone.mat);
        }

        m.mul(this.mat);

        return m;
    }

    public void applyTransformations() {
        this.tempMat.setIdentity();
        this.tempMat.m03 = this.x;
        this.tempMat.m13 = this.y;
        this.tempMat.m23 = this.z;
        this.mat.mul(this.tempMat);

        if (!this.hasModifiers()) {
            if (this.rotateZ != 0) {
                this.tempMat.rotZ(this.rotateZ);
                this.mat.mul(this.tempMat);
            }

            if (this.rotateY != 0) {
                this.tempMat.rotY(this.rotateY);
                this.mat.mul(this.tempMat);
            }

            if (this.rotateX != 0) {
                this.tempMat.rotX(this.rotateX);
                this.mat.mul(this.tempMat);
            }
        }

        this.tempMat.setIdentity();
        this.tempMat.m00 = this.scaleX;
        this.tempMat.m11 = this.scaleY;
        this.tempMat.m22 = this.scaleZ;
        this.mat.mul(this.tempMat);
    }

    public void reset() {
        this.x = this.y = this.z = 0;
        this.rotateX = this.rotateY = this.rotateZ = 0;
        this.scaleX = this.scaleY = this.scaleZ = 1;
    }
}
