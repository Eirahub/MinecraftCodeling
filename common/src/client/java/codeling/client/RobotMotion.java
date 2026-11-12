package codeling.client;

import codeling.robot.RobotAppearance;
import codeling.robot.RobotEntity;
import net.minecraft.world.phys.Vec3;

/** Client-only visual interpolation; commands and collision keep their authoritative grid positions. */
final class RobotMotion {
    record Frame(Vec3 offset, float yaw, String animation, float seconds, float amount, boolean skipRoot) {
    }

    private Vec3 from, target;
    private float moveStarted = -100, turnStarted = -100, fromYaw, targetYaw, turnDelta;

    Frame sample(RobotEntity robot, float partialTick, RobotModel model) {
        float age = robot.tickCount + partialTick;
        if (target == null) {
            from = target = robot.position();
            fromYaw = targetYaw = robot.getYRot();
        }
        Vec3 visual = from.lerp(target, progress(age - moveStarted, 5));
        float yaw = fromYaw + turnDelta * progress(age - turnStarted, 9);
        if (!target.equals(robot.position())) {
            double distance = target.distanceTo(robot.position());
            from = distance <= 1.5 ? visual : robot.position();
            target = robot.position();
            moveStarted = distance <= 1.5 ? age : -100;
            visual = from;
        }
        if (RobotAppearance.angleDelta(targetYaw, robot.getYRot()) != 0) {
            fromYaw = yaw;
            targetYaw = robot.getYRot();
            turnDelta = RobotAppearance.angleDelta(fromYaw, targetYaw);
            turnStarted = age;
        }
        float movingFor = age - moveStarted, turningFor = age - turnStarted;
        String animation = "idle";
        float seconds = age / 20, amount = 1;
        boolean skipRoot = false;
        if (movingFor < 8) {
            animation = "walk";
            seconds = movingFor / 10 + 0.25F;
            amount = Math.min(1, movingFor / 1.5F) * Math.max(0, Math.min(1, (8 - movingFor) / 3));
        } else if (turningFor < 9) {
            animation = turnDelta < 0 ? "turn_left" : "turn_right";
            seconds = turningFor / 9 * model.length(animation);
            skipRoot = true;
        }
        String gesture = robot.animation();
        float gestureTime = robot.animationSeconds(partialTick);
        if (!gesture.equals("idle") && gestureTime <= model.length(gesture)) {
            animation = gesture;
            seconds = gestureTime;
            amount = 1;
        }
        // The dispatcher already positions the entity at its vanilla tick interpolation.
        Vec3 base = new Vec3(robot.xOld + (robot.getX() - robot.xOld) * partialTick,
                robot.yOld + (robot.getY() - robot.yOld) * partialTick, robot.zOld + (robot.getZ() - robot.zOld) * partialTick);
        return new Frame(visual.subtract(base), yaw, animation, seconds, amount, skipRoot);
    }

    private static float progress(float elapsed, float length) {
        float t = Math.max(0, Math.min(1, elapsed / length));
        return t * t * (3 - 2 * t);
    }
}
