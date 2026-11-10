package codeling.neoforge.client;

import codeling.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if >=26.1 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?} else
/*import net.neoforged.neoforge.network.PacketDistributor;*/

public final class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public boolean canSend(CustomPacketPayload.Type<?> type) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) throw new IllegalStateException("Not in a world");
        return connection.hasChannel(type);
    }

    @Override
    public void send(CustomPacketPayload payload) {
        //? if >=26.1 {
        ClientPacketDistributor.sendToServer(payload);
        //?} else
        /*PacketDistributor.sendToServer(payload);*/
    }
}
