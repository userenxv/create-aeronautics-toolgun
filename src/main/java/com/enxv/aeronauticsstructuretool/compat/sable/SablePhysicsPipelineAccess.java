package com.enxv.aeronauticsstructuretool.compat.sable;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;

public final class SablePhysicsPipelineAccess {
    private SablePhysicsPipelineAccess() {
    }

    public static PhysicsPipeline require(ServerLevel level) throws IOException {
        if (level == null) {
            throw new IOException("missing server level");
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null
                || container.physicsSystem() == null
                || container.physicsSystem().getPipeline() == null) {
            throw new IOException("sublevel physics pipeline unavailable in " + level.dimension().location());
        }
        return container.physicsSystem().getPipeline();
    }
}
