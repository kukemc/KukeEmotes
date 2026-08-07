package kuke.emotes.client;

import kuke.emotes.bobj.BOBJAction;
import kuke.emotes.client.effect.EmoteEffect;
import kuke.emotes.client.effect.EmoteEffects;
import kuke.emotes.client.play.EmoteActionConfig;
import kuke.emotes.client.play.EmotePlayback;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * One player's in-progress emote: the playback cursor plus whatever props and effects that
 * particular emote brings with it.
 *
 * <p>Corresponds to the client half of upstream's {@code Cosmetic} capability. Prop state lives
 * here rather than on a shared animator config (as upstream had it) so that two players emoting
 * simultaneously cannot overwrite each other's popcorn bucket.
 */
public final class EmoteSession {

    /**
     * Fade-out length, in ticks. Upstream used 5 so an emote could blend back into its idle
     * animation; this port hands the player straight back to the vanilla renderer, so a long fade
     * would just be five ticks of a frozen last frame. One tick keeps the hand-off crisp.
     */
    private static final float FADE_TICKS = 1F;

    public final EmoteDefinition definition;
    public final EmotePlayback playback;

    /** Variant suffix, e.g. {@code rock} for {@code rock_paper_scissors:rock}. */
    private final String variant;

    @Nullable
    private final EmoteEffect effect;

    /** Prop meshes of the model that this emote turns on (the popcorn bucket). */
    private final Set<String> visibleProps = new HashSet<>();

    /** An item held in the right hand for the duration (rock/paper/scissors). */
    private ItemStack prop = ItemStack.EMPTY;
    private float propScale;

    /** Ticks since the emote started — drives the sound and the non-looping auto-stop. */
    private int elapsed;
    private boolean soundStarted;

    /** Last animation tick the effect ran for, so a burst fires once per tick, not once per frame. */
    private int lastEffectTick = -1;

    private EmoteSession(EmoteDefinition definition, EmotePlayback playback, String variant,
            @Nullable EmoteEffect effect) {
        this.definition = definition;
        this.playback = playback;
        this.variant = variant;
        this.effect = effect;
    }

    /**
     * @param variant optional suffix chosen by whoever started the emote (the server, when it is
     *                driving), so every viewer sees the same random pick
     * @return null when the emote has no animation loaded (assets not ready, or unknown action)
     */
    @Nullable
    public static EmoteSession create(EmoteDefinition definition, String variant) {
        BOBJAction action = EmoteAssets.INSTANCE.action(definition.actionName());

        if (action == null) {
            return null;
        }

        EmoteActionConfig config = new EmoteActionConfig(definition.actionName());
        config.fade = FADE_TICKS;

        EmotePlayback playback = new EmotePlayback(action, config, definition.looping());
        playback.reset();

        EmoteSession session = new EmoteSession(definition, playback, variant,
            EmoteEffects.get(definition.key()));

        if (session.effect != null) {
            session.effect.start(session);
        }

        return session;
    }

    public String variant() {
        return this.variant;
    }

    public void tick() {
        this.elapsed++;
        this.playback.update();
    }

    public int elapsed() {
        return this.elapsed;
    }

    /**
     * Whether the emote's music should start now — true on the first tick and again each time a
     * looping emote wraps around, matching upstream's "play when the cursor is at zero".
     */
    public boolean shouldPlaySound(float lastTick, float currentTick) {
        if (this.definition.soundName() == null) {
            return false;
        }

        if (!this.soundStarted) {
            this.soundStarted = true;

            return true;
        }

        return this.definition.looping() && currentTick < lastTick;
    }

    /* Effects and props */

    /** Run the effect for this frame, at most once per animation tick. */
    public void runEffect(kuke.emotes.client.effect.EmoteEffectContext context, int animationTick) {
        if (this.effect == null || animationTick == this.lastEffectTick) {
            return;
        }

        this.lastEffectTick = animationTick;
        this.effect.progress(context, animationTick);
    }

    public void showProp(String meshName) {
        this.visibleProps.add(meshName);
    }

    public boolean isPropVisible(String meshName) {
        return this.visibleProps.contains(meshName);
    }

    public void setProp(ItemStack stack) {
        this.prop = stack;
    }

    public ItemStack prop() {
        return this.prop;
    }

    public void setPropScale(float scale) {
        this.propScale = scale;
    }

    public float propScale() {
        return this.propScale;
    }

    /* Lifecycle */

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
