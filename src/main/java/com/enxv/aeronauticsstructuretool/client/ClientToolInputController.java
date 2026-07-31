package com.enxv.aeronauticsstructuretool.client;

import com.enxv.aeronauticsstructuretool.client.render.ClientConstraintVisualTracker;
import com.enxv.aeronauticsstructuretool.client.tool.ClientStructureToolHandler;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.client.screen.ToolModeScreen;
import com.enxv.aeronauticsstructuretool.ToolPanel;
import com.enxv.aeronauticsstructuretool.client.tool.ClientHeldToolState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientToolInputController {
    private static final String KEY_CATEGORY = "key.categories.create_aeronautics_toolgun";
    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.create_aeronautics_toolgun.open_menu",
            GLFW.GLFW_KEY_TAB,
            KEY_CATEGORY
    );
    private static boolean simpleWeldCtrlWasDown;
    private static boolean heldStructureToolLastTick;

    private ClientToolInputController() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            heldStructureToolLastTick = false;
            ClientStructureToolHandler.clearPendingWeld();
            ClientConstraintVisualTracker.clear();
            return;
        }

        boolean holdsStructureTool = ClientHeldToolState.holdsStructureTool(minecraft.player);
        if (holdsStructureTool && !heldStructureToolLastTick) {
            ClientToolState.setPanel(ToolPanel.BLUEPRINTS);
            ClientToolState.setMode(ToolMode.SAVE);
        }
        heldStructureToolLastTick = holdsStructureTool;
        if (minecraft.screen != null) {
            return;
        }

        while (OPEN_MENU.consumeClick()) {
            if (holdsStructureTool) {
                minecraft.setScreen(new ToolModeScreen());
            }
        }

        if (!holdsStructureTool) {
            ClientStructureToolHandler.clearPendingWeld();
            ClientStructureToolHandler.clearPendingSimpleWeld();
        } else if (ClientHeldToolState.holdsRestrictedStructureTool(minecraft.player)) {
            sanitizeRestrictedToolState();
        }
        boolean ctrlDown = !minecraft.options.keyShift.isDown()
                && (GLFW.glfwGetKey(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
        if (ClientToolState.getMode() == ToolMode.SIMPLE_WELD
                && ClientStructureToolHandler.isAdjustingSimpleWeld()
                && ctrlDown
                && !simpleWeldCtrlWasDown) {
            ClientStructureToolHandler.togglePendingSimpleWeldAdjustMode();
        }
        simpleWeldCtrlWasDown = ctrlDown;
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ClientHeldToolState.holdsStructureTool(minecraft.player)) {
            return;
        }
        if (Math.abs(event.getScrollDeltaY()) < 1.0E-4D) {
            return;
        }
        if (minecraft.screen instanceof ToolModeScreen toolModeScreen) {
            if (toolModeScreen.shouldHotbarScrollPassThrough()) {
                rotateSelectedHotbarSlot(minecraft, event.getScrollDeltaY());
                event.setCanceled(true);
            }
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        int delta = event.getScrollDeltaY() > 0.0D ? 1 : -1;
        if (ClientToolState.getMode() == ToolMode.WELD
                && ClientStructureToolHandler.isAdjustingWeldDistance()) {
            ClientStructureToolHandler.adjustPendingWeldDistance(delta);
            event.setCanceled(true);
            return;
        }
        if (ClientToolState.getMode() == ToolMode.SIMPLE_WELD
                && ClientStructureToolHandler.isAdjustingSimpleWeld()) {
            ClientStructureToolHandler.adjustPendingSimpleWeld(delta);
            event.setCanceled(true);
            return;
        }
        if (ClientToolState.getMode() == ToolMode.ROTATE
                && ClientStructureToolHandler.isAdjustingRotation()) {
            ClientStructureToolHandler.adjustPendingRotation(delta);
            event.setCanceled(true);
            return;
        }
        if (ClientToolState.getMode() == ToolMode.TRANSLATE
                && ClientStructureToolHandler.isAdjustingTranslation()) {
            ClientStructureToolHandler.adjustPendingTranslation(delta);
            event.setCanceled(true);
            return;
        }
        if (ClientToolState.getMode() == ToolMode.WELD
                && ClientToolState.getConnectionMode() == ConnectionMode.BEARING
                && !ClientStructureToolHandler.isAdjustingWeldDistance()) {
            ClientToolState.cycleBearingAxisMode(delta);
            event.setCanceled(true);
            return;
        }
        if (ClientToolState.getMode() == ToolMode.SAVE) {
            ClientToolState.adjustConnectedSublevelProximity(delta);
            event.setCanceled(true);
            return;
        }
        if (ClientToolState.getMode() != ToolMode.LOAD) {
            return;
        }
        if (minecraft.player.isCrouching()) {
            ClientToolState.adjustOffsetY(delta);
            event.setCanceled(true);
            return;
        }
        if (minecraft.options.keySprint.isDown()) {
            ClientToolState.cycleSnapMode(delta);
            event.setCanceled(true);
            return;
        }
        ClientToolState.rotatePlacement(delta);
        event.setCanceled(true);
    }

    public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.screen != null
                || !ClientHeldToolState.holdsStructureTool(minecraft.player)) {
            return;
        }
        if (event.isAttack()
                && ClientToolState.getMode() == ToolMode.WELD
                && ClientStructureToolHandler.isAdjustingWeldDistance()) {
            if (ClientStructureToolHandler.confirmPendingWeld()) {
                cancelInteraction(event);
            }
            return;
        }
        if (event.isAttack()
                && ClientToolState.getMode() == ToolMode.SIMPLE_WELD
                && ClientStructureToolHandler.isAdjustingSimpleWeld()) {
            if (ClientStructureToolHandler.confirmPendingSimpleWeld()) {
                cancelInteraction(event);
            }
            return;
        }
        if (event.isAttack()
                && ClientToolState.getMode() == ToolMode.ROTATE
                && ClientStructureToolHandler.isAdjustingRotation()) {
            if (ClientStructureToolHandler.confirmPendingRotation()) {
                cancelInteraction(event);
            }
            return;
        }
        if (event.isAttack()
                && ClientToolState.getMode() == ToolMode.TRANSLATE
                && ClientStructureToolHandler.isAdjustingTranslation()
                && ClientStructureToolHandler.confirmPendingTranslation()) {
            cancelInteraction(event);
        }
    }

    private static void rotateSelectedHotbarSlot(Minecraft minecraft, double scrollDelta) {
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.getInventory().swapPaint(scrollDelta);
        if (minecraft.player.connection != null) {
            minecraft.player.connection.send(
                    new ServerboundSetCarriedItemPacket(minecraft.player.getInventory().selected)
            );
        }
    }

    private static void sanitizeRestrictedToolState() {
        if (ClientToolState.getMode() == ToolMode.LOAD
                || ClientToolState.getMode() == ToolMode.DELETE
                || ClientToolState.getMode() == ToolMode.NO_COLLISION
                || ClientToolState.getMode() == ToolMode.GHOST_VEHICLE_TEST) {
            ClientToolState.setPanel(ToolPanel.BLUEPRINTS);
            ClientToolState.setMode(ToolMode.SAVE);
        }
    }

    private static void cancelInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        event.setSwingHand(false);
        event.setCanceled(true);
    }
}
