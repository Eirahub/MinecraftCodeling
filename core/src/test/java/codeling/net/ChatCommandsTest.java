package codeling.net;

import codeling.robot.BlockIds;
import codeling.robot.Result;
import codeling.robot.RobotException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Issue #27: which keyword a chat line triggers, its numbers as MakeCode parses them, and the subscribe limits.
class ChatCommandsTest {
    @Test
    void firstWordMatchesAKeywordElseStar() {
        Set<String> jump = Set.of("jump", "build");
        assertEquals("jump", ChatCommands.match(jump, "jump 3"));
        assertEquals("build", ChatCommands.match(jump, "  build   1 2 "));
        assertNull(ChatCommands.match(jump, "hello jump"), "only the first word counts");
        assertNull(ChatCommands.match(jump, "Jump 3"), "case matters");
        assertNull(ChatCommands.match(jump, "jumping"));
        assertNull(ChatCommands.match(jump, "   "));
        assertEquals("*", ChatCommands.match(Set.of("jump", "*"), "hello there"));
        assertEquals("jump", ChatCommands.match(Set.of("jump", "*"), "jump"), "a keyword before the star");
        assertNull(ChatCommands.match(Set.of(), "jump"));
    }

    @Test
    void numbersAreParsedAsParseInt() {
        assertArrayEquals(new double[] {3, 0, 0}, ChatCommands.numbers("jump 3"));
        assertArrayEquals(new double[] {0, 0, 0}, ChatCommands.numbers("jump"));
        assertArrayEquals(new double[] {-5, 12, 3}, ChatCommands.numbers("jump -5 12abc 3.9 7"), "at most three");
        assertArrayEquals(new double[] {0, 16, 0}, ChatCommands.numbers("jump abc 0x10 -"));
        assertArrayEquals(new double[] {5, 0, 255}, ChatCommands.numbers("go\t+5  -0 0XfF"));
        assertArrayEquals(new double[] {0, 0, 0}, ChatCommands.numbers("go ٣ 0x x5"), "only ASCII digits");
        assertEquals(1e20, ChatCommands.numbers("go 100000000000000000000")[0]);
        assertEquals(0, ChatCommands.parseInt("9".repeat(400)), "never infinite");
    }

    @Test
    void keywordRules() {
        for (String ok : new String[] {"jump", "*", "hopp", "løp", "build-2", "a_b", "7", "a".repeat(ChatCommands.MAX_KEYWORD_LENGTH)}) {
            assertNull(ChatCommands.keywordProblem(ok), ok);
        }
        for (String bad : new String[] {"", "two words", "/tp", "jump!", "**", "a".repeat(ChatCommands.MAX_KEYWORD_LENGTH + 1), "§c"}) {
            assertNotNull(ChatCommands.keywordProblem(bad), bad);
        }
    }

    private static Protocol.Subscription subscribe(String fields) throws RobotException {
        return Protocol.subscription(Protocol.parse("{\"v\":2,\"t\":\"control\",\"op\":\"subscribe\",\"run\":1" + fields + "}"));
    }

    private static Result.Code refused(String fields) {
        return assertThrows(RobotException.class, () -> subscribe(fields)).code;
    }

    @Test
    void subscribeIsChecked() throws RobotException {
        assertEquals(new Protocol.Subscription(Set.of("jump", "*"), true, Set.of(), false, false, Set.of()), subscribe(",\"chat\":[\"jump\",\"*\",\"jump\"],\"died\":true"));
        assertEquals(new Protocol.Subscription(Set.of(), false, Set.of(), false, false, Set.of()), subscribe(""));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i <= ChatCommands.MAX_KEYWORDS; i++) many.append(i == 0 ? "" : ",").append("\"k").append(i).append('"');
        assertEquals(Result.Code.BAD_ARGS, refused(",\"chat\":[" + many + "]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"chat\":[\"two words\"]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"chat\":\"jump\""));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"chat\":[3]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"died\":1"));
    }

    // Issue #89: known blocks (not air) up to the cap, and two flags; an unknown field is ignored as before.
    @Test
    void subscribeToBlocksRedstoneAndDayStart() throws RobotException {
        int stone = BlockIds.byName("STONE"), log = BlockIds.byName("OAK_LOG");
        assertEquals(new Protocol.Subscription(Set.of(), false, Set.of(stone, log), true, true, Set.of()),
                subscribe(",\"broken\":[" + stone + "," + log + "," + stone + "],\"redstone\":true,\"dayStart\":true,\"later\":[1]"));
        StringBuilder many = new StringBuilder();
        List<String> names = List.of("STONE", "GRANITE", "DIORITE", "ANDESITE", "DIRT", "COBBLESTONE", "SAND", "GRAVEL", "GOLD_ORE",
                "IRON_ORE", "COAL_ORE", "GLASS", "TNT", "OBSIDIAN", "TORCH", "CHEST", "ICE");
        for (String name : names) many.append(many.isEmpty() ? "" : ",").append(BlockIds.byName(name));
        assertEquals(Protocol.MAX_BROKEN_BLOCKS + 1, names.size());
        assertEquals(Result.Code.BAD_ARGS, refused(",\"broken\":[" + many + "]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"broken\":[0]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"broken\":[999999]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"broken\":[1.5]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"broken\":[\"stone\"]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"broken\":1"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"redstone\":\"yes\""));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"dayStart\":null"));
    }

    // Issue #249: signal names follow the keyword rules without "*", up to the cap; an older editor sends none.
    @Test
    void subscribeToSignals() throws RobotException {
        assertEquals(Set.of("qr-forest", "lamp_2"), subscribe(",\"signals\":[\"qr-forest\",\"lamp_2\",\"qr-forest\"]").signals());
        StringBuilder many = new StringBuilder();
        for (int i = 0; i <= Protocol.MAX_SIGNALS; i++) many.append(i == 0 ? "" : ",").append("\"s").append(i).append('"');
        assertEquals(Result.Code.BAD_ARGS, refused(",\"signals\":[" + many + "]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"signals\":[\"*\"]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"signals\":[\"two words\"]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"signals\":[\"\"]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"signals\":[1]"));
        assertEquals(Result.Code.BAD_ARGS, refused(",\"signals\":\"lamp\""));
    }

    @Test
    void eventMessage() {
        assertEquals("{\"v\":2,\"t\":\"event\",\"name\":\"chat\",\"args\":[\"jump\",3.0,0.0,-1.0]}", Protocol.event("chat", List.of("jump", 3.0, 0.0, -1.0)));
        assertEquals("{\"v\":2,\"t\":\"event\",\"name\":\"playerDied\",\"args\":[]}", Protocol.event("playerDied", List.of()));
        assertEquals("{\"v\":2,\"t\":\"event\",\"name\":\"blockBroken\",\"args\":[1]}", Protocol.event("blockBroken", List.of(1)));
        assertEquals("{\"v\":2,\"t\":\"event\",\"name\":\"signal\",\"args\":[\"qr\",0.0]}", Protocol.event("signal", List.of("qr", 0.0)));
    }
}
