package codeling.fabric.client;

import codeling.Codeling;
import codeling.client.CodelingClient;
import codeling.client.RobotCameraClient;
import codeling.client.RobotRenderer;
import codeling.client.RobotScreen;
import codeling.net.RelayPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
//?} else
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;*/
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;*/
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
//?} else
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/
import net.minecraft.client.gui.screens.MenuScreens;
//? if >=26.1 {
import net.minecraft.client.renderer.entity.EntityRenderers;
//?} else
/*import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;*/

/** Fabric client entrypoint: registers the renderer, screen, keys and HUD and wires Fabric's client events. */
public final class CodelingFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        //? if >=26.1 {
        EntityRenderers.register(Codeling.ROBOT.get(), RobotRenderer::new);
        //?} else
        /*EntityRendererRegistry.register(Codeling.ROBOT.get(), RobotRenderer::new);*/
        MenuScreens.register(Codeling.ROBOT_MENU.get(), RobotScreen::new);
        //? if >=26.1 {
        KeyMappingHelper.registerKeyMapping(RobotCameraClient.KEY);
        KeyMappingHelper.registerKeyMapping(RobotCameraClient.FOLLOW_KEY);
        //?} else {
        /*KeyBindingHelper.registerKeyBinding(RobotCameraClient.KEY);
        KeyBindingHelper.registerKeyBinding(RobotCameraClient.FOLLOW_KEY);
        *///?}
        ClientPlayNetworking.registerGlobalReceiver(RelayPayloads.Camera.TYPE, (payload, context) -> RobotCameraClient.receive(context.client(), payload));
        ClientTickEvents.START_CLIENT_TICK.register(RobotCameraClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CodelingClient::tick);
        // 1.21.1 has no level extraction; GameRendererMixin hides the hand there.
        //? if >=26.1 {
        LevelExtractionEvents.END_EXTRACTION.register(context -> RobotCameraClient.extracted(context.levelState()));
        HudElementRegistry.addLast(RobotCameraClient.HUD, RobotCameraClient::hud);
        //?} else
        /*HudRenderCallback.EVENT.register(RobotCameraClient::hud);*/
        ClientLifecycleEvents.CLIENT_STARTED.register(CodelingClient::started);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CodelingClient.stopping());
        // Runs on the render thread.
        ClientPlayNetworking.registerGlobalReceiver(RelayPayloads.ToClient.TYPE, (payload, context) -> CodelingClient.receive(payload.json()));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> CodelingClient.disconnected());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                //? if >=26.1 {
                dispatcher.register(ClientCommands.literal("codeling").executes(ctx -> CodelingClient.openEditor())));
                //?} else
                /*dispatcher.register(ClientCommandManager.literal("codeling").executes(ctx -> CodelingClient.openEditor())));*/
    }
}
