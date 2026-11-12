package codeling.client;

import codeling.Codeling;
import codeling.net.RelayPayloads;
import codeling.robot.RobotCamera;
import codeling.robot.RobotEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=1.21.2
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Robot camera, client side (issue #176): the server says which robot to look through (RobotCamera checks the rules)
 * and the client makes it the camera entity, as spectating does. Vanilla then skips the camera entity's model in
 * first person (so RobotRenderer needs no change), stops sending the player's position and ignores movement input,
 * so the player's body stays put. Follow mode (issue #177) keeps the robot as camera entity and CameraMixin moves the
 * camera behind it; MouseHandlerMixin turns the mouse and scroll wheel into orbit and distance while it is on.
 */
public final class RobotCameraClient {
    /** Unbound by default; set it in Controls under Codeling. The loader registers both keys. */
    //? if >=26.1 {
    public static final KeyMapping KEY = new KeyMapping("key.codeling.camera",
            InputConstants.UNKNOWN.getValue(), KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Codeling.MOD_ID, "codeling")));
    //?} else {
    /*// Before KeyMapping.Category the category is a translation key; this one is the 26.x category's key.
    public static final KeyMapping KEY = new KeyMapping("key.codeling.camera",
            InputConstants.UNKNOWN.getValue(), "key.category.codeling.codeling");
    *///?}
    /** Follow mode; unbound by default as well. */
    public static final KeyMapping FOLLOW_KEY = new KeyMapping("key.codeling.camera_follow",
            InputConstants.UNKNOWN.getValue(), KEY.getCategory());
    /** The HUD element's id. */
    public static final Identifier HUD = Identifier.fromNamespaceAndPath(Codeling.MOD_ID, "camera");
    public static final float MIN_DISTANCE = 2, MAX_DISTANCE = 12, START_DISTANCE = 4, START_PITCH = 55;
    // Follow mode easing, per second (issue #177): the yaw follows the robot's 90-degree turns in about a third of a
    // second; a wall pulls the camera in fast (it would show the inside of the wall) and lets it out slowly.
    private static final float YAW_RATE = 8, IN_RATE = 20, OUT_RATE = 3;
    private static RobotEntity robot; // the robot the camera shows, or null
    private static boolean follow;
    // Follow mode: the eased yaw behind the robot, the mouse's orbit, the wheel's distance and the eased distance shown.
    private static float yaw, orbitYaw, pitch, distance, shown;
    private static long lastFrame; // System.nanoTime of the last follow frame, 0 before the first
    private static float frameSeconds;

    private RobotCameraClient() {
    }

    /** A {@code codeling:camera} payload from the server. */
    public static void receive(Minecraft mc, RelayPayloads.Camera payload) {
        if (payload.robot() == RobotCamera.NONE) end(mc, false);
        else if (mc.level.getEntity(payload.robot()) instanceof RobotEntity r) {
            robot = r;
            follow = payload.follow();
            yaw = r.getYRot();
            orbitYaw = 0;
            pitch = START_PITCH;
            distance = shown = START_DISTANCE;
            lastFrame = 0;
            mc.setCameraEntity(r);
        } else end(mc, true); // not tracked by this client yet
    }

    /**
     * Start of the client tick: before Minecraft handles the attack and use keys, which would aim from the robot's eye.
     * Escape is left to vanilla (the pause menu) and keeps the camera; the camera key or /robot camera off leaves it.
     */
    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        while (KEY.consumeClick()) {
            if (robot != null && !follow) end(mc, true);
            else mc.getConnection().sendCommand("robot camera");
        }
        while (FOLLOW_KEY.consumeClick()) {
            if (robot != null && follow) end(mc, true);
            else mc.getConnection().sendCommand("robot camera follow");
        }
        if (robot == null) return;
        if (robot.level() != mc.level) { // left the world: nothing to tell
            robot = null;
        } else if (robot.isRemoved() || mc.getCameraEntity() != robot) {
            end(mc, true);
        } else {
            while (mc.options.keyAttack.consumeClick()) { }
            while (mc.options.keyUse.consumeClick()) { }
            while (mc.options.keyPickItem.consumeClick()) { }
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
        }
    }

    //? if >=1.21.2 {
    /**
     * End of level extraction: the first-person hand and the fire, water and portal overlays belong to the player.
     * Before 26.3 the level render state has no player part; GameRendererMixin hides the hand there.
     */
    public static void extracted(LevelRenderState state) {
        //? if >=26.3
        if (robot != null) state.playerRenderState.reset();
    }
    //?}

    /** The HUD element: which robot the camera shows. */
    public static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (robot != null) {
            Minecraft mc = Minecraft.getInstance();
            //? if >=26.1 {
            graphics.text(mc.font, Component.translatable("hud.codeling.camera", robot.getDisplayName()), 4, 4, 0xFFFFFFFF);
            //?} else
            /*graphics.drawString(mc.font, Component.translatable("hud.codeling.camera", robot.getDisplayName()), 4, 4, 0xFFFFFFFF);*/
        }
    }

    /** The robot the camera shows, or null. */
    public static RobotEntity robot() {
        return robot;
    }

    public static boolean following() {
        return robot != null && follow;
    }

    /**
     * Follow mode's camera yaw, once per frame (CameraMixin, before {@link #shownDistance}): eased toward the robot's
     * facing ({@code robotYaw}, already interpolated with the partial tick), plus the mouse's orbit.
     */
    public static float yaw(float robotYaw) {
        long now = System.nanoTime();
        frameSeconds = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 1e9F, 0.1F);
        lastFrame = now;
        yaw += Mth.wrapDegrees(robotYaw - yaw) * ease(YAW_RATE);
        return yaw + orbitYaw;
    }

    /** Follow mode's distance this frame: eased toward {@code allowed}, the wheel's distance shortened by any block behind. */
    public static float shownDistance(float allowed) {
        shown += (allowed - shown) * ease(allowed < shown ? IN_RATE : OUT_RATE);
        return shown;
    }

    // How much of the way to the target to go this frame: the same speed at any frame rate.
    private static float ease(float rate) {
        return 1 - (float) Math.exp(-rate * frameSeconds);
    }

    public static float pitch() {
        return pitch;
    }

    /** How far behind the robot's eye, before CameraMixin pulls it in front of a block. */
    public static float distance() {
        return distance;
    }

    /** The mouse in follow mode: false when it should turn the player as usual. Same scale as Entity.turn. */
    public static boolean orbit(double x, double y) {
        if (!following()) return false;
        orbitYaw += (float) x * 0.15F;
        pitch = Mth.clamp(pitch + (float) y * 0.15F, -90, 90);
        return true;
    }

    /** The scroll wheel in follow mode, one block per step: false when it should change the hotbar slot as usual. */
    public static boolean zoom(double scroll) {
        if (!following()) return false;
        distance = Mth.clamp(distance - (float) Math.signum(scroll), MIN_DISTANCE, MAX_DISTANCE);
        return true;
    }

    /** Back to the player's own eyes; {@code tellServer} when the client ends it (key, robot gone). */
    private static void end(Minecraft mc, boolean tellServer) {
        if (robot != null && mc.getCameraEntity() == robot) mc.setCameraEntity(mc.player);
        robot = null;
        if (tellServer && mc.getConnection() != null) mc.getConnection().sendCommand("robot camera off");
    }
}
