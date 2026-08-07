package kuke.emotes.bobj;

import kuke.emotes.math.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A skeleton: the bones, their order, and the per-frame matrix palette fed to the skinning pass.
 *
 * <p>Ported from McHorse's Emoticons (GPL-3.0).
 */
public class BOBJArmature {

    /** Name of this armature. */
    public String name;

    /** Default action of this armature. */
    public String action = "";

    /** Map of all bones in this armature. */
    public Map<String, BOBJBone> bones = new HashMap<String, BOBJBone>();

    /** All bones from {@link #bones}, ordered by index. */
    public List<BOBJBone> orderedBones = new ArrayList<BOBJBone>();

    /** Bones that carry a {@link BOBJBoneModifier}. */
    public List<BOBJBone> ikBones = new ArrayList<BOBJBone>();

    /** Matrix palette used for transforming vertices. */
    public Matrix4f[] matrices;

    private boolean initialized;

    public BOBJArmature(String name) {
        this.name = name;
    }

    public void addBone(BOBJBone bone) {
        this.bones.put(bone.name, bone);
        this.orderedBones.add(bone);
    }

    /**
     * Connect parent bones to their children and allocate the matrix palette. Runs once.
     */
    public void initArmature() {
        if (this.initialized) {
            return;
        }

        List<BOBJBone> ikBones = new ArrayList<BOBJBone>();

        /* "Connect" parent bones to children bones */
        for (BOBJBone bone : this.bones.values()) {
            if (bone.hasModifiers()) {
                ikBones.add(bone);
            }

            if (!bone.parent.isEmpty()) {
                bone.parentBone = this.bones.get(bone.parent);
                bone.relBoneMat.set(bone.parentBone.boneMat);
                bone.relBoneMat.invert();
                bone.relBoneMat.mul(bone.boneMat);
            } else {
                bone.relBoneMat.set(bone.boneMat);
            }
        }

        /* IK bones, McHorse 2022: it was never finished :( */
        if (!ikBones.isEmpty()) {
            this.ikBones = ikBones;
        }

        /* Sort bones according to their index */
        Collections.sort(this.orderedBones, (o1, o2) -> o1.index - o2.index);

        this.matrices = new Matrix4f[this.orderedBones.size()];
        this.initialized = true;
    }

    /** Recompute the matrix palette from the bones' current TRS. */
    public void setupMatrices() {
        for (BOBJBone bone : this.orderedBones) {
            this.matrices[bone.index] = bone.compute();
        }
    }

    public void copyOrder(BOBJArmature armature) {
        for (BOBJBone bone : armature.orderedBones) {
            BOBJBone thisBone = this.bones.get(bone.name);

            if (thisBone != null) {
                thisBone.index = bone.index;
            }
        }

        Collections.sort(this.orderedBones, (o1, o2) -> o1.index - o2.index);
    }

    /** Reset every bone to its rest pose. */
    public void resetBones() {
        for (BOBJBone bone : this.orderedBones) {
            bone.reset();
        }
    }
}
