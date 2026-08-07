package kuke.emotes.client;

import kuke.emotes.KukeEmotes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Optional music for the dance emotes.
 *
 * <p><b>No audio ships with this mod, on purpose.</b> Upstream Emoticons declares 16 music tracks
 * in its {@code sounds.json}, but every {@code .ogg} in the public repository is the same 4 KB
 * placeholder (identical checksums) — the real tracks are licensed pop music and were never
 * committed. Shipping the placeholders would only add a second of silence per emote and 16 "Missing
 * sound for event" warnings.
 *
 * <p>So this class <em>looks up</em> sound events rather than registering them: if something else
 * on the client registers {@code kukeemotes:<track>} (a music add-on, or a future asset drop), the
 * dance emotes start playing it with no code change. Until then {@link #get} returns null and the
 * emotes are silent.
 */
public final class EmoteSounds {

    /** Upstream's playback volume. */
    public static final float VOLUME = 0.33F;

    private EmoteSounds() {
    }

    /** @return null when no sound event with that name is registered (the normal case) */
    public static SoundEvent get(String name) {
        if (name == null) {
            return null;
        }

        return BuiltInRegistries.SOUND_EVENT.getValue(
            ResourceLocation.fromNamespaceAndPath(KukeEmotes.MOD_ID, name));
    }
}
