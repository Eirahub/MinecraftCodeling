package codeling.neoforge;

import codeling.Codeling;
import codeling.platform.Platform;
import com.mojang.authlib.GameProfile;
//? if >=1.21.11 {
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
//?} else {
/*import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
*///?}
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.11 {
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.core.registries.BuiltInRegistries;
//?}
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.command.EnumArgument;

/**
 * NeoForge freezes its registries after loading, so everything goes through one {@link DeferredRegister} per registry,
 * registered on Codeling's mod event bus; the returned holders resolve once NeoForge has fired its RegisterEvent.
 */
public final class NeoForgePlatform implements Platform {
    //? if >=1.21.11 {
    /**
     * Vanilla has only INT and BOOL game rule types. NeoForge makes {@link GameRuleType} extensible, so
     * META-INF/enumextensions.json adds this constant; NeoForge's Edit Game Rules screen then shows a text field for it.
     */
    private static final String ENUM_RULE_TYPE = "CODELING_ENUM";
    //?}

    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> register(Registry<? super T> registry, Identifier id, Supplier<T> value) {
        if (!id.getNamespace().equals(Codeling.MOD_ID)) throw new IllegalArgumentException("Not a codeling id: " + id);
        DeferredRegister<Object> register = (DeferredRegister<Object>) registers.computeIfAbsent(registry.key(), key -> {
            DeferredRegister<?> created = DeferredRegister.create((ResourceKey<Registry<Object>>) key, Codeling.MOD_ID);
            created.register(ModList.get().getModContainerById(Codeling.MOD_ID).orElseThrow().getEventBus());
            return created;
        });
        return (Supplier<T>) register.register(id.getPath(), value);
    }

    //? if >=1.21.11 {
    // The three rules below take the arguments vanilla's GameRules.registerBoolean and registerInteger pass.
    @Override
    public Supplier<GameRule<Boolean>> booleanRule(Identifier id, GameRuleCategory category, boolean defaultValue) {
        return register(BuiltInRegistries.GAME_RULE, id, () -> new GameRule<>(category, GameRuleType.BOOL, BoolArgumentType.bool(),
                GameRuleTypeVisitor::visitBoolean, Codec.BOOL, b -> b ? 1 : 0, defaultValue, FeatureFlagSet.of()));
    }

    @Override
    public Supplier<GameRule<Integer>> intRule(Identifier id, GameRuleCategory category, int defaultValue, int min, int max) {
        return register(BuiltInRegistries.GAME_RULE, id, () -> new GameRule<>(category, GameRuleType.INT, IntegerArgumentType.integer(min, max),
                GameRuleTypeVisitor::visitInteger, Codec.intRange(min, max), i -> i, defaultValue, FeatureFlagSet.of()));
    }

    /**
     * Same values as Fabric's GameRuleBuilder.forEnum: saved as the constant name, /gamerule takes the name
     * (NeoForge's EnumArgument, which NeoForge clients know), and the command result is the ordinal.
     */
    @Override
    public <E extends Enum<E>> Supplier<GameRule<E>> enumRule(Identifier id, GameRuleCategory category, E defaultValue) {
        Class<E> type = defaultValue.getDeclaringClass();
        Codec<E> codec = Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Enum.valueOf(type, name));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown value " + name + " for game rule " + id);
            }
        }, Enum::name);
        // No vanilla visitor method fits, so /gamerule and the screen use the rule's argument and codec directly.
        return register(BuiltInRegistries.GAME_RULE, id, () -> new GameRule<>(category, GameRuleType.valueOf(ENUM_RULE_TYPE),
                EnumArgument.enumArgument(type), (visitor, rule) -> { }, codec, Enum::ordinal, defaultValue, FeatureFlagSet.of()));
    }
    //?} else {
    /*// Before 1.21.11 a game rule is a GameRules key, registered at once (vanilla's own rules are static fields). The
    // range and the enum type need members that META-INF/accesstransformer-gamerules.cfg opens.
    @Override
    public Supplier<GameRules.Key<GameRules.BooleanValue>> booleanRule(Identifier id, GameRules.Category category, boolean defaultValue) {
        GameRules.Key<GameRules.BooleanValue> key = GameRules.register(id.toString(), category, GameRules.BooleanValue.create(defaultValue));
        return () -> key;
    }

    @Override
    public Supplier<GameRules.Key<GameRules.IntegerValue>> intRule(Identifier id, GameRules.Category category, int defaultValue, int min, int max) {
        GameRules.Key<GameRules.IntegerValue> key = GameRules.register(id.toString(), category,
                //? if >=1.21.2 {
                GameRules.IntegerValue.create(defaultValue, min, max, net.minecraft.world.flag.FeatureFlagSet.of(), (server, value) -> { }));
                //?} else
                /^GameRules.IntegerValue.create(defaultValue, min, max, (server, value) -> { }));^/
        return () -> key;
    }

    /^*
     * Saved as the constant name and set by name with NeoForge's EnumArgument, as on 26.x. No visitor: vanilla's has no
     * method for it, so the Edit Game Rules screen leaves it out (on 26.x NeoForge shows a text field).
     ^/
    @Override
    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> Supplier<? extends GameRules.Key<?>> enumRule(Identifier id, GameRules.Category category, E defaultValue) {
        Class<E> type = defaultValue.getDeclaringClass();
        GameRules.Type<EnumValue<E>> ruleType = new GameRules.Type<EnumValue<E>>(() -> EnumArgument.enumArgument(type),
                //? if >=1.21.2 {
                t -> new EnumValue<E>(t, type, defaultValue), (server, value) -> { }, (visitor, k, t) -> { },
                (Class<EnumValue<E>>) (Class<?>) EnumValue.class, net.minecraft.world.flag.FeatureFlagSet.of());
                //?} else
                /^t -> new EnumValue<E>(t, type, defaultValue), (server, value) -> { }, (visitor, k, t) -> { });^/
        GameRules.Key<EnumValue<E>> key = GameRules.register(id.toString(), category, ruleType);
        return () -> key;
    }

    private static final class EnumValue<E extends Enum<E>> extends GameRules.Value<EnumValue<E>> {
        private final Class<E> enumType;
        private E value;

        EnumValue(GameRules.Type<EnumValue<E>> type, Class<E> enumType, E value) {
            super(type);
            this.enumType = enumType;
            this.value = value;
        }

        @Override
        protected void updateFromArgument(CommandContext<CommandSourceStack> context, String name) {
            value = context.getArgument(name, enumType);
        }

        @Override
        protected void deserialize(String name) {
            try {
                value = Enum.valueOf(enumType, name);
            } catch (IllegalArgumentException e) {
                Codeling.LOG.warn("Unknown value {} for an enum game rule, keeping {}", name, value);
            }
        }

        @Override
        public String serialize() {
            return value.name();
        }

        @Override
        public int getCommandResult() {
            return value.ordinal();
        }

        @Override
        protected EnumValue<E> getSelf() {
            return this;
        }

        @Override
        protected EnumValue<E> copy() {
            return new EnumValue<>(type, enumType, value);
        }

        @Override
        public void setFrom(EnumValue<E> other, MinecraftServer server) {
            value = other.value;
            onChanged(server);
        }
    }
    *///?}

    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        return FakePlayerFactory.get(level, profile);
    }

    @Override
    public boolean isFakePlayer(Player player) {
        return player instanceof FakePlayer;
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        // A FakePlayer has no client; on NeoForge 21.1 hasChannel would fail on its missing network channel.
        return !(player instanceof FakePlayer) && player.connection.hasChannel(type);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
