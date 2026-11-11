package codeling.robot;

/**
 * Outcome of one robot command: {@code ok} with a value, or an error code from the protocol (architecture section 3).
 * An ok result may carry a message too: why an action did less than asked (no blocks left), shown in the editor's log.
 */
public record Result(boolean ok, Object value, Code code, String message) {
    public enum Code { BAD_ARGS, NO_PERMISSION, RATE_LIMITED, PROTECTED, UNLOADED, OUT_OF_RANGE, STOPPED, VERSION }

    public static Result ok(Object value) {
        return new Result(true, value, null, null);
    }

    /** Ok, with a sentence for the player on why the action did less than asked. */
    public static Result ok(Object value, String message) {
        return new Result(true, value, null, message);
    }

    public static Result error(Code code, String message) {
        return new Result(false, null, code, message);
    }

    @Override
    public String toString() {
        return ok ? "ok " + value + (message == null ? "" : " (" + message + ")") : code + ": " + message;
    }
}
