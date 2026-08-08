package kuke.emotes.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kuke.emotes.KukeEmotes;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The handful of client preferences, in {@code config/kukeemotes.json}.
 *
 * <p>Hand-rolled rather than NeoForge's config system: there are two booleans, and a plain JSON file
 * the player can edit is friendlier than a TOML spec for something this small.
 */
public final class EmoteConfig {

    private static final String FILE = "kukeemotes.json";

    /** Swing the camera to third person while emoting, and back afterwards. */
    public static volatile boolean autoThirdPerson = true;

    private EmoteConfig() {
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    public static void load() {
        Path path = path();

        if (!Files.exists(path)) {
            save();

            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();

            if (root.has("smoothModel")) {
                EmoteAssets.smoothModel = root.get("smoothModel").getAsBoolean();
            }

            if (root.has("autoThirdPerson")) {
                autoThirdPerson = root.get("autoThirdPerson").getAsBoolean();
            }
        } catch (Exception e) {
            KukeEmotes.LOGGER.warn("Could not read {}; using defaults", FILE, e);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();

        root.addProperty("_comment", "smoothModel: true = upstream's deforming model, "
            + "false = the low-poly blocky one");
        root.addProperty("smoothModel", EmoteAssets.smoothModel);
        root.addProperty("autoThirdPerson", autoThirdPerson);

        try {
            Files.writeString(path(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KukeEmotes.LOGGER.warn("Could not write {}", FILE, e);
        }
    }
}
