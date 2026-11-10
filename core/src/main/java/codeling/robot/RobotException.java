package codeling.robot;

/** A rule violation that rejects a command before it runs. */
public class RobotException extends Exception {
    public final Result.Code code;

    public RobotException(Result.Code code, String message) {
        super(message);
        this.code = code;
    }

    public Result result() {
        return Result.error(code, getMessage());
    }
}
