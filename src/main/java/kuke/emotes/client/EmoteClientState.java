package kuke.emotes.client;

import kuke.emotes.KukeEmotes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Who is emoting, and how far along they are.
 *
 * <p>Client-authoritative when playing alone or on a server without the KukeCore half; when the
 * server does answer, it owns starting and stopping and this class just follows what it is told
 * (see {@code kuke.emotes.net}). The local movement check below therefore stays enabled either way
 * — it is what makes the emote feel responsive (you stop the instant you walk, not a round trip
 * later), and a redundant stop is harmless.
 *
 * <p>Render-thread only.
 */
public final class EmoteClientState {

    /** Upstream's threshold: any real movement cancels the emote. */
    private static final double MOVE_EPSILON = 0.015D;

    private static final Map<UUID, EmoteSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POSITIONS = new HashMap<>();

    /** Set when the server is driving emotes, so local rules stop competing with it. */
    private static boolean serverAuthoritative;

    private EmoteClientState() {
    }

    public static void setServerAuthoritative(boolean authoritative) {
        serverAuthoritative = authoritative;
    }

    public static boolean isServerAuthoritative() {
        return serverAuthoritative;
    }

    /**
     * Start (or restart) an emote for a player.
     *
     * @return false if the emote is unknown or its animation is not loaded
     */
    public static boolean start(UUID player, String emoteKey) {
        EmoteDefinition definition = EmoteRegistry.get(emoteKey);

        if (definition == null) {
            KukeEmotes.LOGGER.debug("Ignoring unknown emote '{}'", emoteKey);

            return false;
        }

        EmoteSession session = EmoteSession.create(definition);

        if (session == null) {
            return false;
        }

        SESSIONS.put(player, session);
        LAST_POSITIONS.remove(player);

        return true;
    }

    /** Stop immediately, without the fade-out (used when the player is gone). */
    public static void clear(UUID player) {
        SESSIONS.remove(player);
        LAST_POSITIONS.remove(player);
    }

    /** Ask the emote to fade out; it disappears a few ticks later. */
    public static void stop(UUID player) {
        EmoteSession session = SESSIONS.get(player);

        if (session != null) {
            session.requestStop();
        }
    }

    @Nullable
    public static EmoteSession get(UUID player) {
        return SESSIONS.get(player);
    }

    public static boolean isEmoting(UUID player) {
        return SESSIONS.containsKey(player);
    }

    public static boolean isEmpty() {
        return SESSIONS.isEmpty();
    }

    public static void clearAll() {
        SESSIONS.clear();
        LAST_POSITIONS.clear();
    }

    /** Advance every playback; drop finished ones; apply the local interrupt rules. */
    public static void tick(Minecraft minecraft) {
        if (SESSIONS.isEmpty() || minecraft.level == null) {
            return;
        }

        Iterator<Map.Entry<UUID, EmoteSession>> it = SESSIONS.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, EmoteSession> entry = it.next();
            UUID uuid = entry.getKey();
            EmoteSession session = entry.getValue();
            Player player = minecraft.level.getPlayerByUUID(uuid);

            if (player == null || player.isRemoved()) {
                it.remove();
                LAST_POSITIONS.remove(uuid);

                continue;
            }

            session.tick();

            if (session.isFinished()) {
                it.remove();
                LAST_POSITIONS.remove(uuid);

                continue;
            }

            if (shouldInterrupt(uuid, player, session)) {
                session.requestStop();
            }
        }
    }

    private static boolean shouldInterrupt(UUID uuid, Player player, EmoteSession session) {
        if (session.isExpired()) {
            return true;
        }

        Vec3 position = player.position();
        Vec3 last = LAST_POSITIONS.put(uuid, position);

        if (last == null) {
            return false;
        }

        /* Upstream sums the signed per-axis deltas rather than taking a distance; keep that so the
         * client and the KukeCore half agree on when an emote breaks. */
        double delta = Math.abs(position.x - last.x + (position.y - last.y) + (position.z - last.z));

        return delta > MOVE_EPSILON;
    }
}
