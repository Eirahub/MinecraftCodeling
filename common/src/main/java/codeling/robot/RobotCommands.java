package codeling.robot;

import codeling.McCompat;
import codeling.Codeling;
import codeling.api.Signals;
import codeling.net.PlayerEvents;
import codeling.net.RelayPayloads;
import codeling.platform.Platform;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /robot} server commands. The robot actions use the same validator and queue as the
 * network commands of step 2; until the relay is wired they are the manual test hook.
 * Issue #140: every command for one robot takes an optional robot name at the end; without it the command is for the
 * player's default robot, as before. Only the player's own robots are ever named, so no one controls another's robot.
 */
public final class RobotCommands {
    private static final String[] DIRECTIONS = {"forward", "back", "left", "right", "up", "down"}; // SixDirection order
    private static final String ROBOT = "robot";
    private static final SuggestionProvider<CommandSourceStack> MY_ROBOTS = (c, builder) -> c.getSource().getPlayer() == null
            ? builder.buildFuture()
            : SharedSuggestionProvider.suggest(RobotLinks.get(c.getSource().getServer()).names(c.getSource().getPlayer().getUUID()), builder);

    private RobotCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> move = Commands.literal("move");
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int direction = i;
            move.then(Commands.literal(DIRECTIONS[i]).then(named(Commands.argument("blocks", IntegerArgumentType.integer()),
                    c -> send(c, "agent.move", List.of(direction, IntegerArgumentType.getInteger(c, "blocks"))))));
        }
        LiteralArgumentBuilder<CommandSourceStack> place = Commands.literal("place");
        LiteralArgumentBuilder<CommandSourceStack> destroy = Commands.literal("destroy");
        LiteralArgumentBuilder<CommandSourceStack> inspect = Commands.literal("inspect");
        LiteralArgumentBuilder<CommandSourceStack> detect = Commands.literal("detect");
        LiteralArgumentBuilder<CommandSourceStack> drop = Commands.literal("drop");
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int d = i;
            place.then(named(Commands.literal(DIRECTIONS[i]), c -> send(c, "agent.place", List.of(d))));
            destroy.then(named(Commands.literal(DIRECTIONS[i]), c -> send(c, "agent.destroy", List.of(d))));
            inspect.then(named(Commands.literal(DIRECTIONS[i]), c -> send(c, "agent.inspect", List.of(RobotCommand.KIND_BLOCK, d))));
            detect.then(named(Commands.literal(DIRECTIONS[i]), c -> send(c, "agent.detect", List.of(RobotCommand.KIND_BLOCK, d))));
            drop.then(named(Commands.literal(DIRECTIONS[i]), c -> send(c, "agent.dropAll", List.of(d))));
        }
        // Issue #27: a chat line for the caller's own program, for servers whose chat plugins take chat lines away. No
        // operator rights needed: it reaches nobody but the caller's program. /codeling alone is the client's command.
        // Issue #249: a signal for the programs of the players named, for operators, command blocks and map makers.
        dispatcher.register(Commands.literal("codeling").then(Commands.literal("chat")
                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(RobotCommands::chat)))
                .then(Commands.literal("signal").requires(McCompat::isGamemaster).then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("name", StringArgumentType.string()).executes(c -> signal(c, 0))
                                .then(Commands.argument("number", DoubleArgumentType.doubleArg()).executes(c -> signal(c, DoubleArgumentType.getDouble(c, "number"))))))));
        // The slot comes before the robot name, so "give stone 5 3" is slot 3 (robot names start with a letter).
        Command<CommandSourceStack> giveToSlot = c -> give(c, IntegerArgumentType.getInteger(c, "slot"));
        dispatcher.register(Commands.literal("robot")
                .then(place).then(destroy).then(inspect).then(detect).then(drop)
                .then(named(Commands.literal("collect"), c -> send(c, "agent.collectAll", List.of())))
                .then(Commands.literal("slot").then(named(Commands.argument("slot", IntegerArgumentType.integer()),
                        c -> send(c, "agent.setSlot", List.of(IntegerArgumentType.getInteger(c, "slot"))))))
                .then(Commands.literal("give").then(Commands.argument("block", StringArgumentType.word())
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(c -> give(c, selectedSlot(c)))
                                .then(named(Commands.argument("slot", IntegerArgumentType.integer()), giveToSlot))
                                .then(robotName().executes(c -> give(c, selectedSlot(c)))))))
                .then(Commands.literal("assist").then(named(Commands.argument("on", BoolArgumentType.bool()),
                        c -> send(c, "agent.setAssist", List.of(RobotCommand.PLACE_ON_MOVE, BoolArgumentType.getBool(c, "on"))))))
                .then(Commands.literal("gravity").then(named(Commands.argument("on", BoolArgumentType.bool()),
                        c -> send(c, "agent.setGravity", List.of(BoolArgumentType.getBool(c, "on"))))))
                .then(named(Commands.literal("inventory"), RobotCommands::inventory))
                .then(named(Commands.literal("sethome"), c -> send(c, "agent.setHome", List.of())))
                .then(named(Commands.literal("come"), RobotCommands::come))
                .then(named(Commands.literal("camera"), c -> camera(c, false))
                        .then(named(Commands.literal("follow"), c -> camera(c, true)))
                        .then(Commands.literal("off").executes(c -> {
                            RobotCamera.stop(c.getSource().getPlayerOrException(), "/robot camera off (also sent by the camera key)");
                            return 1;
                        })))
                .then(named(Commands.literal("spawn"), RobotCommands::spawn))
                .then(named(Commands.literal("stop"), RobotCommands::stop)
                        //? if >=1.21.11 {
                        .then(Commands.literal("all").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        //?} else
                        /*.then(Commands.literal("all").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))*/
                                .executes(c -> {
                                    RobotManager.stopAll();
                                    c.getSource().sendSuccess(() -> Component.literal("Stopped all robots"), true);
                                    return 1;
                                })))
                .then(named(Commands.literal("status"), RobotCommands::status))
                .then(Commands.literal("list").executes(RobotCommands::list))
                .then(Commands.literal("remove").then(robotName().executes(RobotCommands::remove)))
                // rename <name>: the default robot's tag; rename <robot> <name>: a named robot's name.
                .then(Commands.literal("rename").then(robotName().executes(c -> rename(c, "", StringArgumentType.getString(c, ROBOT)))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(c -> rename(c, StringArgumentType.getString(c, ROBOT), StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("nametag")
                        .then(named(Commands.literal("on"), c -> nametag(c, true)))
                        .then(named(Commands.literal("off"), c -> nametag(c, false))))
                .then(Commands.literal("skin").then(named(Commands.argument("skin", StringArgumentType.word())
                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(RobotAppearance.SKINS, builder)), RobotCommands::skin)))
                .then(Commands.literal("color").then(named(Commands.argument("color", StringArgumentType.word()), RobotCommands::color)))
                .then(Commands.literal("animation").then(named(Commands.argument("animation", StringArgumentType.word())
                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(RobotAppearance.ANIMATIONS, builder)), RobotCommands::animation)))
                .then(move)
                .then(Commands.literal("turn")
                        .then(named(Commands.literal("left"), c -> send(c, "agent.turn", List.of(RobotCommand.LEFT_TURN))))
                        .then(named(Commands.literal("right"), c -> send(c, "agent.turn", List.of(RobotCommand.RIGHT_TURN))))));
    }

    private static int chat(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String text = StringArgumentType.getString(c, "text");
        if (PlayerEvents.chat(player, text)) return 1;
        c.getSource().sendFailure(Component.literal(PlayerEvents.listensForChat(player.getUUID())
                ? "Your program has no on chat command for \"" + text.strip().split("\\s+")[0] + "\"."
                : "No program of yours is listening for chat commands. Run a program with on chat command from the editor first."));
        return 0;
    }

    // Answers how many programs got it (the command's result, for command blocks) and names the players it did not
    // reach, so a map maker sees a typo at once.
    private static int signal(CommandContext<CommandSourceStack> c, double value) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "name");
        String problem = Signals.nameProblem(name);
        if (problem != null) {
            c.getSource().sendFailure(Component.literal(problem));
            return 0;
        }
        int reached = 0;
        List<String> missed = new ArrayList<>();
        for (ServerPlayer player : EntityArgument.getPlayers(c, "players")) {
            if (PlayerEvents.signal(player.getUUID(), name, value)) reached++;
            else missed.add(player.getGameProfile().name());
        }
        String others = missed.isEmpty() ? "" : " It did not reach " + String.join(", ", missed)
                + " (no running program listening for it, or " + RobotQueue.MAX_LENGTH + " events waiting).";
        if (reached == 0) {
            c.getSource().sendFailure(Component.literal("Signal \"" + name + "\" reached no program." + others));
            return 0;
        }
        int n = reached;
        c.getSource().sendSuccess(() -> Component.literal("Sent signal \"" + name + "\" to " + n + (n == 1 ? " program." : " programs.") + others), false);
        return reached;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> robotName() {
        return Commands.argument(ROBOT, StringArgumentType.word()).suggests(MY_ROBOTS);
    }

    /** The command, and the same command with a robot name after it. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T named(T node, Command<CommandSourceStack> command) {
        return node.executes(command).then(robotName().executes(command));
    }

    /** The robot name the command was given, or "" for the default robot. */
    private static String name(CommandContext<CommandSourceStack> c) {
        try {
            return StringArgumentType.getString(c, ROBOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static RobotLinks.Key key(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return new RobotLinks.Key(c.getSource().getPlayerOrException().getUUID(), name(c));
    }

    // "Robot" or "Robot Miner", for messages.
    private static String robot(String name) {
        return name.isEmpty() ? "Robot" : "Robot " + name;
    }

    private static int skin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RobotEntity robot = RobotManager.robot(key(c));
        if (robot == null) {
            c.getSource().sendFailure(Component.literal("Robot is not loaded. Use /robot spawn first."));
            return 0;
        }
        String skin = StringArgumentType.getString(c, "skin");
        if (!RobotAppearance.SKINS.contains(skin)) {
            c.getSource().sendFailure(Component.literal("Choose workshop, industrial or mint."));
            return 0;
        }
        robot.setSkin(skin);
        c.getSource().sendSuccess(() -> Component.literal("Robot skin: " + skin), false);
        return 1;
    }

    private static int color(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RobotEntity robot = RobotManager.robot(key(c));
        if (robot == null) {
            c.getSource().sendFailure(Component.literal("Robot is not loaded. Use /robot spawn first."));
            return 0;
        }
        try {
            robot.setPrimaryColor(RobotAppearance.color(StringArgumentType.getString(c, "color")));
        } catch (IllegalArgumentException exception) {
            c.getSource().sendFailure(Component.literal("Use six hex digits, for example 3366FF, or reset."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Robot panel color updated"), false);
        return 1;
    }

    private static int animation(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RobotEntity robot = RobotManager.robot(key(c));
        if (robot == null) {
            c.getSource().sendFailure(Component.literal("Robot is not loaded. Use /robot spawn first."));
            return 0;
        }
        String animation = StringArgumentType.getString(c, "animation");
        if (!RobotAppearance.ANIMATIONS.contains(animation)) {
            c.getSource().sendFailure(Component.literal("Choose " + String.join(", ", RobotAppearance.ANIMATIONS) + "."));
            return 0;
        }
        robot.animate(animation);
        c.getSource().sendSuccess(() -> Component.literal("Robot animation: " + animation), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String name = name(c);
        String refusal = RobotManager.spawnRefusal(c.getSource().getServer(), player.getUUID(), name);
        if (refusal != null) {
            c.getSource().sendFailure(Component.literal(refusal));
            return 0;
        }
        codeling.Codeling.LOG.info("spawn: {} typed /robot spawn{} while standing at x={} y={} z={} (block {}) in {}, looking {}.", player.getGameProfile().name(),
                name.isEmpty() ? "" : " " + name, String.format(java.util.Locale.ROOT, "%.2f", player.getX()), String.format(java.util.Locale.ROOT, "%.2f", player.getY()),
                String.format(java.util.Locale.ROOT, "%.2f", player.getZ()), player.blockPosition().toShortString().replace(",", ""), McCompat.dimension(McCompat.level(player)).getPath(),
                player.getDirection().getName());
        RobotManager.spawn(McCompat.level(player), key(c), Vec3.atBottomCenterOf(player.blockPosition()), player.getDirection());
        c.getSource().sendSuccess(() -> Component.literal(robot(name) + " spawned facing " + player.getDirection().getName()), false);
        return 1;
    }

    // Issue #77: walks to the player's block, or teleports there when there is no path; same dimension only.
    private static int come(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        RobotEntity robot = RobotManager.robot(key(c));
        if (robot != null && robot.level() != player.level()) {
            String spawn = name(c).isEmpty() ? "/robot spawn" : "/robot spawn " + name(c);
            c.getSource().sendFailure(Component.literal("Your robot is in another dimension. Use " + spawn + " to bring it here."));
            return 0;
        }
        BlockPos at = player.blockPosition();
        return send(c, "come", new RobotCommand(RobotCommand.Op.COME, at.getX(), at.getY(), at.getZ()));
    }

    // Issues #176 and #177: toggles the view through (or, with follow, behind) the player's robot; the other mode or
    // another robot switches over. The client's keys use /robot camera off.
    private static int camera(CommandContext<CommandSourceStack> c, boolean follow) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        RobotEntity robot = RobotManager.robot(key(c));
        if (robot != null && RobotCamera.viewing(player) == robot && RobotCamera.following(player) == follow) return RobotCamera.stop(player, "toggled off with /robot camera") ? 1 : 0;
        if (!Platform.INSTANCE.canSend(player, RelayPayloads.Camera.TYPE)) {
            c.getSource().sendFailure(Component.literal("The robot camera needs Codeling installed in your game."));
            return 0;
        }
        String refusal = RobotCamera.start(player, robot, follow);
        if (refusal != null) c.getSource().sendFailure(Component.literal(refusal));
        return refusal == null ? 1 : 0;
    }

    // Without a name every robot of the player stops (and the editor's run ends); with a name only that robot's queue.
    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID owner = c.getSource().getPlayerOrException().getUUID();
        String name = name(c);
        if (name.isEmpty()) {
            for (RobotQueue queue : RobotManager.queues(owner)) queue.clear(Result.Code.STOPPED, "stopped with /robot stop"); // the editor shows why
            RobotManager.stop(owner, "/robot stop");
            c.getSource().sendSuccess(() -> Component.literal("Robot stopped"), false);
            return 1;
        }
        if (RobotLinks.get(c.getSource().getServer()).robot(key(c)) == null) {
            c.getSource().sendFailure(Component.literal("You have no robot called " + name));
            return 0;
        }
        RobotManager.queue(key(c)).clear(Result.Code.STOPPED, "stopped with /robot stop " + name);
        c.getSource().sendSuccess(() -> Component.literal(robot(name) + " stopped"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RobotLinks.Key key = key(c);
        RobotEntity robot = RobotManager.robot(key);
        RobotQueue queue = RobotManager.queue(key);
        String spawn = key.name().isEmpty() ? "No robot. Use /robot spawn" : "You have no robot called " + key.name();
        String text = robot == null
                ? (RobotLinks.get(c.getSource().getServer()).robot(key) == null ? spawn : robot(key.name()) + " is not loaded")
                : robot(key.name()) + " at " + robot.blockPosition().toShortString() + " in " + McCompat.dimension(robot.level())
                        + ", facing " + robot.getDirection().getName() + ", queue " + queue.size() + ", run " + queue.run();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    // Issue #140: the player's robots, where they are, and how many the world allows.
    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        UUID owner = c.getSource().getPlayerOrException().getUUID();
        RobotLinks links = RobotLinks.get(c.getSource().getServer());
        List<String> names = new java.util.ArrayList<>(links.names(owner));
        if (links.robot(owner) != null) names.addFirst("");
        int max = Codeling.maxRobots(c.getSource().getServer());
        StringBuilder text = new StringBuilder(names.isEmpty() ? "No robot. Use /robot spawn" : "Your robots (" + names.size() + ", this world allows " + max + "):");
        for (String name : names) {
            RobotEntity robot = RobotManager.robot(new RobotLinks.Key(owner, name));
            text.append("\n").append(name.isEmpty() ? "default robot (agent)" : name).append(robot == null ? ": not loaded"
                    : ": " + robot.blockPosition().toShortString() + " in " + McCompat.dimension(robot.level()));
        }
        c.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = name(c);
        String refusal = RobotManager.remove(c.getSource().getPlayerOrException(), name);
        if (refusal != null) c.getSource().sendFailure(Component.literal(refusal));
        else c.getSource().sendSuccess(() -> Component.literal(robot(name) + " removed. Its items are in your inventory or at your feet."), false);
        return refusal == null ? 1 : 0;
    }

    private static int rename(CommandContext<CommandSourceStack> c, String from, String to) throws CommandSyntaxException {
        UUID owner = c.getSource().getPlayerOrException().getUUID();
        String refusal = RobotManager.rename(c.getSource().getServer(), new RobotLinks.Key(owner, from), to);
        if (refusal != null) c.getSource().sendFailure(Component.literal(refusal));
        else c.getSource().sendSuccess(() -> Component.literal(robot(from) + " is now called " + to), false);
        return refusal == null ? 1 : 0;
    }

    // Issue #151: shows or hides the name over the robot; without a name for all of the player's loaded robots.
    private static int nametag(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        UUID owner = c.getSource().getPlayerOrException().getUUID();
        List<RobotEntity> robots = name(c).isEmpty() ? RobotManager.robots(owner)
                : RobotManager.robot(key(c)) == null ? List.of() : List.of(RobotManager.robot(key(c)));
        if (robots.isEmpty()) {
            c.getSource().sendFailure(Component.literal(name(c).isEmpty() ? "No robot is loaded" : robot(name(c)) + " is not loaded"));
            return 0;
        }
        robots.forEach(r -> r.setCustomNameVisible(on));
        c.getSource().sendSuccess(() -> Component.literal("Name tag " + (on ? "on" : "off")), false);
        return 1;
    }

    private static int selectedSlot(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RobotEntity robot = RobotManager.robot(key(c));
        return robot == null ? 1 : robot.selectedSlot();
    }

    // <block> is one of our block constant names (stone, oak_planks, white_wool), an item id (iron_ingot) or a number.
    private static int give(CommandContext<CommandSourceStack> c, int slot) throws CommandSyntaxException {
        String block = StringArgumentType.getString(c, "block");
        int code = block.matches("-?\\d+") ? Integer.parseInt(block) : BlockIds.byName(block);
        if (code == BlockIds.UNKNOWN) code = ItemIds.byName(block);
        return send(c, "agent.setItem", List.of(code, IntegerArgumentType.getInteger(c, "count"), slot));
    }

    // Opens the inventory screen when the robot is within reach (issue #69), else prints the slots.
    private static int inventory(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        RobotEntity robot = RobotManager.robot(key(c));
        if (robot == null) {
            String spawn = name(c).isEmpty() ? "/robot spawn" : "/robot spawn " + name(c);
            c.getSource().sendFailure(Component.literal(robot(name(c)) + " is not loaded (or not spawned: " + spawn + ")"));
            return 0;
        }
        if (robot.level() == player.level() && McCompat.inReach(player, robot, 0) && RobotManager.openInventory(player, robot)) {
            return 1;
        }
        StringBuilder text = new StringBuilder("Selected slot " + robot.selectedSlot() + (robot.placeOnMove() ? ", place on move on" : ""));
        for (int i = 0; i < robot.inventory().getContainerSize(); i++) {
            ItemStack stack = robot.inventory().getItem(i);
            if (!stack.isEmpty()) text.append("\n").append(i + 1).append(": ").append(stack.getCount()).append(" ").append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        c.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static int send(CommandContext<CommandSourceStack> c, String op, List<?> args) throws CommandSyntaxException {
        try {
            return send(c, op, RobotCommand.parse(op, args));
        } catch (RobotException e) {
            c.getSource().sendFailure(Component.literal(op + ": " + e.result()));
            return 0;
        }
    }

    private static int send(CommandContext<CommandSourceStack> c, String op, RobotCommand command) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        RobotLinks.Key key = key(c);
        try {
            RobotManager.submit(key, RobotManager.queue(key).run(), command, result -> {
                if (result.ok()) source.sendSuccess(() -> Component.literal(op + ": " + result), false);
                else source.sendFailure(Component.literal(op + ": " + result));
            });
            return 1;
        } catch (RobotException e) {
            source.sendFailure(Component.literal(op + ": " + e.result()));
            return 0;
        }
    }
}
