package kuke.emotes.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kuke.emotes.KukeEmotes;
import kuke.emotes.client.EmoteClientState;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Multiplayer sync, routed through the KukeUI mod's plugin-message bridge.
 *
 * <p>Same shape as ParCool's {@code ParCoolBridge}: KukeUI owns the {@code kukeui:main} channel and
 * frames everything as {@code [namespace][action][body]}; this class only supplies namespace
 * {@code emotes}, an action and a UTF-8 JSON body. The bridge class is resolved <b>reflectively,
 * once</b> — there is no compile dependency on KukeUI, and if it is absent everything no-ops after
 * one warning and emotes stay local (which is exactly the single-player experience).
 *
 * <p>The peer is the KukeCore Paper plugin, which owns the authoritative rules: who may emote,
 * when an emote breaks, and who gets told about it.
 */
public final class EmotesBridge {

    public static final String NAMESPACE = "emotes";

    /** Bumped when the wire format changes incompatibly. */
    public static final int PROTOCOL = 1;

    private static final String BRIDGE_CLASS = "kuke.kukeui.client.net.UiPayloadBridge";

    /* C2S */
    public static final String HELLO = "hello";
    public static final String PLAY = "play";
    public static final String STOP = "stop";

    /* S2C */
    public static final String HELLO_ACK = "hello_ack";
    public static final String STATE = "state";
    public static final String STATE_BULK = "state_bulk";

    private static volatile boolean resolved;
    private static Method sendMethod;       // sendNamespaceAction(String, String, byte[]) -> boolean
    private static Method registerMethod;   // registerNamespaceHandler(String, BiConsumer<String,byte[]>)
    private static Method availableMethod;  // isBridgeAvailable() -> boolean

    /** Whether we have already said hello on this connection. */
    private static boolean greeted;

    private EmotesBridge() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }

        resolved = true;

        try {
            Class<?> clazz = Class.forName(BRIDGE_CLASS);

            sendMethod = clazz.getMethod("sendNamespaceAction", String.class, String.class, byte[].class);
            registerMethod = clazz.getMethod("registerNamespaceHandler", String.class, BiConsumer.class);
            availableMethod = clazz.getMethod("isBridgeAvailable");

            KukeEmotes.LOGGER.info("Resolved KukeUI bridge; emotes will sync over namespace '{}'", NAMESPACE);
        } catch (Throwable t) {
            sendMethod = null;
            registerMethod = null;
            availableMethod = null;

            KukeEmotes.LOGGER.info("KukeUI bridge not found ({}); emotes stay client-local", BRIDGE_CLASS);
        }
    }

    /** Register the S2C handler once, at client setup. KukeUI keeps the namespace map across sessions. */
    public static void init() {
        resolve();

        if (registerMethod == null) {
            return;
        }

        try {
            BiConsumer<String, byte[]> handler = EmotesBridge::onReceive;
            registerMethod.invoke(null, NAMESPACE, handler);
        } catch (Throwable t) {
            KukeEmotes.LOGGER.warn("Failed to register the emote receive handler", t);
        }
    }

    public static boolean isAvailable() {
        resolve();

        if (availableMethod == null) {
            return false;
        }

        try {
            return availableMethod.invoke(null) instanceof Boolean available && available;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void resetSession() {
        greeted = false;
    }

    /**
     * Say hello once the bridge comes up, so the server knows this client can show emotes and can
     * compare protocol versions. Called every tick; cheap and idempotent.
     */
    public static void tick() {
        if (greeted || !isAvailable()) {
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("protocol", PROTOCOL);
        body.addProperty("modVersion", "1.0.0");

        if (send(HELLO, body)) {
            greeted = true;
        }
    }

    /** Ask the server to start an emote. Returns false when there is no server half to ask. */
    public static boolean requestPlay(String emoteKey) {
        JsonObject body = new JsonObject();
        body.addProperty("key", emoteKey);

        return send(PLAY, body);
    }

    public static boolean requestStop() {
        return send(STOP, new JsonObject());
    }

    private static boolean send(String action, JsonObject body) {
        resolve();

        if (sendMethod == null) {
            return false;
        }

        try {
            Object result = sendMethod.invoke(null, NAMESPACE, action,
                body.toString().getBytes(StandardCharsets.UTF_8));

            return result instanceof Boolean sent && sent;
        } catch (Throwable t) {
            KukeEmotes.LOGGER.warn("Failed to send emote action '{}'", action, t);

            return false;
        }
    }

    private static void onReceive(String action, byte[] payload) {
        Minecraft minecraft = Minecraft.getInstance();

        /* KukeUI dispatches on the network thread; every state change below touches client state. */
        minecraft.execute(() -> {
            try {
                handle(action, payload);
            } catch (Throwable t) {
                KukeEmotes.LOGGER.warn("Failed to handle emote action '{}'", action, t);
            }
        });
    }

    private static void handle(String action, byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }

        JsonObject body = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();

        switch (action) {
            case HELLO_ACK -> {
                EmoteClientState.setServerAuthoritative(true);
                KukeEmotes.LOGGER.info("Emote sync active (server protocol {})",
                    body.has("protocol") ? body.get("protocol").getAsInt() : -1);
            }
            case STATE -> applyState(body);
            case STATE_BULK -> {
                if (body.has("entries")) {
                    body.getAsJsonArray("entries").forEach(entry -> applyState(entry.getAsJsonObject()));
                }
            }
            default -> {
                /* Unknown actions are ignored on purpose: the namespace may be shared. */
            }
        }
    }

    private static void applyState(JsonObject entry) {
        if (!entry.has("uuid")) {
            return;
        }

        UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
        String key = entry.has("key") ? entry.get("key").getAsString() : "";

        if (key.isEmpty()) {
            EmoteClientState.stop(uuid);
        } else {
            EmoteClientState.start(uuid, key);
        }
    }
}
