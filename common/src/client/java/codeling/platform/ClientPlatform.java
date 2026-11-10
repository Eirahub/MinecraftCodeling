package codeling.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What the common client code asks of the mod loader; see {@link Platform}. */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Services.load(ClientPlatform.class);

    /** True when the server accepts this payload type. May throw IllegalStateException when not in a world. */
    boolean canSend(CustomPacketPayload.Type<?> type);

    void send(CustomPacketPayload payload);
}
