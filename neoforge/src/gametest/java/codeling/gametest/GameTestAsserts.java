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
}
