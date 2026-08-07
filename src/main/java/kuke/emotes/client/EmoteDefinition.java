package kuke.emotes.client;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * One entry of the emote registry: how long it runs, whether it loops, and what it sounds like.
 *
 * @param key        registry key, e.g. {@code wave}; the BOBJ action is {@code emote_<key>}
 * @param duration   ticks before the emote stops itself (ignored while {@code looping})
 * @param looping    whether it repeats until interrupted
 * @param soundName  sound event path under this mod's namespace, or null for a silent emote
 */
public record EmoteDefinition(String key, int duration, boolean looping, @Nullable String soundName) {

    /** BOBJ action name for this emote. */
    public String actionName() {
        return "emote_" + this.key;
    }

    public String titleKey() {
        return "kukeemotes.emote." + this.key + ".title";
    }

    public String descriptionKey() {
        return "kukeemotes.emote." + this.key + ".desc";
    }

    /**
     * Localised name, falling back to the raw key when the translation is missing — several emotes
     * added late upstream have no zh_CN entry, and a blank wheel slot is worse than an English word.
     */
    public Component title() {
        Component translated = Component.translatable(this.titleKey());

        return translated.getString().equals(this.titleKey()) ? Component.literal(this.key) : translated;
    }

    public Component description() {
        Component translated = Component.translatable(this.descriptionKey());

        return translated.getString().equals(this.descriptionKey()) ? Component.empty() : translated;
    }
}
