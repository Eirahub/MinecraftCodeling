package codeling.net;

import java.util.Collection;

/**
 * Chat-command events (issue #27): which {@code player.onChat} keyword a chat line matches, and the numbers after it.
 * Plain Java so the rules are unit-testable. Only the keyword and the numbers ever reach a program, never the line.
 */
public final class ChatCommands {
    /** The keyword that matches every line, as in MakeCode. */
    public static final String ANY = "*";
    // shortcut: 16 keywords of 32 characters per run are guesses at "plenty for a lesson"; raise them if players ask.
    public static final int MAX_KEYWORDS = 16;
    public static final int MAX_KEYWORD_LENGTH = 32;
    /** MakeCode's handler gets num1, num2 and num3. */
    public static final int NUMBERS = 3;

    private ChatCommands() {
    }

    /** Why {@code keyword} cannot be an onChat keyword, or null: one word of letters, digits, - and _, or "*". */
    public static String keywordProblem(String keyword) {
        if (keyword.equals(ANY)) return null;
        if (keyword.isEmpty() || keyword.length() > MAX_KEYWORD_LENGTH) {
            return "an on chat command keyword must be 1 to " + MAX_KEYWORD_LENGTH + " characters long";
        }
        return isWord(keyword) ? null : "an on chat command keyword is one word of letters, digits, - and _ (or * for every line)";
    }

    /** True when {@code s} has only letters, digits, - and _ (the length is checked by the caller). Signal names (issue #249) use it too. */
    public static boolean isWord(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') return false;
        }
        return true;
    }

    /**
     * The keyword {@code line} triggers: its first word when a handler listens for it, else "*" when one listens for
     * every line, else null (the line is not for the program). Case matters, as in a program's string compare.
     */
    public static String match(Collection<String> keywords, String line) {
        String[] words = words(line);
        if (words.length == 0) return null;
        if (keywords.contains(words[0])) return words[0];
        return keywords.contains(ANY) ? ANY : null;
    }

    /** num1, num2 and num3: the words after the first, each parsed as JavaScript's parseInt, 0 when missing or not a number. */
    public static double[] numbers(String line) {
        String[] words = words(line);
        double[] numbers = new double[NUMBERS];
        for (int i = 0; i < NUMBERS && i + 1 < words.length; i++) numbers[i] = parseInt(words[i + 1]);
        return numbers;
    }

    private static String[] words(String line) {
        String s = line.strip();
        return s.isEmpty() ? new String[0] : s.split("\\s+");
    }

    /**
     * JavaScript's parseInt(word) as MakeCode's chat numbers use it: an optional sign, then 0x for hex, then as many
     * ASCII digits as there are ("12abc" is 12, "3.9" is 3). NaN (no digits) becomes 0, and so does -0.
     */
    static double parseInt(String word) {
        int i = 0;
        boolean negative = false;
        if (i < word.length() && (word.charAt(i) == '+' || word.charAt(i) == '-')) negative = word.charAt(i++) == '-';
        int radix = 10;
        if (i + 1 < word.length() && word.charAt(i) == '0' && (word.charAt(i + 1) == 'x' || word.charAt(i + 1) == 'X')) {
            radix = 16;
            i += 2;
        }
        double value = 0;
        for (; i < word.length(); i++) {
            char c = word.charAt(i);
            int digit = c < 128 ? Character.digit(c, radix) : -1;
            if (digit < 0) break;
            value = value * radix + digit;
        }
        // A line has at most 256 characters, so the value stays finite; the check keeps JSON valid regardless.
        if (value == 0 || !Double.isFinite(value)) return 0;
        return negative ? -value : value;
    }
}
