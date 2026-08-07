package kuke.emotes.client.effect;

import kuke.emotes.client.EmoteSession;

/**
 * Extra behaviour a handful of emotes have beyond their animation: particles, a prop that appears
 * in the hand, a mesh that becomes visible.
 *
 * <p>Corresponds to upstream's {@code Emote} subclasses (GPL-3.0). Two differences:
 *
 * <ul>
 *   <li>State lives on the {@link EmoteSession}, not on a shared animator, so two players emoting
 *       at once cannot clobber each other's props — upstream's model config was global.</li>
 *   <li>{@link #progress} is called at most once per game tick. Upstream called it from the render
 *       path with an {@code ==} tick test, so on a 60 fps client every burst fired three times.</li>
 * </ul>
 */
public interface EmoteEffect {

    /** Called once when the emote starts, before the first frame. */
    default void start(EmoteSession session) {
    }

    /**
     * Called at most once per animation tick, from the render path (the skeleton has to be posed
     * for bone positions to mean anything).
     */
    default void progress(EmoteEffectContext context, int tick) {
    }
}
