package com.enxv.aeronauticsstructuretool.toolgun.weld;

import com.enxv.aeronauticsstructuretool.BearingAxisMode;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import net.minecraft.core.Direction;
import org.joml.Vector3d;

import java.util.UUID;

public record WeldRequest(
        UUID firstSubLevelId,
        UUID secondSubLevelId,
        Vector3d firstPoint,
        Vector3d adjustedSecondPoint,
        Vector3d secondLocalPoint,
        Direction firstFace,
        Direction secondFace,
        BearingAxisMode bearingAxisMode,
        ConnectionMode connectionMode
) {
}
