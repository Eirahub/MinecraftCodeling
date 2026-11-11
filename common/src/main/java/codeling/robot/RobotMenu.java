package codeling.robot;

import codeling.McCompat;
import codeling.Codeling;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * The robot's inventory screen: a 3-row chest menu over the robot's own container (no copy, so the program and the
 * screen always see the same items) plus one data slot with the selected slot, which the client screen highlights.
 */
public class RobotMenu extends ChestMenu {
    private final RobotEntity robot; // null on the client
    private final ContainerData selected;

    /** Client side: an empty container that the server's slot updates fill. */
    public RobotMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(RobotCommand.SLOTS), null, new SimpleContainerData(1));
    }

    RobotMenu(int containerId, Inventory inventory, RobotEntity robot) {
        this(containerId, inventory, robot.inventory(), robot, new ContainerData() {
            @Override
            public int get(int dataId) {
                return robot.selectedSlot();
            }

            @Override
            public void set(int dataId, int value) {
            }

            @Override
            public int getCount() {
                return 1;
            }
        });
    }

    private RobotMenu(int containerId, Inventory inventory, Container container, RobotEntity robot, ContainerData selected) {
        super(Codeling.ROBOT_MENU.get(), containerId, inventory, container, 3);
        this.robot = robot;
        this.selected = selected;
        addDataSlots(selected);
    }

    /** 1-based, as in MakeCode. */
    public int selectedSlot() {
        return selected.get(0);
    }

    // Closes (the server checks this every tick and before every click) once the robot is removed or unloaded, out of
    // reach or in another dimension, or the player may no longer open it, so no click can reach a dropped inventory.
    @Override
    public boolean stillValid(Player player) {
        return robot == null || robot.level() == player.level() && McCompat.inReach(player, robot, 4.0)
                && RobotManager.mayOpen(player, robot);
    }
}
