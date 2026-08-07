package kuke.emotes.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import kuke.emotes.client.EmoteController;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Key bindings: hold to open the wheel, and a stop key.
 *
 * <p>The wheel is <em>held</em>, not toggled, so the screen itself watches for the release (see
 * {@link EmoteWheelScreen#keyReleased}) — while a screen is open the game stops updating
 * {@code KeyMapping.isDown()}, so polling from the tick loop would never see the key come back up.
 */
public final class KukeEmoteKeys {

    private static final String CATEGORY = "key.categories.kukeemotes";

    public static final KeyMapping WHEEL = new KeyMapping(
        "key.kukeemotes.wheel", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
        InputConstants.KEY_B, CATEGORY);

    public static final KeyMapping STOP = new KeyMapping(
        "key.kukeemotes.stop", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.getValue(), CATEGORY);

    private KukeEmoteKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(WHEEL);
        event.register(STOP);
    }

    /** Called every client tick while no screen is open. */
    public static void tick(Minecraft minecraft) {
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }

        if (WHEEL.isDown()) {
            minecraft.setScreen(new EmoteWheelScreen());
        }

        while (STOP.consumeClick()) {
            EmoteController.stop();
        }
    }
}
