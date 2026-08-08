package kuke.emotes.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import kuke.emotes.bobj.BOBJArmature;
import kuke.emotes.bobj.BOBJBone;
import kuke.emotes.bobj.BOBJLoader;
import kuke.emotes.math.Matrix4f;
import kuke.emotes.math.Vector3f;
import kuke.emotes.math.Vector4f;
import kuke.emotes.util.Interpolations;

import java.util.ArrayList;
import java.util.List;

/**
 * One skinned sub-mesh of an emote model (the body, a piece of armour, a prop).
 *
 * <p>Ported from Emoticons' {@code AnimationMesh} + {@code AnimationSimpleMesh} (GPL-3.0). The
 * skinning maths is upstream's, unchanged; what was replaced is the <em>submission</em> — upstream
 * pushed the deformed vertices into a GL15 VBO and drew with the fixed-function pipeline, which no
 * longer exists. Here the deformed triangles go straight into a {@link VertexConsumer}, so the mesh
 * rides Minecraft's normal entity render types and needs no custom pipeline or shader.
 *
 * <p>The mesh is small (a couple of hundred vertices), so re-skinning it on the CPU every frame is
 * cheaper than any GPU skinning setup would be to maintain.
 */
public class EmoteMesh {

    /** Name of the BOBJ object this came from — "body", "armor_chest", "popcorn", … */
    public final String name;

    public final BOBJLoader.CompiledData data;

    private final BOBJArmature restArmature;
    private BOBJArmature currentArmature;

    /** Deformed copies, rebuilt by {@link #updateMesh()} each frame. */
    private final float[] vertices;
    private final float[] normals;

    /**
     * Blocky-joint stitching. The "simple" models are Minecraft-shaped cubes, so letting the skin
     * weights bend a limb smoothly would melt the cube. Upstream instead detects the vertices that
     * sit in the joint bands of the skin UV and pins them rigidly to the upper bone, offset along Y
     * by how far the joint is bent — which reads as a hard elbow/knee crease instead of a stretch.
     * Only the body mesh does this.
     */
    private final boolean blockyJoints;

    /**
     * Debug toggle for the joint stitching, so a suspect pose can be bisected in-game
     * (/emote debug joints). Not a setting — the stitching is what keeps cube limbs from melting.
     */
    public static boolean jointStitchingEnabled = true;

    private Joint armLeft;
    private Joint armRight;
    private Joint legLeft;
    private Joint legRight;
    private Joint body;
    private boolean jointsClassified;

    private final Vector4f sumVertex = new Vector4f();
    private final Vector4f resultVertex = new Vector4f(0, 0, 0, 0);
    private final Vector3f sumNormal = new Vector3f();
    private final Vector3f resultNormal = new Vector3f(0, 0, 0);

    public EmoteMesh(String name, BOBJLoader.CompiledData data, boolean blockyJoints) {
        this.name = name;
        this.data = data;
        this.restArmature = data.mesh.armature;
        this.restArmature.initArmature();
        this.currentArmature = this.restArmature;
        this.blockyJoints = blockyJoints;

        this.vertices = new float[data.posData.length];
        this.normals = new float[data.normData.length];

        System.arraycopy(data.posData, 0, this.vertices, 0, data.posData.length);
        System.arraycopy(data.normData, 0, this.normals, 0, data.normData.length);

        if (blockyJoints) {
            this.setupJoints();
        }
    }

    public BOBJArmature getArmature() {
        return this.restArmature;
    }

    public void setCurrentArmature(BOBJArmature armature) {
        this.currentArmature = armature;
    }

    public int vertexCount() {
        return this.data.posData.length / 4;
    }

    private void setupJoints() {
        BOBJArmature a = this.restArmature;

        this.armLeft = new Joint(a.bones.get("left_arm"), a.bones.get("low_left_arm"));
        this.armRight = new Joint(a.bones.get("right_arm"), a.bones.get("low_right_arm"));
        this.legLeft = new Joint(a.bones.get("left_leg"), a.bones.get("low_left_leg"));
        // Upstream's bone is genuinely named "low_leg_right", not "low_right_leg".
        this.legRight = new Joint(a.bones.get("right_leg"), a.bones.get("low_leg_right"));
        this.body = new Joint(a.bones.get("body"), a.bones.get("low_body"));
    }

    /**
     * Re-skin the mesh from the armature's current matrix palette. Linear blend skinning over up to
     * four influences per vertex, exactly as upstream.
     */
    public void updateMesh() {
        float[] oldVertices = this.data.posData;
        float[] oldNormals = this.data.normData;
        float[] newVertices = this.vertices;
        float[] newNormals = this.normals;

        Matrix4f[] matrices = this.currentArmature.matrices;

        for (int i = 0, c = newVertices.length / 4; i < c; i++) {
            int count = 0;

            this.resultVertex.set(0, 0, 0, 0);
            this.resultNormal.set(0, 0, 0);

            for (int w = 0; w < 4; w++) {
                float weight = this.data.weightData[i * 4 + w];

                if (weight > 0) {
                    int index = this.data.boneIndexData[i * 4 + w];

                    if (index < 0 || index >= matrices.length || matrices[index] == null) {
                        continue;
                    }

                    this.sumVertex.set(oldVertices[i * 4], oldVertices[i * 4 + 1],
                        oldVertices[i * 4 + 2], oldVertices[i * 4 + 3]);
                    matrices[index].transform(this.sumVertex);
                    this.sumVertex.scale(weight);
                    this.resultVertex.add(this.sumVertex);

                    this.sumNormal.set(oldNormals[i * 3], oldNormals[i * 3 + 1], oldNormals[i * 3 + 2]);
                    matrices[index].transform(this.sumNormal);
                    this.sumNormal.scale(weight);
                    this.resultNormal.add(this.sumNormal);

                    count++;
                }
            }

            if (count == 0) {
                this.resultNormal.set(oldNormals[i * 3], oldNormals[i * 3 + 1], oldNormals[i * 3 + 2]);
                this.resultVertex.set(oldVertices[i * 4], oldVertices[i * 4 + 1], oldVertices[i * 4 + 2], 1);
            }

            /* Thanks MiaoNLI for the fix insight! */
            if (this.resultVertex.w != 0F) {
                this.resultVertex.x /= this.resultVertex.w;
                this.resultVertex.y /= this.resultVertex.w;
                this.resultVertex.z /= this.resultVertex.w;
            }

            newVertices[i * 4] = this.resultVertex.x;
            newVertices[i * 4 + 1] = this.resultVertex.y;
            newVertices[i * 4 + 2] = this.resultVertex.z;
            newVertices[i * 4 + 3] = 1;

            newNormals[i * 3] = this.resultNormal.x;
            newNormals[i * 3 + 1] = this.resultNormal.y;
            newNormals[i * 3 + 2] = this.resultNormal.z;
        }

        if (this.blockyJoints && jointStitchingEnabled) {
            this.processBlockyJoints(newVertices, newNormals);
        }
    }

    private void processBlockyJoints(float[] newVertices, float[] newNormals) {
        if (!this.jointsClassified) {
            this.classifyJointVertices();
            this.jointsClassified = true;
        }

        this.armRight.process(this.data, this.currentArmature, newVertices, newNormals);
        this.armLeft.process(this.data, this.currentArmature, newVertices, newNormals);
        this.legRight.process(this.data, this.currentArmature, newVertices, newNormals);
        this.legLeft.process(this.data, this.currentArmature, newVertices, newNormals);
        this.body.process(this.data, this.currentArmature, newVertices, newNormals);
    }

    /**
     * Sort the joint-band vertices into their limb and their front/back half. The bands are the
     * three horizontal strips of the vanilla 64×64 skin layout where a limb is cut in half.
     */
    private void classifyJointVertices() {
        float rmn1 = 22 / 64F;
        float rmx1 = 30 / 64F;
        float rmn2 = 54 / 64F;
        float rmx2 = 62 / 64F;
        float rmn3 = 38 / 64F;
        float rmx3 = 46 / 64F;

        for (int i = 0, c = this.data.posData.length / 4; i < c; i++) {
            double v = this.data.texData[i * 2 + 1];
            JointType type = JointType.NONE;

            for (int j = 0; j < 4; j++) {
                int boneIndex = this.data.boneIndexData[i * 4 + j];

                if (boneIndex < 0 || boneIndex >= this.currentArmature.orderedBones.size()) {
                    continue;
                }

                BOBJBone bone = this.currentArmature.orderedBones.get(boneIndex);

                if (bone.name.contains("leg")) {
                    type = JointType.LEG;
                } else if (bone.name.contains("arm")) {
                    type = JointType.ARM;
                } else if (bone.name.contains("body")) {
                    type = JointType.BODY;
                }

                if (type != JointType.NONE) {
                    break;
                }
            }

            boolean inBand = (v >= rmn1 && v <= rmx1) || (v >= rmn2 && v <= rmx2) || (v >= rmn3 && v <= rmx3);

            if (inBand && type != JointType.NONE) {
                float z = this.data.posData[i * 4 + 2];
                Joint joint;

                if (type == JointType.BODY) {
                    joint = this.body;
                } else if (v > 3 / 4F) {
                    joint = type == JointType.LEG ? this.legLeft : this.armLeft;
                } else {
                    joint = type == JointType.LEG ? this.legRight : this.armRight;
                }

                (z < 0 ? joint.back : joint.front).add(i);
            }
        }
    }

    /**
     * Push the deformed triangles into {@code consumer}.
     *
     * <p>Entity render types take quads, and BOBJ geometry is triangles, so each triangle is
     * emitted as a degenerate quad (last vertex repeated). {@code indexData} is a plain 0..n
     * sequence — upstream never de-duplicated vertices — so triples of consecutive vertices are the
     * triangles.
     */
    public void emit(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        int[] indices = this.data.indexData;

        for (int i = 0; i + 2 < indices.length; i += 3) {
            int a = indices[i];
            int b = indices[i + 1];
            int c = indices[i + 2];

            this.emitVertex(pose, consumer, a, light, overlay, red, green, blue, alpha);
            this.emitVertex(pose, consumer, b, light, overlay, red, green, blue, alpha);
            this.emitVertex(pose, consumer, c, light, overlay, red, green, blue, alpha);
            this.emitVertex(pose, consumer, c, light, overlay, red, green, blue, alpha);
        }
    }

    private void emitVertex(PoseStack.Pose pose, VertexConsumer consumer, int index, int light, int overlay,
            float red, float green, float blue, float alpha) {
        consumer.addVertex(pose,
                this.vertices[index * 4],
                this.vertices[index * 4 + 1],
                this.vertices[index * 4 + 2])
            .setColor(red, green, blue, alpha)
            .setUv((float) this.data.texData[index * 2], (float) this.data.texData[index * 2 + 1])
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose,
                this.normals[index * 3],
                this.normals[index * 3 + 1],
                this.normals[index * 3 + 2]);
    }

    /** A limb's upper bone plus the joint bone whose bend drives the crease. */
    public static class Joint {

        private static final Vector4f TEMPORARY = new Vector4f();

        public final List<Integer> front = new ArrayList<Integer>();
        public final List<Integer> back = new ArrayList<Integer>();
        public final BOBJBone top;
        public final BOBJBone joint;

        public Joint(BOBJBone top, BOBJBone joint) {
            this.top = top;
            this.joint = joint;
        }

        public boolean isFilled() {
            return !this.front.isEmpty();
        }

        public void process(BOBJLoader.CompiledData data, BOBJArmature armature, float[] posData, float[] normalData) {
            if (this.top == null || this.joint == null) {
                return;
            }

            final float pi = (float) Math.PI;

            float rotation = this.joint.rotateX;
            float frontFactor = Interpolations.clamp((rotation + pi / 2F) / pi, 0, 1);
            float backFactor = 1 - frontFactor;

            this.processSide(data, armature, this.front, posData, normalData, frontFactor);
            this.processSide(data, armature, this.back, posData, normalData, backFactor);
        }

        protected void processSide(BOBJLoader.CompiledData data, BOBJArmature armature, List<Integer> indices,
                float[] posData, float[] normalData, float factor) {
            int prevIndex = 0;

            for (int i : indices) {
                float x = data.posData[i * 4];
                float y = data.posData[i * 4 + 1] + factor * 4 / 16F - 2 / 16F;
                float z = data.posData[i * 4 + 2];

                TEMPORARY.set(x, y, z, 1);
                armature.matrices[this.top.index].transform(TEMPORARY);

                posData[i * 4] = TEMPORARY.x;
                posData[i * 4 + 1] = TEMPORARY.y;
                posData[i * 4 + 2] = TEMPORARY.z;
                posData[i * 4 + 3] = TEMPORARY.w;

                /* Copying the normal from the third/second side */
                int base = i - i % 3;
                int a = i - base;
                int b = prevIndex - base;
                int c = 0;

                if (b >= 0) {
                    /* If the previous normal is from the same triangle, work out which vertex the
                     * third one is */
                    if ((a == 0 && b == 2) || (b == 0 && a == 2)) {
                        c = 1;
                    } else if ((a == 0 && b == 1) || (b == 0 && a == 1)) {
                        c = 2;
                    }
                } else {
                    /* With only one sharpened joint vertex, any other one of the triangle will do */
                    c = a == 1 ? 0 : 1;
                }

                c += base;

                normalData[i * 3] = normalData[c * 3];
                normalData[i * 3 + 1] = normalData[c * 3 + 1];
                normalData[i * 3 + 2] = normalData[c * 3 + 2];

                if (b >= 0) {
                    normalData[prevIndex * 3] = normalData[c * 3];
                    normalData[prevIndex * 3 + 1] = normalData[c * 3 + 1];
                    normalData[prevIndex * 3 + 2] = normalData[c * 3 + 2];
                }

                prevIndex = i;
            }
        }
    }

    public enum JointType {
        LEG, ARM, BODY, NONE
    }
}
