package kuke.emotes.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import kuke.emotes.KukeEmotes;
import kuke.emotes.client.render.EmoteRenderer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/** Client wiring: asset reloads, the per-tick playback pump, and the render takeover. */
public final class KukeEmotesClient {

    private static final ResourceLocation ASSETS_LISTENER =
        ResourceLocation.fromNamespaceAndPath(KukeEmotes.MOD_ID, "assets");

    private KukeEmotesClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(KukeEmotesClient::onAddReloadListeners);

        NeoForge.EVENT_BUS.addListener(KukeEmotesClient::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(KukeEmotesClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(KukeEmotesClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(KukeEmotesClient::onRegisterClientCommands);
    }

    private static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(ASSETS_LISTENER, EmoteAssets.INSTANCE);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.isPaused()) {
            return;
        }

        EmoteClientState.tick(minecraft);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EmoteClientState.clearAll();
        EmoteClientState.setServerAuthoritative(false);
    }

    /**
     * Swap the vanilla player render for the emote model. Cancelling here replaces the whole
     * vanilla render — model, armour layers, held items and all — which is why the takeover only
     * lasts as long as the emote does.
     */
    private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (EmoteClientState.isEmpty()) {
            return;
        }

        UUID uuid = resolveUuid(event.getRenderState().id);

        if (uuid == null) {
            return;
        }

        EmoteSession session = EmoteClientState.get(uuid);

        if (session == null) {
            return;
        }

        boolean drawn = EmoteRenderer.render(event.getRenderState(), session, event.getPoseStack(),
            event.getMultiBufferSource(), event.getPackedLight());

        if (drawn) {
            event.setCanceled(true);
        }
    }

    /** The render state carries the entity id, not the UUID the emote protocol is keyed by. */
    private static UUID resolveUuid(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return null;
        }

        Entity entity = minecraft.level.getEntity(entityId);

        return entity instanceof Player ? entity.getUUID() : null;
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        SuggestionProvider<CommandSourceStack> emoteKeys = (context, builder) ->
            SharedSuggestionProvider.suggest(EmoteRegistry.all().stream().map(EmoteDefinition::key), builder);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("emote")
            .then(Commands.literal("stop").executes(context -> {
                Player player = Minecraft.getInstance().player;

                if (player != null) {
                    EmoteClientState.stop(player.getUUID());
                }

                return 1;
            }))
            .then(Commands.literal("list").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal(
                    "KukeEmotes: " + EmoteRegistry.size() + " emotes, assets "
                        + (EmoteAssets.INSTANCE.isReady() ? "ready" : "NOT loaded")), false);

                return 1;
            }))
            /* Camera control exists so the automated visual lab can see the model at all — it can
             * send client commands but cannot press F5. */
            .then(Commands.literal("view")
                .then(Commands.argument("mode", StringArgumentType.word()).executes(context -> {
                    String mode = StringArgumentType.getString(context, "mode");
                    CameraType type = switch (mode) {
                        case "third", "back" -> CameraType.THIRD_PERSON_BACK;
                        case "front" -> CameraType.THIRD_PERSON_FRONT;
                        default -> CameraType.FIRST_PERSON;
                    };

                    Minecraft.getInstance().options.setCameraType(type);

                    return 1;
                })))
            .then(Commands.argument("emote", StringArgumentType.word())
                .suggests(emoteKeys)
                .executes(context -> {
                    String key = StringArgumentType.getString(context, "emote");
                    Player player = Minecraft.getInstance().player;

                    if (player == null) {
                        return 0;
                    }

                    if (!EmoteClientState.start(player.getUUID(), key)) {
                        context.getSource().sendFailure(Component.literal(
                            "Unknown emote or animation not loaded: " + key));

                        return 0;
                    }

                    return 1;
                }));

        event.getDispatcher().register(root);
    }
}
