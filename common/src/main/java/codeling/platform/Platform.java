package codeling.platform;

import com.mojang.authlib.GameProfile;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.11 {
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
//?} else {
/*import net.minecraft.world.level.GameRules;
*///?}

/**
 * What the common code asks of the mod loader (issue #186), one implementation per loader found with
 * {@link java.util.ServiceLoader} (MultiLoader-Template's Services pattern). Events go the other way: each loader's
 * entrypoint subscribes to its own events and calls the common hooks in {@link codeling.Codeling}.
 */
public interface Platform {
    Platform INSTANCE = Services.load(Platform.class);

    /**
     * Registers {@code value} under {@code id}. A supplier, not the value, so a loader with frozen registries
     * (NeoForge's DeferredRegister) can create it later; call {@code get()} only after registration.
     */
    <T> Supplier<T> register(Registry<? super T> registry, Identifier id, Supplier<T> value);

    //? if >=1.21.11 {
    Supplier<GameRule<Boolean>> booleanRule(Identifier id, GameRuleCategory category, boolean defaultValue);

    Supplier<GameRule<Integer>> intRule(Identifier id, GameRuleCategory category, int defaultValue, int min, int max);

    /** Vanilla has no enum game rules; the values are the constant names. */
    <E extends Enum<E>> Supplier<GameRule<E>> enumRule(Identifier id, GameRuleCategory category, E defaultValue);
    //?} else {
    /*// Before 1.21.11 a game rule is a GameRules key with a string name; Codeling uses the id as that name.
    Supplier<GameRules.Key<GameRules.BooleanValue>> booleanRule(Identifier id, GameRules.Category category, boolean defaultValue);

    Supplier<GameRules.Key<GameRules.IntegerValue>> intRule(Identifier id, GameRules.Category category, int defaultValue, int min, int max);

    /^* Vanilla has no enum game rules; the value serializes as the constant name, as on 26.x. ^/
    <E extends Enum<E>> Supplier<? extends GameRules.Key<?>> enumRule(Identifier id, GameRules.Category category, E defaultValue);
    *///?}

    /** A server player that is not connected, for robot actions that go through the normal player path. */
    ServerPlayer fakePlayer(ServerLevel level, GameProfile profile);

    boolean isFakePlayer(Player player);

    /** True when the player's client has Codeling and accepts this payload type. */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

    void send(ServerPlayer player, CustomPacketPayload payload);
}
