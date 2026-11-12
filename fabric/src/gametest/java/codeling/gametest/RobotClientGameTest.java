package codeling.gametest;

import codeling.Codeling;
import codeling.client.RobotCameraClient;
import codeling.client.RobotScreen;
import codeling.net.RelayPayloads;
import codeling.robot.RobotCamera;
import codeling.robot.RobotEntity;
import codeling.robot.RobotLinks;
import codeling.robot.RobotManager;
import codeling.robot.RobotMenu;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Issue #134: a real client and singleplayer world. Commands go through real packets (chat commands and
 * codeling:to_server payloads), the inventory screen is used with the mouse, and the world is closed and reopened.
 * Screenshot templates live in src/gametest/resources/templates; a missing template is written on the first run.
 */
public class RobotClientGameTest implements FabricClientGameTest {
    // Mean squared difference per pixel (0 to 1). A missing bubble or frame scores far above this.
    private static final TestScreenshotComparisonAlgorithm TOLERANCE = TestScreenshotComparisonAlgorithm.meanSquaredDifference(0.005F);
    private static final int TICKS = 200;
    // The templates are crops of an 854 x 480 window at GUI scale 2 (the client GameTest default); each is searched
    // for in a window 10 pixels larger on every side, so a pixel or two of drift still passes.
    private static final int WIDTH = 854, HEIGHT = 480, MARGIN = 10;

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        UUID owner;
        BlockPos robotAt;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            TestServerContext server = world.getServer();
            int[] size = context.computeOnClient(mc -> new int[] {mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale()});
            if (size[0] != WIDTH || size[1] != HEIGHT || size[2] != 2) {
                throw new AssertionError("screenshot templates need an 854 x 480 window at GUI scale 2, got " + size[0] + " x " + size[1] + " at " + size[2]);
            }
            owner = context.computeOnClient(mc -> mc.player.getUUID());

            // /robot spawn typed by the player: a real command packet.
            command(context, world, "robot spawn");
            BlockPos spawnedAt = server.computeOnServer(s -> robot(owner).blockPosition());
            Direction facing = server.computeOnServer(s -> robot(owner).getDirection());

            // What the browser sends, as real codeling:to_server payloads from the client.
            payload(context, world, "{\"v\":2,\"t\":\"control\",\"op\":\"start\"}");
            long run = server.computeOnServer(s -> RobotManager.queue(owner).run());
            payload(context, world, cmd(1, run, "agent.move", "[0,2]"));
            robotAt = spawnedAt.relative(facing, 2);
            BlockPos target = robotAt;
            server.waitFor(s -> target.equals(robot(owner).blockPosition()), TICKS);

            // Speech bubble: synced to the client and drawn as a name tag over the robot.
            payload(context, world, cmd(2, run, "agent.say", "[\"Hello from Codeling\"]"));
            int robotId = server.computeOnServer(s -> robot(owner).getId());
            context.waitFor(mc -> mc.level.getEntity(robotId) instanceof RobotEntity r && r.said().equals("Hello from Codeling"), TICKS);
            context.getInput().lookAt(robotAt);
            context.waitTicks(5);
            context.takeScreenshot("robot-speech-bubble-full");
            assertShows(context, "robot-speech-bubble", 185, 28, 480, 44);

            // Robot camera (issue #176): /robot camera looks through the robot's eyes, the player's body stays put.
            command(context, world, "robot camera");
            context.waitFor(mc -> mc.getCameraEntity() instanceof RobotEntity, TICKS);
            Vec3 playerAt = context.computeOnClient(mc -> mc.player.position());
            context.getInput().holdKeyFor(options -> options.keyUp, 10);
            assertEquals(playerAt, context.computeOnClient(mc -> mc.player.position()), "player after walking with the camera on");
            Vec3 eye = server.computeOnServer(s -> robot(owner).getEyePosition());
            Vec3 camera = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().position());
            if (camera.distanceTo(eye) > 0.01) throw new AssertionError("camera at " + camera + ", robot's eye at " + eye);
            context.waitTicks(5);
            context.takeScreenshot("robot-camera-full");
            assertShows(context, "robot-camera", 10, 10, 380, 120); // HUD line over the sky ahead of the robot, no hand
            assertEquals(true, server.computeOnServer(s -> RobotCamera.viewing(s.getPlayerList().getPlayer(owner)) != null), "server camera state");
            // F5 does not take the eye camera out of the robot (it would show the robot's model from behind).
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(2);
            assertEquals(false, context.computeOnClient(mc -> mc.gameRenderer.mainCamera().isDetached()), "eye camera detached after F5");
            camera = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().position());
            if (camera.distanceTo(eye) > 0.01) throw new AssertionError("camera after F5 at " + camera + ", robot's eye at " + eye);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            // Escape opens the pause menu as usual and keeps the camera; /robot camera off returns to the player's eyes.
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(mc -> mc.gui.screen() instanceof PauseScreen, TICKS);
            context.waitTicks(2);
            assertEquals(true, context.computeOnClient(mc -> mc.getCameraEntity() instanceof RobotEntity), "camera on the robot with the pause menu open");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(mc -> mc.gui.screen() == null, TICKS);
            assertEquals(true, context.computeOnClient(mc -> mc.getCameraEntity() instanceof RobotEntity), "camera on the robot after the pause menu");
            command(context, world, "robot camera off");
            context.waitFor(mc -> mc.getCameraEntity() == mc.player, TICKS);
            server.waitFor(s -> RobotCamera.viewing(s.getPlayerList().getPlayer(owner)) == null, TICKS);
            // The key (unbound by default, bound here) toggles it on and off.
            context.runOnClient(mc -> {
                RobotCameraClient.KEY.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_K));
                KeyMapping.resetMapping();
            });
            context.getInput().pressKey(RobotCameraClient.KEY);
            context.waitFor(mc -> mc.getCameraEntity() instanceof RobotEntity, TICKS);
            context.getInput().pressKey(RobotCameraClient.KEY);
            context.waitFor(mc -> mc.getCameraEntity() == mc.player, TICKS);
            context.runOnClient(mc -> {
                RobotCameraClient.KEY.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
            });
            world.getConnection().waitForServerboundPackets();
            server.waitFor(s -> RobotCamera.viewing(s.getPlayerList().getPlayer(owner)) == null, TICKS);

            // Follow camera (issue #177): behind and above the robot, with the robot's model drawn.
            command(context, world, "robot camera follow");
            context.waitFor(mc -> RobotCameraClient.following(), TICKS);
            context.waitTicks(5);
            Vec3 back = Vec3.atLowerCornerOf(facing.getOpposite().getUnitVec3i());
            Vec3 offset = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().position()).subtract(eye);
            assertEquals(true, context.computeOnClient(mc -> mc.gameRenderer.mainCamera().isDetached()), "follow camera detached (robot drawn)");
            double pitch = Math.toRadians(RobotCameraClient.START_PITCH);
            if (Math.abs(offset.dot(back) - RobotCameraClient.START_DISTANCE * Math.cos(pitch)) > 0.1
                    || Math.abs(offset.y - RobotCameraClient.START_DISTANCE * Math.sin(pitch)) > 0.1) {
                throw new AssertionError("follow camera at " + offset + " from the robot's eye, expected 4 blocks behind and above at "
                        + RobotCameraClient.START_PITCH + " degrees");
            }
            context.takeScreenshot("robot-camera-follow-full");
            assertShows(context, "robot-camera-follow", 377, 212, 100, 100); // the robot's model in the middle (not its speech bubble, which times out)
            // The scroll wheel sets the distance, not the hotbar slot.
            int hotbar = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
            context.getInput().scroll(1);
            assertEquals(RobotCameraClient.START_DISTANCE - 1, context.computeOnClient(mc -> RobotCameraClient.distance()), "distance after scrolling");
            assertEquals(hotbar, context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()), "hotbar slot after scrolling");
            context.getInput().scroll(-1);
            // A wall behind the robot pulls the camera in front of it, as vanilla's third person view does.
            BlockPos wall = robotAt.relative(facing.getOpposite(), 2);
            Direction side = facing.getClockWise();
            server.runOnServer(s -> BlockPos.betweenClosed(wall.relative(side, -1), wall.relative(side, 1).above(4))
                    .forEach(p -> s.overworld().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState())));
            context.waitFor(mc -> mc.level.getBlockState(wall.above(4)).is(Blocks.STONE), TICKS);
            // The distance eases in over a few frames instead of jumping.
            context.waitFor(mc -> mc.gameRenderer.mainCamera().position().subtract(eye).dot(back) < 1.5, TICKS);
            offset = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().position()).subtract(eye);
            if (offset.dot(back) > 1.5 || offset.length() < 0.5) throw new AssertionError("follow camera at " + offset + " with a wall 1.5 blocks behind the robot's eye");
            server.runOnServer(s -> BlockPos.betweenClosed(wall.relative(side, -1), wall.relative(side, 1).above(4))
                    .forEach(p -> s.overworld().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState())));
            // The mouse orbits the camera. The test window never grabs the mouse, so this calls what MouseHandlerMixin
            // calls instead of the player's turn (defaultRequire makes the game fail to start if that hook is missing).
            float cameraYaw = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().yRot());
            context.runOnClient(mc -> RobotCameraClient.orbit(200, 0));
            context.waitTicks(2);
            if (Math.abs(context.computeOnClient(mc -> mc.gameRenderer.mainCamera().yRot()) - cameraYaw) < 1) throw new AssertionError("the mouse did not orbit the camera");
            // /robot camera follow again ends it.
            command(context, world, "robot camera follow");
            context.waitFor(mc -> mc.getCameraEntity() == mc.player, TICKS);
            server.waitFor(s -> RobotCamera.viewing(s.getPlayerList().getPlayer(owner)) == null, TICKS);

            // control stop ends the run: a later command of that run must not move the robot.
            payload(context, world, "{\"v\":2,\"t\":\"control\",\"op\":\"stop\",\"run\":" + run + "}");
            payload(context, world, cmd(3, run, "agent.move", "[0,1]"));
            context.waitTicks(20);
            assertEquals(target, server.computeOnServer(s -> robot(owner).blockPosition()), "robot after a command of a stopped run");

            // Inventory screen: items and the selected slot set with /robot, then one stack moved with the mouse.
            // The test world is survival, where /robot give needs the free_items rule.
            server.runOnServer(s -> s.getGameRules().set(Codeling.FREE_ITEMS.get(), true, s));
            command(context, world, "robot give stone 5 1");
            command(context, world, "robot slot 3");
            server.waitFor(s -> robot(owner).inventory().getItem(0).getCount() == 5 && robot(owner).selectedSlot() == 3, TICKS);
            command(context, world, "robot inventory");
            context.waitForScreen(RobotScreen.class);
            context.waitFor(mc -> mc.gui.screen() instanceof RobotScreen s && s.getMenu() instanceof RobotMenu m && m.selectedSlot() == 3, TICKS);
            context.waitTicks(2);
            context.takeScreenshot("robot-selected-slot-full");
            assertShows(context, "robot-selected-slot", 334, 104, 40, 40); // the gold frame around robot slot 3

            clickSlot(context, 0); // pick up robot slot 1
            clickSlot(context, 4); // put it in robot slot 5
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(mc -> mc.gui.screen() == null, TICKS);
            world.getConnection().waitForServerboundPackets();
            assertStone(server, owner, 0, 0);
            assertStone(server, owner, 4, 5);

            // Name tag (issue #151): a second robot, allowed by the game rule, shows its name over it. The view is
            // above the robot, so the crosshair is not on it and the owner's line (up close, looked at) stays off.
            server.runOnServer(s -> s.getGameRules().set(Codeling.MAX_ROBOTS.get(), 2, s));
            command(context, world, "robot spawn Miner");
            command(context, world, "robot move up 1 Miner");
            command(context, world, "robot move left 3 Miner");
            context.waitTicks(30);
            RobotLinks.Key miner = new RobotLinks.Key(owner, "Miner");
            int minerId = server.computeOnServer(s -> robot(miner).getId());
            BlockPos minerAt = server.computeOnServer(s -> robot(miner).blockPosition());
            context.waitFor(mc -> mc.level.getEntity(minerId) instanceof RobotEntity r && r.isCustomNameVisible()
                    && "Miner".equals(r.getCustomName().getString()) && r.ownerName().equals(mc.player.getGameProfile().name()), TICKS);
            context.getInput().lookAt(minerAt.above(2));
            context.waitTicks(5);
            context.takeScreenshot("robot-name-tag-full");
            // Styles (issue #242): each robot shows its own, synced to the client, and keeps its name.
            command(context, world, "robot skin industrial Miner");
            command(context, world, "robot skin mint");
            context.waitFor(mc -> mc.level.getEntity(minerId) instanceof RobotEntity r && r.skin().equals("industrial")
                    && "Miner".equals(r.getCustomName().getString())
                    && mc.level.getEntity(robotId) instanceof RobotEntity d && d.skin().equals("mint"), TICKS);
            context.getInput().lookAt(minerAt);
            context.waitTicks(210); // the chat lines over the robots fade after 10 seconds
            context.takeScreenshot("robot-styles-full");
            context.getInput().lookAt(robotAt);
            context.waitTicks(5);
            context.takeScreenshot("robot-mint-full");
            command(context, world, "robot animation wave");
            context.waitFor(mc -> mc.level.getEntity(robotId) instanceof RobotEntity r && r.animation().equals("wave"), TICKS);
            context.waitTicks(10);
            context.takeScreenshot("robot-wave-full");
            command(context, world, "robot nametag off Miner");
            context.waitFor(mc -> mc.level.getEntity(minerId) instanceof RobotEntity r && !r.isCustomNameVisible(), TICKS);
            save = world.getWorldSave();
        }

        // Close and reopen: the robot comes back where it was, with its items and selected slot.
        try (TestSingleplayerContext world = save.open()) {
            world.getConnection().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.waitFor(s -> RobotManager.robot(owner) != null, TICKS);
            BlockPos expected = robotAt;
            assertEquals(expected, server.computeOnServer(s -> robot(owner).blockPosition()), "robot position after reload");
            assertEquals(3, server.computeOnServer(s -> robot(owner).selectedSlot()), "selected slot after reload");
            assertStone(server, owner, 0, 0);
            assertStone(server, owner, 4, 5);
            // The styles and the name come back with the robots, on the server and on the client.
            RobotLinks.Key miner = new RobotLinks.Key(owner, "Miner");
            server.waitFor(s -> RobotManager.robot(miner) != null, TICKS);
            assertEquals("mint", server.computeOnServer(s -> robot(owner).skin()), "skin after reload");
            assertEquals("industrial", server.computeOnServer(s -> robot(miner).skin()), "Miner's skin after reload");
            int minerId = server.computeOnServer(s -> robot(miner).getId());
            context.waitFor(mc -> mc.level.getEntity(minerId) instanceof RobotEntity r && r.skin().equals("industrial")
                    && "Miner".equals(r.getCustomName().getString()), TICKS);
        }
    }

    /** The template (cropped at x, y with this size from a reference run) shows up near that spot. */
    private static void assertShows(ClientGameTestContext context, String template, int x, int y, int width, int height) {
        context.assertScreenshotContains(TestScreenshotComparisonOptions.of(template).withAlgorithm(TOLERANCE)
                .withRegion(x - MARGIN, y - MARGIN, width + 2 * MARGIN, height + 2 * MARGIN).save());
    }

    private static RobotEntity robot(UUID owner) {
        RobotEntity robot = RobotManager.robot(owner);
        if (robot == null) throw new AssertionError("robot is not loaded");
        return robot;
    }

    private static RobotEntity robot(RobotLinks.Key key) {
        RobotEntity robot = RobotManager.robot(key);
        if (robot == null) throw new AssertionError("robot " + key.name() + " is not loaded");
        return robot;
    }

    private static String cmd(long id, long run, String op, String args) {
        return "{\"v\":2,\"t\":\"cmd\",\"id\":" + id + ",\"run\":" + run + ",\"op\":\"" + op + "\",\"args\":" + args + "}";
    }

    private static void command(ClientGameTestContext context, TestSingleplayerContext world, String command) {
        context.runOnClient(mc -> mc.getConnection().sendCommand(command));
        world.getConnection().waitForServerboundPackets();
    }

    private static void payload(ClientGameTestContext context, TestSingleplayerContext world, String json) {
        context.runOnClient(mc -> ClientPlayNetworking.send(new RelayPayloads.ToServer(json)));
        world.getConnection().waitForServerboundPackets();
    }

    /** Left click on a slot of the open robot screen, in window pixels. */
    private static void clickSlot(ClientGameTestContext context, int index) {
        double[] xy = context.computeOnClient(mc -> {
            RobotScreen screen = (RobotScreen) mc.gui.screen();
            Slot slot = screen.getMenu().getSlot(index);
            // leftPos and topPos are protected; AbstractContainerScreen.init centres the 176 x (114 + 3 * 18) chest image.
            int left = (screen.width - 176) / 2;
            int top = (screen.height - (114 + 3 * 18)) / 2;
            int scale = mc.getWindow().getGuiScale();
            return new double[] {(left + slot.x + 8) * scale, (top + slot.y + 8) * scale};
        });
        context.getInput().setCursorPos(xy[0], xy[1]);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static void assertStone(TestServerContext server, UUID owner, int index, int count) {
        ItemStack stack = server.computeOnServer(s -> robot(owner).inventory().getItem(index).copy());
        if (count == 0 ? !stack.isEmpty() : !stack.is(Items.STONE) || stack.getCount() != count) {
            throw new AssertionError("robot slot " + (index + 1) + ": expected " + count + " stone, got " + stack);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }
}
