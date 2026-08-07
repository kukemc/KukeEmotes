package kuke.emotes.client.play;

/**
 * Playback settings for one action — speed, fade-out length, looping behaviour.
 *
 * <p>Ported from Emoticons' {@code ActionConfig} (GPL-3.0) with the NBT (de)serialisation dropped:
 * upstream persisted these per morph, and this port has no morphs. Defaults are upstream's.
 */
public class EmoteActionConfig {

    public String name = "";
    public boolean clamp = true;
    public boolean reset = true;
    public float speed = 1;
    public float fade = 5;
    public int tick = 0;

    public EmoteActionConfig() {
    }

    public EmoteActionConfig(String name) {
        this.name = name;
    }

    public EmoteActionConfig copy() {
        EmoteActionConfig config = new EmoteActionConfig(this.name);

        config.clamp = this.clamp;
        config.reset = this.reset;
        config.speed = this.speed;
        config.fade = this.fade;
        config.tick = this.tick;

        return config;
    }
}
