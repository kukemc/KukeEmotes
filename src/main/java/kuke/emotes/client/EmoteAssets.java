package kuke.emotes.client;

import kuke.emotes.KukeEmotes;
import kuke.emotes.bobj.BOBJAction;
import kuke.emotes.bobj.BOBJLoader;
import kuke.emotes.client.model.EmoteModel;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and holds the BOBJ models and the animation library.
 *
 * <p>Runs as a resource-reload listener so the ~3 MB {@code actions.bobj} is parsed on the reload
 * worker threads rather than blocking the render thread, and so F3+T picks up edits. Until the
 * first reload finishes {@link #isReady()} is false and the renderer simply declines to take over
 * the player — never a half-loaded model on screen.
 */
public final class EmoteAssets extends SimplePreparableReloadListener<EmoteAssets.Loaded> {

    public static final EmoteAssets INSTANCE = new EmoteAssets();

    private static final String BASE = "models/entity/";

    /** Upstream's {@code default_simple.json}: scale 0.9375, head bone "head". */
    private static final float MODEL_SCALE = 0.9375F;
    private static final String HEAD_BONE = "head";

    /**
     * Which body to draw. {@code true} = upstream's main model (2,532 vertices, genuinely deforming
     * limbs — what Emoticons looks like); {@code false} = the low-poly "simple" one (156 vertices,
     * Minecraft-cube silhouette, but a joint is one ring of vertices so bending it collapses the
     * limb into a sheet, which the stitching can only partly disguise).
     */
    public static volatile boolean smoothModel = true;

    private volatile EmoteModel wide;
    private volatile EmoteModel slim;
    private volatile EmoteModel wideBlocky;
    private volatile EmoteModel slimBlocky;
    private volatile Map<String, BOBJAction> actions = Collections.emptyMap();
    private volatile boolean ready;

    private EmoteAssets() {
    }

    public boolean isReady() {
        return this.ready;
    }

    public EmoteModel model(PlayerSkin.Model skinModel) {
        boolean slimSkin = skinModel == PlayerSkin.Model.SLIM;

        if (smoothModel) {
            return slimSkin ? this.slim : this.wide;
        }

        return slimSkin ? this.slimBlocky : this.wideBlocky;
    }

    public BOBJAction action(String name) {
        return this.actions.get(name);
    }

    /** Action names, for the emote registry to validate itself against. */
    public Map<String, BOBJAction> actions() {
        return this.actions;
    }

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        long start = System.currentTimeMillis();
        Loaded loaded = new Loaded();

        try {
            loaded.wide = readModel(manager, "default", "props", "wide", false);
            loaded.slim = readModel(manager, "slim", "props", "slim", false);
            loaded.wideBlocky = readModel(manager, "default_simple", "props_simple", "wide-blocky", true);
            loaded.slimBlocky = readModel(manager, "slim_simple", "props_simple", "slim-blocky", true);
            loaded.actions.putAll(read(manager, "actions").actions);

            /* The ragdoll emotes animate their own skeleton, shipped separately. */
            BOBJLoader.BOBJData ragdoll = read(manager, "ragdoll");
            loaded.actions.putAll(ragdoll.actions);
            loaded.ragdollArmatureOwner = ragdoll;

            KukeEmotes.LOGGER.info("Loaded {} emote actions in {} ms",
                loaded.actions.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            KukeEmotes.LOGGER.error("Failed to load emote assets; emotes will be unavailable", e);
            loaded.failed = true;
        }

        return loaded;
    }

    @Override
    protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
        if (loaded.failed || loaded.wide == null || loaded.slim == null
                || loaded.wideBlocky == null || loaded.slimBlocky == null) {
            this.ready = false;

            return;
        }

        this.wide = loaded.wide;
        this.slim = loaded.slim;
        this.wideBlocky = loaded.wideBlocky;
        this.slimBlocky = loaded.slimBlocky;
        this.actions = loaded.actions;
        this.ready = true;

        EmoteRegistry.validateAgainstAssets();
    }

    /**
     * A body model plus the prop meshes merged onto its armature — that is where the armour pieces
     * and the popcorn bucket come from; they are authored in a separate file but skinned to the
     * same skeleton.
     */
    private static EmoteModel readModel(ResourceManager manager, String bodyFile, String propsFile, String name,
            boolean blockyJoints) throws Exception {
        BOBJLoader.BOBJData data = read(manager, bodyFile);

        BOBJLoader.merge(data, read(manager, propsFile));

        return new EmoteModel(name, data, MODEL_SCALE, HEAD_BONE, blockyJoints);
    }

    private static BOBJLoader.BOBJData read(ResourceManager manager, String file) throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(KukeEmotes.MOD_ID, BASE + file + ".bobj");

        try (InputStream stream = manager.getResourceOrThrow(id).open()) {
            return BOBJLoader.readData(stream);
        }
    }

    /** Parse output handed from the worker thread to the render thread. */
    public static final class Loaded {

        EmoteModel wide;
        EmoteModel slim;
        EmoteModel wideBlocky;
        EmoteModel slimBlocky;
        final Map<String, BOBJAction> actions = new HashMap<>();
        BOBJLoader.BOBJData ragdollArmatureOwner;
        boolean failed;
    }
}
