package codeling.neoforge.client;

import codeling.Codeling;
import codeling.client.CodelingClient;
import codeling.client.RobotCameraClient;
import codeling.client.RobotRenderer;
import codeling.client.RobotScreen;
import codeling.net.RelayPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//? if >=26.3
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
//? if >=26.1 {
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
//?} else {
/*import codeling.neoforge.CodelingNeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
*///?}
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge client entrypoint: registers the renderer, screen, keys and HUD and wires NeoForge's client events. */
@Mod(value = Codeling.MOD_ID, dist = Dist.CLIENT)
public final class CodelingNeoForgeClient {
    //? if <26.1
    /*private static boolean started;*/

    public CodelingNeoForgeClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(Codeling.ROBOT.get(), RobotRenderer::new));
        modBus.addListener((RegisterMenuScreensEvent event) -> event.register(Codeling.ROBOT_MENU.get(), RobotScreen::new));
        // RobotCameraClient's KeyMapping.Category.register already adds the Codeling category to vanilla's sort order
        // (deprecated on NeoForge, but it works); registerCategory as well would list it twice.
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(RobotCameraClient.KEY);
            event.register(RobotCameraClient.FOLLOW_KEY);
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(RobotCameraClient.HUD, RobotCameraClient::hud));
        // Handlers run on the render thread (NeoForge's default HandlerThread.MAIN), as Fabric's receivers do.
        //? if >=26.1 {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            event.register(RelayPayloads.Camera.TYPE, (payload, context) -> RobotCameraClient.receive(Minecraft.getInstance(), payload));
            event.register(RelayPayloads.ToClient.TYPE, (payload, context) -> CodelingClient.receive(payload.json()));
        });
        //?} else {
        /*CodelingNeoForge.camera = (payload, context) -> RobotCameraClient.receive(Minecraft.getInstance(), payload);
        CodelingNeoForge.toClient = (payload, context) -> CodelingClient.receive(payload.json());
        *///?}

        IEventBus game = NeoForge.EVENT_BUS;
        //? if >=26.1 {
        game.addListener((ClientTickEvent.Pre event) -> RobotCameraClient.tick(Minecraft.getInstance()));
        //? if >=26.3
        game.addListener((ExtractLevelRenderStateEvent event) -> RobotCameraClient.extracted(event.getRenderState()));
        game.addListener((ClientStartedEvent event) -> CodelingClient.started(event.getClient()));
        game.addListener((ClientStoppingEvent event) -> CodelingClient.stopping());
        //?} else {
        /*// NeoForge 21.1 has no client started event: the first client tick comes right after start-up. The hand is
        // hidden by GameRendererMixin there (no level extraction).
        game.addListener((ClientTickEvent.Pre event) -> {
            if (!started) {
                started = true;
                CodelingClient.started(Minecraft.getInstance());
            }
            RobotCameraClient.tick(Minecraft.getInstance());
        });
        game.addListener((GameShuttingDownEvent event) -> CodelingClient.stopping());
        *///?}
        game.addListener((ClientTickEvent.Post event) -> CodelingClient.tick(Minecraft.getInstance()));
        game.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> CodelingClient.disconnected());
        game.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(Commands.literal("codeling").executes(context -> CodelingClient.openEditor())));
    }
}
