package com.enxv.aeronauticsstructuretool.toolgun.constraint;

import com.enxv.aeronauticsstructuretool.SurvivalToolgunConfig;
import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.server.ServerServices;
import com.enxv.aeronauticsstructuretool.toolgun.ToolgunAccessPolicy;
import com.enxv.aeronauticsstructuretool.core.FailureMessages;
import com.enxv.aeronauticsstructuretool.core.ModConstants;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ConstraintToolService {
    private ConstraintToolService() {
    }

    public static void disconnect(ServerPlayer player, BlockPos clickedPos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (ToolgunAccessPolicy.rejectSurvivalFeature(
                player,
                SurvivalToolgunConfig.allowDisconnect(),
                Component.translatable("screen.create_aeronautics_toolgun.mode.disconnect")
        )) {
            return;
        }
        try {
            SubLevel containing = Sable.HELPER.getContaining(level, clickedPos);
            if (!(containing instanceof ServerSubLevel serverSubLevel)) {
                throw new IllegalStateException("target is not a physical structure");
            }
            int removed = ToolgunConstraintTracker.removeConstraintsForSubLevel(level, serverSubLevel.getUniqueId());
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.disconnect_success",
                    removed
            ));
        } catch (Exception exception) {
            ModConstants.LOGGER.warn(
                    "Constraint disconnect failed for player {} at {}",
                    player.getGameProfile().getName(),
                    clickedPos,
                    exception
            );
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.disconnect_failed",
                    FailureMessages.describe(exception, "constraint disconnect failed")
            ));
        }
    }

    public static void toggleCollision(ServerPlayer player, BlockPos clickedPos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (ToolgunAccessPolicy.holdsSurvivalTool(player)) {
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.survival_tool_restricted",
                    Component.translatable("screen.create_aeronautics_toolgun.tool.no_collision_mode")
            ));
            return;
        }
        try {
            SubLevel containing = Sable.HELPER.getContaining(level, clickedPos);
            if (!(containing instanceof ServerSubLevel serverSubLevel)) {
                throw new IllegalStateException("target is not a physical structure");
            }
            boolean disabled = ServerServices.SUBLEVEL_COLLISION.toggle(level, serverSubLevel);
            player.sendSystemMessage(Component.translatable(disabled
                    ? "message.create_aeronautics_toolgun.no_collision_enabled"
                    : "message.create_aeronautics_toolgun.no_collision_disabled"));
        } catch (Exception exception) {
            ModConstants.LOGGER.warn(
                    "Collision toggle failed for player {} at {}",
                    player.getGameProfile().getName(),
                    clickedPos,
                    exception
            );
            player.sendSystemMessage(Component.translatable(
                    "message.create_aeronautics_toolgun.no_collision_failed",
                    FailureMessages.describe(exception, "collision toggle failed")
            ));
        }
    }
}
