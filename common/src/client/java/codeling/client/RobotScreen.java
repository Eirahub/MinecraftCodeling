package codeling.client;

import codeling.robot.RobotCommand;
import codeling.robot.RobotMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

/** The vanilla 3-row chest screen with a gold frame around the robot's selected slot (issue #68). */
public class RobotScreen extends ContainerScreen {
    private static final int SELECTED_FRAME = 0xFFFFC800;

    // Takes a ChestMenu so it fits MenuScreens.register next to ContainerScreen; the menu is always a RobotMenu.
    public RobotScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    //? if >=26.1 {
    @Override
    protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int selected = menu instanceof RobotMenu robot ? robot.selectedSlot() : 0; // 0 until the server's data arrives
        if (selected >= 1 && selected <= RobotCommand.SLOTS) {
            Slot slot = menu.getSlot(selected - 1); // the robot's 27 slots come first, in order
            graphics.outline(slot.x - 1, slot.y - 1, 18, 18, SELECTED_FRAME);
        }
        super.extractSlots(graphics, mouseX, mouseY);
    }
    //?} else if >=1.21.6 && <1.21.11 {
    /*// As below; 1.21.6 to 1.21.10 call the outline submitOutline (1.21.11 renamed it back).
    @Override
    protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int selected = menu instanceof RobotMenu robot ? robot.selectedSlot() : 0; // 0 until the server's data arrives
        if (selected >= 1 && selected <= RobotCommand.SLOTS) {
            Slot slot = menu.getSlot(selected - 1); // the robot's 27 slots come first, in order
            graphics.submitOutline(leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18, SELECTED_FRAME);
        }
    }
    *///?} else {
    /*// 1.21.1 draws the slots right after the background, so the frame goes on the background (screen coordinates
    // here; slot coordinates count from the chest's corner).
    @Override
    protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int selected = menu instanceof RobotMenu robot ? robot.selectedSlot() : 0; // 0 until the server's data arrives
        if (selected >= 1 && selected <= RobotCommand.SLOTS) {
            Slot slot = menu.getSlot(selected - 1); // the robot's 27 slots come first, in order
            graphics.renderOutline(leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18, SELECTED_FRAME);
        }
    }
    *///?}
}
