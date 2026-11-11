package codeling.fabric;

import codeling.Codeling;
import codeling.net.RelayPayloads;
import codeling.net.ServerRelay;
import codeling.robot.RobotCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Fabric entrypoint: registers the payloads and wires Fabric's events to the common hooks in {@link Codeling}. */
public final class CodelingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Codeling.init();
        //? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(RelayPayloads.ToServer.TYPE, RelayPayloads.ToServer.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RelayPayloads.ToClient.TYPE, RelayPayloads.ToClient.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RelayPayloads.Camera.TYPE, RelayPayloads.Camera.CODEC);
        //?} else {
        /*PayloadTypeRegistry.playC2S().register(RelayPayloads.ToServer.TYPE, RelayPayloads.ToServer.CODEC);
        PayloadTypeRegistry.playS2C().register(RelayPayloads.ToClient.TYPE, RelayPayloads.ToClient.CODEC);
        PayloadTypeRegistry.playS2C().register(RelayPayloads.Camera.TYPE, RelayPayloads.Camera.CODEC);
        *///?}
        // Runs on the server thread (Fabric Javadoc).
        ServerPlayNetworking.registerGlobalReceiver(RelayPayloads.ToServer.TYPE, (payload, context) -> ServerRelay.receive(context.player(), payload.json()));
        ServerEntityEvents.ENTITY_LOAD.register(Codeling::entityLoaded);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Codeling.entityUnloaded(entity));
        ServerTickEvents.END_SERVER_TICK.register(Codeling::serverTick);
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> Codeling.playerLeft(listener.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Codeling.serverStopped());
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> RobotCommands.register(dispatcher));
    }
}
