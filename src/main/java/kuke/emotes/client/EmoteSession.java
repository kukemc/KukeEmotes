package kuke.emotes.client;

import kuke.emotes.bobj.BOBJAction;
import kuke.emotes.client.play.EmoteActionConfig;
import kuke.emotes.client.play.EmotePlayback;

/**
 * One player's in-progress emote.
 *
 * <p>Corresponds to the client half of upstream's {@code Cosmetic} capability, minus the
 * server-side bookkeeping: whether the emote should end is decided by the server (or, offline, by
 * {@link EmoteClientState}), and this class only owns the playback cursor.
 */
public final class EmoteSession {

    public final EmoteDefinition definition;
    public final EmotePlayback playback;

    /** Ticks since the emote started — drives the sound and the non-looping auto-stop. */
    private int elapsed;
    private boolean soundStarted;

    private EmoteSession(EmoteDefinition definition, EmotePlayback playback) {
        this.definition = definition;
        this.playback = playback;
    }

    /** @return null when the emote has no animation loaded (assets not ready, or unknown action) */
    public static EmoteSession create(EmoteDefinition definition) {
        BOBJAction action = EmoteAssets.INSTANCE.action(definition.actionName());

        if (action == null) {
            return null;
        }

        EmoteActionConfig config = new EmoteActionConfig(definition.actionName());
        EmotePlayback playback = new EmotePlayback(action, config, definition.looping());

        playback.reset();

        return new EmoteSession(definition, playback);
    }

    public void tick() {
        this.elapsed++;
        this.playback.update();
    }

    public int elapsed() {
        return this.elapsed;
    }

    public boolean shouldPlaySound() {
        if (this.soundStarted || this.definition.soundName() == null) {
            return false;
        }

        this.soundStarted = true;

        return true;
    }

    /** Begin the fade-out; {@link #isFinished()} turns true once it completes. */
    public void requestStop() {
        if (!this.playback.isFading()) {
            this.playback.fade();
        }
    }

    public boolean isFinished() {
        return this.playback.finishedFading();
    }

    /**
     * Whether this emote has outstayed its declared duration. Looping emotes never do — they run
     * until something interrupts them.
     */
    public boolean isExpired() {
        return !this.definition.looping() && this.elapsed >= this.definition.duration();
    }
}
