package kuke.emotes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KukeEmotes — a client-only emote mod.
 *
 * <p>This is a port of <a href="https://github.com/mchorse/emoticons">McHorse's Emoticons</a>
 * (GPL-3.0) from Minecraft 1.12.2/Forge to 1.21.8/NeoForge. The skeletal animation format (BOBJ),
 * the armature/skinning maths and the 67 emote animations are the original author's work; see
 * {@code LICENSE.txt} and {@code README.md}.
 *
 * <p>Differences from upstream, by design:
 * <ul>
 *   <li>Client-only. There is no server-side mod half; multiplayer sync rides KukeUI's
 *       plugin-message bridge and is answered by the KukeCore Paper plugin. With neither present
 *       the mod still works — you just emote for your own eyes only.</li>
 *   <li>The player render is taken over <em>only while an emote plays</em>; the rest of the time
 *       the vanilla renderer (plus ParCool / KukeAnim) is untouched.</li>
 *   <li>Only the "simple" (blocky) models are shipped, to stay in the server's Minecraft-cube
 *       art style.</li>
 * </ul>
 */
@Mod(value = KukeEmotes.MOD_ID, dist = Dist.CLIENT)
public final class KukeEmotes {

    public static final String MOD_ID = "kukeemotes";
    public static final Logger LOGGER = LoggerFactory.getLogger("KukeEmotes");

    public KukeEmotes() {
        LOGGER.info("KukeEmotes loaded (port of McHorse's Emoticons, GPL-3.0)");
    }
}
