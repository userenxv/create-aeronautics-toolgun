package com.enxv.aeronauticsstructuretool.compat.synaxis;

import com.enxv.aeronauticsstructuretool.blueprint.model.CapturePlan;
import com.enxv.aeronauticsstructuretool.blueprint.model.CapturedSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;

public final class SynaxisCaptureSession {
    private final ServerLevel level;
    private final CapturePlan plan;

    public SynaxisCaptureSession(ServerLevel level, CapturePlan plan) {
        this.level = level;
        this.plan = plan;
    }

    public void processPlot(CompoundTag plotTag, CapturedSubLevel currentSubLevel) throws IOException {
        SynaxisBlockEntitySanitizer.sanitizeForSave(level, plotTag);
        SynaxisControllerWireCompat.capture(
                level,
                plotTag,
                plan,
                currentSubLevel.subLevel()
        );
        SynaxisCompanionJointCompat.capture(level, plotTag, plan, currentSubLevel);
    }

    public void finishRoot(CompoundTag root) throws IOException {
        SynaxisBlueprintNbt.writeCimulink(root, SynaxisCimulinkCompat.capture(level, plan));
    }
}
