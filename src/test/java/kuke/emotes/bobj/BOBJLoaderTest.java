package kuke.emotes.bobj;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the shipped assets on a bare JVM (no Minecraft) and checks the shapes the renderer will
 * rely on. This is the port's safety net for the format layer: if a bone loses its parent or an
 * emote loses its keyframes, it fails here rather than as an invisible or exploded player in-game.
 */
class BOBJLoaderTest {

    private static BOBJLoader.BOBJData model;
    private static BOBJLoader.BOBJData actions;

    @BeforeAll
    static void load() throws Exception {
        model = read("/assets/kukeemotes/models/entity/default_simple.bobj");
        actions = read("/assets/kukeemotes/models/entity/actions.bobj");
    }

    private static BOBJLoader.BOBJData read(String path) throws Exception {
        try (InputStream stream = BOBJLoaderTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing asset: " + path);

            return BOBJLoader.readData(stream);
        }
    }

    @Test
    void modelHasOneArmatureWithAConnectedSkeleton() {
        assertEquals(1, model.armatures.size());

        BOBJArmature armature = model.armatures.values().iterator().next();
        armature.initArmature();

        assertTrue(armature.orderedBones.size() >= 10,
            "expected a full humanoid skeleton, got " + armature.orderedBones.size());

        // Every bone but the root must resolve its parent, otherwise the pose walks off into space.
        List<String> orphans = new ArrayList<>();

        for (BOBJBone bone : armature.orderedBones) {
            if (!bone.parent.isEmpty() && bone.parentBone == null) {
                orphans.add(bone.name + " -> " + bone.parent);
            }
        }

        assertTrue(orphans.isEmpty(), "bones with unresolved parents: " + orphans);

        // The joints the blocky "simple" mesh stitches by hand must exist under these exact names.
        for (String required : new String[] {"body", "low_body", "head",
                "left_arm", "low_left_arm", "right_arm", "low_right_arm",
                "left_leg", "low_left_leg", "right_leg", "low_leg_right"}) {
            assertNotNull(armature.bones.get(required), "missing bone: " + required);
        }
    }

    @Test
    void modelCompilesToASkinnedMesh() {
        Map<String, BOBJLoader.CompiledData> meshes = BOBJLoader.loadMeshes(model);

        BOBJLoader.CompiledData body = meshes.get("body");
        assertNotNull(body, "the model must expose a 'body' mesh: " + meshes.keySet());

        int vertices = body.posData.length / 4;
        assertTrue(vertices > 0, "no vertices compiled");
        assertEquals(vertices * 3, body.normData.length);
        assertEquals(vertices * 2, body.texData.length);
        assertEquals(vertices * 4, body.weightData.length);
        assertEquals(vertices * 4, body.boneIndexData.length);

        // At least one real bone influence per vertex, or the skinning pass falls back to rest pose.
        int unweighted = 0;

        for (int i = 0; i < vertices; i++) {
            boolean weighted = false;

            for (int w = 0; w < 4; w++) {
                if (body.weightData[i * 4 + w] > 0 && body.boneIndexData[i * 4 + w] >= 0) {
                    weighted = true;
                    break;
                }
            }

            if (!weighted) {
                unweighted++;
            }
        }

        assertEquals(0, unweighted, unweighted + " of " + vertices + " vertices have no bone influence");
    }

    @Test
    void everyShippedEmoteHasAnAction() {
        List<String> emotes = new ArrayList<>();

        for (String name : actions.actions.keySet()) {
            if (name.startsWith("emote_")) {
                emotes.add(name);
            }
        }

        assertTrue(emotes.size() >= 60, "expected the full emote set, found " + emotes.size());

        // Ragdoll emotes live in their own file; everything else must be here with real keyframes.
        List<String> empty = new ArrayList<>();

        for (String name : emotes) {
            BOBJAction action = actions.actions.get(name);

            if (action.groups.isEmpty() || action.getDuration() <= 0) {
                empty.add(name);
            }
        }

        assertTrue(empty.isEmpty(), "emotes with no usable keyframes: " + empty);
    }

    @Test
    void locomotionActionsSurvivedTheSplitOut() {
        // Not used while only emotes take over the render, but they ship in the same file and are
        // what a future "full takeover" mode would need — assert they parsed rather than silently
        // going missing.
        for (String required : new String[] {"idle", "running", "jump", "crouching", "flying"}) {
            assertNotNull(actions.actions.get(required), "missing locomotion action: " + required);
        }
    }

    @Test
    void keyframeCurvesEvaluateInsideTheirRange() {
        BOBJAction wave = actions.actions.get("emote_wave");
        assertNotNull(wave);

        int duration = wave.getDuration();
        assertTrue(duration > 0);

        for (BOBJGroup group : wave.groups.values()) {
            for (BOBJChannel channel : group.channels) {
                if (channel.keyframes.isEmpty()) {
                    continue;
                }

                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;

                for (BOBJKeyframe keyframe : channel.keyframes) {
                    min = Math.min(min, keyframe.value);
                    max = Math.max(max, keyframe.value);
                }

                // Bezier handles can overshoot, but only slightly — a blown-up value here means the
                // interpolation solver is wrong, which shows up in-game as a limb shooting off.
                float slack = Math.max(1F, (max - min) * 2F);

                for (float frame = 0; frame <= duration; frame += 0.5F) {
                    float value = channel.calculate(frame);

                    assertTrue(Float.isFinite(value),
                        "non-finite value on " + group.name + "/" + channel.path + " at frame " + frame);
                    assertTrue(value >= min - slack && value <= max + slack,
                        "runaway interpolation on " + group.name + "/" + channel.path
                            + " at frame " + frame + ": " + value + " outside [" + min + ", " + max + "]");
                }
            }
        }
    }

    @Test
    void armatureMatricesAreFiniteAcrossAWholeEmote() {
        BOBJArmature armature = model.armatures.values().iterator().next();
        armature.initArmature();

        BOBJAction dance = actions.actions.get("emote_default");
        assertNotNull(dance);

        for (float frame = 0; frame <= dance.getDuration(); frame += 1F) {
            armature.resetBones();

            for (Map.Entry<String, BOBJGroup> entry : dance.groups.entrySet()) {
                BOBJBone bone = armature.bones.get(entry.getKey());

                if (bone != null) {
                    entry.getValue().apply(bone, frame);
                }
            }

            armature.setupMatrices();

            for (BOBJBone bone : armature.orderedBones) {
                assertFalse(Float.isNaN(armature.matrices[bone.index].m03),
                    "NaN in bone " + bone.name + " at frame " + frame);
            }
        }
    }
}
