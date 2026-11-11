package codeling.net;

import codeling.Codeling;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One play payload type per direction, each carrying one protocol v1 JSON message (architecture section 3).
 * The string codec caps a message at {@link Protocol#MAX_MESSAGE_BYTES} characters; the relay never sends more
 * because its WebSocket frames are capped at the same number of bytes. Each loader registers the types and codecs.
 */
public final class RelayPayloads {
    private static final StreamCodec<ByteBuf, String> JSON = ByteBufCodecs.stringUtf8(Protocol.MAX_MESSAGE_BYTES);

    private RelayPayloads() {
    }

    /** Browser to server, sent by the client relay. */
    public record ToServer(String json) implements CustomPacketPayload {
        public static final Type<ToServer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Codeling.MOD_ID, "to_server"));
        public static final StreamCodec<ByteBuf, ToServer> CODEC = JSON.map(ToServer::new, ToServer::json);

        @Override
        public Type<ToServer> type() {
            return TYPE;
        }
    }

    /** Server to browser, forwarded by the client relay to the WebSocket. */
    public record ToClient(String json) implements CustomPacketPayload {
        public static final Type<ToClient> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Codeling.MOD_ID, "to_client"));
        public static final StreamCodec<ByteBuf, ToClient> CODEC = JSON.map(ToClient::new, ToClient::json);

        @Override
        public Type<ToClient> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: look through this robot (entity id), or {@link codeling.robot.RobotCamera#NONE} to stop;
     * {@code follow} puts the camera behind the robot instead of in its eye.
     */
    public record Camera(int robot, boolean follow) implements CustomPacketPayload {
        public static final Type<Camera> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Codeling.MOD_ID, "camera"));
        public static final StreamCodec<ByteBuf, Camera> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Camera::robot, ByteBufCodecs.BOOL, Camera::follow, Camera::new);

        @Override
        public Type<Camera> type() {
            return TYPE;
        }
    }
}
