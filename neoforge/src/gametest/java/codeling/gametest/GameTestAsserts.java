package codeling.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * Minecraft 1.21.5 to 1.21.10 take the message of an assert only as a Component; 1.21.1 and 1.21.11 on also take a
 * String. On those versions stonecutter.gradle.kts turns {@code helper.assertTrue(...)} into
 * {@code GameTestAsserts.assertTrue(helper, ...)}, and the same for assertFalse and assertValueEqual. fail takes a
 * String from 1.21.9 (checked on 1.21.10), so before that {@code helper.fail(...)} goes through here too.
 */
final class GameTestAsserts {
    private GameTestAsserts() {
    }

    //? if >=1.21.5 && <1.21.11 {
    /*static void assertTrue(GameTestHelper h, boolean condition, String message) {
        h.assertTrue(condition, Component.literal(message));
    }

    static void assertFalse(GameTestHelper h, boolean condition, String message) {
        h.assertFalse(condition, Component.literal(message));
    }

    static <N> void assertValueEqual(GameTestHelper h, N actual, N expected, String message) {
        h.assertValueEqual(actual, expected, Component.literal(message));
    }
    *///?}

    //? if >=1.21.5 && <1.21.9 {
    /*static void fail(GameTestHelper h, String message) {
        h.fail(Component.literal(message));
    }
    *///?}

    //? if >=1.21.6 && <1.21.9 {
    /*// Issue #252: placeNewPlayer waits in ServerLevel.waitForChunkAndEntities until the entities of the chunks around
    // the player are loaded. A chunk only asks for its entities after a chunk task has made it visible, and the wait
    // runs chunk tasks only while the tick is on time (MinecraftServer.pollTaskInternal checks haveTime()). So in a
    // GameTest tick that runs late (a slow first batch, a busy machine) the wait never ends. Inside a server task
    // haveTime() is true (runningTask()), so the same wait runs the chunk tasks and ends.
    static net.minecraft.server.level.ServerPlayer makeMockServerPlayerInLevel(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var player = java.util.concurrent.CompletableFuture.supplyAsync(h::makeMockServerPlayerInLevel,
            task -> server.schedule(server.wrapRunnable(task)));
        server.managedBlock(player::isDone);
        return player.join();
    }
    *///?}
}
