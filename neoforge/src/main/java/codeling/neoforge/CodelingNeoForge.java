package codeling.neoforge;

import codeling.Codeling;
import codeling.net.RelayPayloads;
import codeling.net.ServerRelay;
import codeling.robot.RobotCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//? if <26.1
/*import net.neoforged.neoforge.network.handling.IPayloadHandler;*/

/** NeoForge entrypoint: registers the payloads and wires NeoForge's events to the common hooks in {@link Codeling}. */
@Mod(Codeling.MOD_ID)
public final class CodelingNeoForge {
    /** Payload protocol version; NeoForge compares it between client and server. */
    private static final String NETWORK_VERSION = "1";

    //? if <26.1 {
    /*// Before RegisterClientPayloadHandlersEvent the client handlers are given at registration, here; the client
    // entrypoint (constructed before the event) sets these, so this class never names client classes.
    public static IPayloadHandler<RelayPayloads.ToClient> toClient = (payload, context) -> { };
    public static IPayloadHandler<RelayPayloads.Camera> camera = (payload, context) -> { };
    *///?}

    public CodelingNeoForge(IEventBus modBus) {
        Codeling.init(); // creates the DeferredRegisters on modBus (NeoForgePlatform)
        modBus.addListener(CodelingNeoForge::registerPayloads);
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener((ServerTickEvent.Post event) -> Codeling.serverTick(event.getServer()));
        game.addListener((ServerStoppedEvent event) -> Codeling.serverStopped());
        game.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) Codeling.playerLeft(player);
        });
        // Lowest priority and not when cancelled: only an entity that really joins the level counts as loaded.
        game.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) Codeling.entityLoaded(event.getEntity(), level);
        });
        game.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel) Codeling.entityUnloaded(event.getEntity());
        });
        // Issue #27: fired on the server thread, not when another mod cancelled the line; cancelled keeps it for its sender's program.
        game.addListener((ServerChatEvent event) -> {
            if (Codeling.chat(event.getPlayer(), event.getRawText())) event.setCanceled(true);
        });
        // Lowest priority and not when cancelled: only a death that happens counts.
        game.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> Codeling.died(event.getEntity()));
        game.addListener((RegisterCommandsEvent event) -> RobotCommands.register(event.getDispatcher()));
    }

    // Optional, as on Fabric: a client without Codeling may join, and canSend tells the common code whether it has it.
    // The client's handlers for ToClient and Camera are registered in CodelingNeoForgeClient.
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();
        // Handlers run on the server thread (NeoForge's default HandlerThread.MAIN), as Fabric's receiver does.
        registrar.playToServer(RelayPayloads.ToServer.TYPE, RelayPayloads.ToServer.CODEC,
                (payload, context) -> ServerRelay.receive((ServerPlayer) context.player(), payload.json()));
        //? if >=26.1 {
        registrar.playToClient(RelayPayloads.ToClient.TYPE, RelayPayloads.ToClient.CODEC);
        registrar.playToClient(RelayPayloads.Camera.TYPE, RelayPayloads.Camera.CODEC);
        //?} else {
        /*registrar.playToClient(RelayPayloads.ToClient.TYPE, RelayPayloads.ToClient.CODEC, (payload, context) -> toClient.handle(payload, context));
        registrar.playToClient(RelayPayloads.Camera.TYPE, RelayPayloads.Camera.CODEC, (payload, context) -> camera.handle(payload, context));
        *///?}
    }
}
