package kuke.emotes.client;

import kuke.emotes.net.EmotesBridge;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Random;

/**
 * The single place a player's intent to emote goes through — the wheel, the keybinds and the
 * command all call here.
 *
 * <p>Playback starts locally straight away and the server is told in parallel, rather than waiting
 * for a round trip: an emote that lags a tenth of a second behind the button feels broken, and the
 * server can still stop it a moment later if it disagrees. Other players' emotes are never started
 * here — those only ever arrive from the server.
 */
public final class EmoteController {

    private static final Random RANDOM = new Random();

    /**
     * Camera to go back to when the emote ends. An emote you cannot see is a wasted emote, so
     * first-person players are swung round to third-person for the duration and put back after —
     * the same thing every game with an emote wheel does.
     */
    private static CameraType cameraToRestore;

    private EmoteController() {
    }

    /**
     * @param emoteKey registry key, optionally with a variant suffix
     * @return false if the emote is unknown or its animation has not loaded
     */
    public static boolean play(String emoteKey) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return false;
        }

        String resolved = withVariant(emoteKey);

        if (!EmoteRegistry.isUnlocked(resolved)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("kukeemotes.ui.locked"), true);

            return false;
        }

        if (!EmoteClientState.start(player.getUUID(), resolved)) {
            return false;
        }

        EmotesBridge.requestPlay(resolved);

        Minecraft minecraft = Minecraft.getInstance();

        if (cameraToRestore == null && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            cameraToRestore = CameraType.FIRST_PERSON;
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        return true;
    }

    public static void stop() {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        EmoteClientState.stop(player.getUUID());
        EmotesBridge.requestStop();
    }

    /** Put the camera back once the local player's emote is over. Called every client tick. */
    public static void tick() {
        if (cameraToRestore == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            cameraToRestore = null;

            return;
        }

        if (!EmoteClientState.isEmoting(player.getUUID())) {
            minecraft.options.setCameraType(cameraToRestore);
            cameraToRestore = null;
        }
    }

    public static boolean playRandom() {
        var all = EmoteRegistry.all();

        return !all.isEmpty() && play(all.get(RANDOM.nextInt(all.size())).key());
    }

    /**
     * Pick the variant for emotes that have one, here on the initiating client, so the choice can
     * travel with the key and every viewer sees the same thing.
     */
    private static String withVariant(String emoteKey) {
        if (emoteKey.indexOf(':') >= 0 || !"rock_paper_scissors".equals(emoteKey)) {
            return emoteKey;
        }

        return emoteKey + ":" + switch (RANDOM.nextInt(3)) {
            case 0 -> "rock";
            case 1 -> "paper";
            default -> "scissors";
        };
    }
}
