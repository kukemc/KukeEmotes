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

    private volatile EmoteModel wide;
    private volatile EmoteModel slim;
    private volatile Map<String, BOBJAction> actions = Collections.emptyMap();
    private volatile boolean ready;

    private EmoteAssets() {
    }

    public boolean isReady() {
        return this.ready;
    }

    public EmoteModel model(PlayerSkin.Model skinModel) {
        return skinModel == PlayerSkin.Model.SLIM ? this.slim : this.wide;
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
            loaded.wide = readModel(manager, "default_simple", "wide");
            loaded.slim = readModel(manager, "slim_simple", "slim");
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
        if (loaded.failed || loaded.wide == null || loaded.slim == null) {
            this.ready = false;

            return;
        }

        this.wide = loaded.wide;
        this.slim = loaded.slim;
        this.actions = loaded.actions;
        this.ready = true;
    }

    /**
     * A body model plus the prop meshes merged onto its armature — that is where the armour pieces
     * and the popcorn bucket come from; they are authored in a separate file but skinned to the
     * same skeleton.
     */
    private static EmoteModel readModel(ResourceManager manager, String bodyFile, String name) throws Exception {
        BOBJLoader.BOBJData data = read(manager, bodyFile);

        BOBJLoader.merge(data, read(manager, "props_simple"));

        return new EmoteModel(name, data, MODEL_SCALE, HEAD_BONE);
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
        final Map<String, BOBJAction> actions = new HashMap<>();
        BOBJLoader.BOBJData ragdollArmatureOwner;
        boolean failed;
    }
}
