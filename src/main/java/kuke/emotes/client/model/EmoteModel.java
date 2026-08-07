package kuke.emotes.client.model;

import kuke.emotes.bobj.BOBJArmature;
import kuke.emotes.bobj.BOBJLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A loaded emote model: one armature plus every sub-mesh skinned to it (body, the four armour
 * pieces, props).
 *
 * <p>Corresponds to upstream's {@code Animation} minus the GL resource management — nothing here
 * owns a GPU buffer, so the model is immutable after load and can be shared by every player being
 * rendered. The per-frame state (which armature, the deformed vertices) lives in {@link EmoteMesh},
 * which is why a model instance is <em>not</em> safe to skin for two players at the same time —
 * {@link kuke.emotes.client.render.EmoteRenderer} skins and emits one player at a time within a
 * single frame, which is exactly how the render loop calls it.
 */
public class EmoteModel {

    /** "body" — the mesh that wears the player's skin. */
    public static final String BODY = "body";

    /** The prop meshes that upstream drives from armour slots. */
    public static final String ARMOR_HELMET = "armor_helmet";
    public static final String ARMOR_CHEST = "armor_chest";
    public static final String ARMOR_LEGGINGS = "armor_leggings";
    public static final String ARMOR_FEET = "armor_feet";
    public static final String POPCORN = "popcorn";

    /** Bone the emote props hang off, from upstream's {@code rightHands} attachment map. */
    public static final String RIGHT_HAND_BONE = "low_right_arm.end";

    public final String name;
    public final BOBJArmature armature;
    public final List<EmoteMesh> meshes = new ArrayList<>();
    private final Map<String, EmoteMesh> byName = new LinkedHashMap<>();

    /** Model scale from upstream's {@code default_simple.json}. */
    public final float scale;

    /** Name of the bone that follows the player's head rotation. */
    public final String headBone;

    public EmoteModel(String name, BOBJLoader.BOBJData data, float scale, String headBone) {
        this.name = name;
        this.scale = scale;
        this.headBone = headBone;

        data.initiateArmatures();

        BOBJArmature armature = null;
        Map<String, BOBJLoader.CompiledData> compiled = BOBJLoader.loadMeshes(data);

        for (Map.Entry<String, BOBJLoader.CompiledData> entry : compiled.entrySet()) {
            String meshName = entry.getKey();
            EmoteMesh mesh = new EmoteMesh(meshName, entry.getValue(), BODY.equals(meshName));

            this.meshes.add(mesh);
            this.byName.put(meshName, mesh);

            if (armature == null) {
                armature = mesh.getArmature();
            }
        }

        if (armature == null) {
            throw new IllegalStateException("emote model '" + name + "' has no meshes");
        }

        this.armature = armature;
        this.armature.initArmature();

        /* Every sub-mesh carries its own armature instance from its own BOBJ object; point them all
         * at the one we pose so a single skinning pass drives the whole model. */
        for (EmoteMesh mesh : this.meshes) {
            mesh.setCurrentArmature(this.armature);
        }

        data.dispose();
    }

    public EmoteMesh mesh(String name) {
        return this.byName.get(name);
    }

    public EmoteMesh body() {
        return this.byName.get(BODY);
    }
}
