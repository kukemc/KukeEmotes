package kuke.emotes.client.play;

import kuke.emotes.bobj.BOBJAction;
import kuke.emotes.bobj.BOBJArmature;
import kuke.emotes.bobj.BOBJBone;
import kuke.emotes.bobj.BOBJGroup;

/**
 * Plays one BOBJ action: advances a tick cursor, loops or fades out at the end, and stamps the
 * resulting pose onto an armature.
 *
 * <p>Ported from Emoticons' {@code ActionPlayback} (GPL-3.0), unchanged in behaviour.
 */
public class EmotePlayback {

    public final BOBJAction action;
    public EmoteActionConfig config;

    private int fade;
    private float ticks;
    private final int duration;
    private float speed = 1;

    private final boolean looping;
    private boolean fading;
    public boolean playing = true;

    /** Non-null for the ragdoll emotes, which animate a different skeleton than the body's. */
    public BOBJArmature customArmature;

    public EmotePlayback(BOBJAction action, EmoteActionConfig config, boolean looping) {
        this.action = action;
        this.config = config;
        this.duration = action.getDuration();
        this.looping = looping;
        this.setSpeed(1);
    }

    public int getDuration() {
        return this.duration;
    }

    public boolean isLooping() {
        return this.looping;
    }

    /** Reset the cursor (when the config allows it) and clear any fade. */
    public void reset() {
        if (this.config.reset) {
            this.ticks = Math.copySign(1, this.speed) < 0 ? this.duration : 0;
        }

        this.unfade();
    }

    public boolean finishedFading() {
        return this.fading && this.fade <= 0;
    }

    public boolean isFading() {
        return this.fading && this.fade > 0;
    }

    public void fade() {
        this.fade = (int) this.config.fade;
        this.fading = true;
    }

    public void unfade() {
        this.fade = 0;
        this.fading = false;
    }

    /**
     * Fade weight for the given partial tick: closer to 1 right after the fade started, closer to 0
     * as it finishes.
     */
    public float getFadeFactor(float partialTicks) {
        return (this.fade - partialTicks) / this.config.fade;
    }

    public void setSpeed(float speed) {
        this.speed = speed * this.config.speed;
    }

    public void update() {
        if (this.fading && this.fade > 0) {
            this.fade--;

            return;
        }

        if (!this.playing) {
            return;
        }

        this.ticks += this.speed;

        if (!this.looping && !this.fading && this.ticks >= this.duration) {
            this.fade();
        }

        if (this.looping) {
            if (this.ticks >= this.duration && this.speed > 0 && this.config.clamp) {
                this.ticks -= this.duration;
                this.ticks += this.config.tick;
            } else if (this.ticks < 0 && this.speed < 0 && this.config.clamp) {
                this.ticks = this.duration + this.ticks;
                this.ticks -= this.config.tick;
            }
        }
    }

    public float getTick(float partialTick) {
        float ticks = this.ticks + partialTick * this.speed;

        if (this.looping) {
            if (ticks >= this.duration && this.speed > 0 && this.config.clamp) {
                ticks -= this.duration;
            } else if (this.ticks < 0 && this.speed < 0 && this.config.clamp) {
                ticks = this.duration + ticks;
            }
        }

        return ticks;
    }

    /** Stamp the pose at the current tick onto the armature. */
    public void apply(BOBJArmature armature, float partialTick) {
        for (BOBJGroup group : this.action.groups.values()) {
            BOBJBone bone = armature.bones.get(group.name);

            if (bone != null) {
                group.apply(bone, this.getTick(partialTick));
            }
        }
    }

    /** Blend the pose in with weight {@code x} on whatever the bones already hold. */
    public void applyInactive(BOBJArmature armature, float partialTick, float x) {
        for (BOBJGroup group : this.action.groups.values()) {
            BOBJBone bone = armature.bones.get(group.name);

            if (bone != null) {
                group.applyInterpolate(bone, this.ticks, x);
            }
        }
    }
}
