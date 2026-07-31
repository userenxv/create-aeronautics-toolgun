package com.enxv.aeronauticsstructuretool.network.handler;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.client.render.ClientConstraintVisualTracker;
import com.enxv.aeronauticsstructuretool.ConnectionMode;
import com.enxv.aeronauticsstructuretool.SyncConstraintVisualsPayload;
import com.enxv.aeronauticsstructuretool.toolgun.constraint.ConstraintVisualSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class ConstraintVisualPayloadHandler {
    private ConstraintVisualPayloadHandler() {
    }

    public static void handleSync(SyncConstraintVisualsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            List<ClientConstraintVisualTracker.ConstraintVisual> visuals = new ArrayList<>();
            try {
                for (SyncConstraintVisualsPayload.Entry entry : payload.entries()) {
                    visuals.add(new ClientConstraintVisualTracker.ConstraintVisual(
                            entry.firstSubLevelId(),
                            entry.secondSubLevelId(),
                            ConnectionMode.fromName(entry.connectionMode()),
                            new Vector3d(entry.firstDisplayLocalPoint()),
                            new Vector3d(entry.secondDisplayLocalPoint()),
                            new Vector3d(entry.firstConstraintLocalPoint()),
                            new Vector3d(entry.secondConstraintLocalPoint()),
                            entry.firstAxisLocal() != null ? new Vector3d(entry.firstAxisLocal()) : null
                    ));
                }
            } catch (IllegalArgumentException exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Rejected malformed constraint visual synchronization payload",
                        exception
                );
                return;
            }
            ClientConstraintVisualTracker.replaceAll(visuals);
        });
    }

    public static void sync(ServerLevel level, List<ConstraintVisualSnapshot> snapshots) {
        if (level.players().isEmpty()) {
            return;
        }
        SyncConstraintVisualsPayload payload = createPayload(snapshots);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void syncTo(ServerPlayer player, List<ConstraintVisualSnapshot> snapshots) {
        PacketDistributor.sendToPlayer(player, createPayload(snapshots));
    }

    private static SyncConstraintVisualsPayload createPayload(List<ConstraintVisualSnapshot> snapshots) {
        List<SyncConstraintVisualsPayload.Entry> entries = new ArrayList<>();
        for (ConstraintVisualSnapshot snapshot : snapshots) {
            entries.add(new SyncConstraintVisualsPayload.Entry(
                    snapshot.firstSubLevelId(),
                    snapshot.secondSubLevelId(),
                    snapshot.connectionMode().name(),
                    new Vector3d(snapshot.firstDisplayLocalPoint()),
                    new Vector3d(snapshot.secondDisplayLocalPoint()),
                    new Vector3d(snapshot.firstConstraintLocalPoint()),
                    new Vector3d(snapshot.secondConstraintLocalPoint()),
                    snapshot.firstAxisLocal() != null ? new Vector3d(snapshot.firstAxisLocal()) : null
            ));
        }
        return new SyncConstraintVisualsPayload(entries);
    }
}
