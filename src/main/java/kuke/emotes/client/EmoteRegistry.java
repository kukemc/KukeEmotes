package kuke.emotes.client;

import kuke.emotes.KukeEmotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shipped emote catalogue.
 *
 * <p>The table is upstream's ({@code mchorse.emoticons.common.emotes.Emotes}, GPL-3.0), preserved
 * entry for entry including the durations — those are hand-tuned per emote and are <em>not</em>
 * derivable from the BOBJ action length, so they must be copied rather than computed.
 *
 * <p>Order matters: it is the order the wheel shows, grouped the way upstream grouped them.
 */
public final class EmoteRegistry {

    private static final Map<String, EmoteDefinition> EMOTES = new LinkedHashMap<>();
    private static final List<EmoteDefinition> ORDERED = new ArrayList<>();

    static {
        /* Dance emotes — these have music */
        register("best_mates", 11, true, "best_mates");
        register("boneless", 40, true, "boneless");
        register("default", 139, true, "default");
        register("disco_fever", 175, true, "disco_fever");
        register("electro_shuffle", 169, true, "electro_shuffle");
        register("floss", 32, true, "floss");
        register("fresh", 101, true, "fresh");
        register("gangnam_style", 18, true, "gangnam_style");
        register("hype", 68, true, "hype");
        register("infinite_dab", 19, true, "infinite_dab");
        register("orange_justice", 130, true, "orange_justice");
        register("skibidi", 16, true, "skibidi");
        register("squat_kick", 232, true, "squat_kick");
        register("star_power", 160, true, "star_power");
        register("take_the_l", 16, true, "take_the_l");
        register("tidy", 104, true, "tidy");
        register("free_flow", 158, true, "free_flow");
        register("shimmer", 156, true, "shimmer");
        register("get_funky", 172, true, "get_funky");

        /* Just emotes */
        register("boy", 29, false, null);
        register("bow", 43, false, null);
        register("calculated", 33, false, null);
        register("chicken", 19, true, null);
        register("clapping", 15, true, null);
        register("club", 20, true, null);
        register("confused", 140, false, null);
        register("crying", 27, true, null);
        register("dab", 23, false, null);
        register("facepalm", 104, false, null);
        register("fist", 53, false, null);
        register("laughing", 15, true, null);
        register("no", 30, false, null);
        register("pointing", 33, false, null);
        register("popcorn", 102, true, null);
        register("pure_salt", 104, false, null);
        register("rock_paper_scissors", 60, false, null);
        register("salute", 50, false, null);
        register("shrug", 50, false, null);
        register("t_pose", 80, true, null);
        register("thinking", 100, true, null);
        register("twerk", 14, true, null);
        register("wave", 40, false, null);
        register("yes", 23, false, null);

        /* Emotes 2020 — authored at 30 fps, hence the conversion */
        register("bitchslap", toTicks(100), false, null);
        register("bongo_cat", toTicks(238), false, null);
        register("breathtaking", toTicks(154), false, null);
        register("disgusted", toTicks(200), false, null);
        register("exhausted", toTicks(330), true, null);
        register("punch", toTicks(58), false, null);
        register("sneeze", toTicks(200), false, null);
        register("threatening", toTicks(70), false, null);
        register("woah", toTicks(66), false, null);

        register("stick_bug", toTicks(25), true, null);
        register("am_stuff", toTicks(80), false, null);
        register("slow_clap", toTicks(200), false, null);
        register("hell_yeah", toTicks(70), false, null);
        register("paranoid", toTicks(315), false, null);
        register("scared", toTicks(50), true, null);

        register("tada", toTicks(90), false, null);
        register("smug_dance", toTicks(29), true, null);
        register("nope", toTicks(101), false, null);
    }

    private EmoteRegistry() {
    }

    /** Upstream's 30 fps → 20 tick conversion. */
    private static int toTicks(int frames30) {
        return (int) Math.floor(frames30 / 30F * 20F);
    }

    private static void register(String key, int duration, boolean looping, String soundName) {
        EmoteDefinition definition = new EmoteDefinition(key, duration, looping, soundName);

        EMOTES.put(key, definition);
        ORDERED.add(definition);
    }

    public static EmoteDefinition get(String key) {
        return key == null ? null : EMOTES.get(key);
    }

    public static boolean has(String key) {
        return key != null && EMOTES.containsKey(key);
    }

    public static List<EmoteDefinition> all() {
        return Collections.unmodifiableList(ORDERED);
    }

    public static int size() {
        return ORDERED.size();
    }

    /**
     * Warn about registry entries with no animation behind them. Called once the assets finish
     * loading: a missing action means the emote silently does nothing when picked, which is
     * otherwise only discoverable by trying all 67.
     */
    public static void validateAgainstAssets() {
        List<String> missing = new ArrayList<>();

        for (EmoteDefinition definition : ORDERED) {
            if (EmoteAssets.INSTANCE.action(definition.actionName()) == null) {
                missing.add(definition.key());
            }
        }

        if (!missing.isEmpty()) {
            KukeEmotes.LOGGER.warn("{} registered emotes have no BOBJ action and will not play: {}",
                missing.size(), missing);
        }
    }
}
