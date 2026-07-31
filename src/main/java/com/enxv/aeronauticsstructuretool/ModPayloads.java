package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.network.handler.ClientPayloadHandlers;
import com.enxv.aeronauticsstructuretool.network.handler.BlueprintPayloadHandlers;
import com.enxv.aeronauticsstructuretool.network.handler.ConstraintVisualPayloadHandler;
import com.enxv.aeronauticsstructuretool.network.handler.ConstraintToolPayloadHandlers;
import com.enxv.aeronauticsstructuretool.network.handler.MagneticGunPayloadHandlers;
import com.enxv.aeronauticsstructuretool.network.handler.PortableStructurePrinterPayloadHandlers;
import com.enxv.aeronauticsstructuretool.network.handler.StructureDeletePayloadHandler;
import com.enxv.aeronauticsstructuretool.network.handler.StructureTransformPayloadHandlers;
import com.enxv.aeronauticsstructuretool.network.handler.VehicleQueryPayloadHandlers;
import com.enxv.aeronauticsstructuretool.network.handler.WeldPayloadHandlers;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private ModPayloads() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ModPayloads::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AeronauticsStructureToolMod.MOD_ID);
        registrar.playToServer(SaveSubLevelPayload.TYPE, SaveSubLevelPayload.STREAM_CODEC, BlueprintPayloadHandlers::handleSave);
        registrar.playToServer(LoadSubLevelPayload.TYPE, LoadSubLevelPayload.STREAM_CODEC, BlueprintPayloadHandlers::handleLoad);
        registrar.playToServer(LoadSubLevelChunkPayload.TYPE, LoadSubLevelChunkPayload.STREAM_CODEC, BlueprintPayloadHandlers::handleLoadChunk);
        registrar.playToServer(CompleteLoadSubLevelPayload.TYPE, CompleteLoadSubLevelPayload.STREAM_CODEC, BlueprintPayloadHandlers::handleLoadComplete);
        registrar.playToServer(WeldSubLevelsPayload.TYPE, WeldSubLevelsPayload.STREAM_CODEC, WeldPayloadHandlers::handleWeld);
        registrar.playToServer(MoveSubLevelPointPayload.TYPE, MoveSubLevelPointPayload.STREAM_CODEC, WeldPayloadHandlers::handleMovePoint);
        registrar.playToServer(BeginTranslateSubLevelPayload.TYPE, BeginTranslateSubLevelPayload.STREAM_CODEC, StructureTransformPayloadHandlers::handleBeginTranslation);
        registrar.playToServer(AdjustTranslateSubLevelPayload.TYPE, AdjustTranslateSubLevelPayload.STREAM_CODEC, StructureTransformPayloadHandlers::handleAdjustTranslation);
        registrar.playToServer(FinishTranslateSubLevelPayload.TYPE, FinishTranslateSubLevelPayload.STREAM_CODEC, StructureTransformPayloadHandlers::handleFinishTranslation);
        registrar.playToServer(BeginRotateSubLevelPayload.TYPE, BeginRotateSubLevelPayload.STREAM_CODEC, StructureTransformPayloadHandlers::handleBeginRotation);
        registrar.playToServer(AdjustRotateSubLevelPayload.TYPE, AdjustRotateSubLevelPayload.STREAM_CODEC, StructureTransformPayloadHandlers::handleAdjustRotation);
        registrar.playToServer(FinishRotateSubLevelPayload.TYPE, FinishRotateSubLevelPayload.STREAM_CODEC, StructureTransformPayloadHandlers::handleFinishRotation);
        registrar.playToServer(FinishSimpleWeldPayload.TYPE, FinishSimpleWeldPayload.STREAM_CODEC, WeldPayloadHandlers::handleSimpleWeld);
        registrar.playToServer(DisconnectSubLevelPayload.TYPE, DisconnectSubLevelPayload.STREAM_CODEC, ConstraintToolPayloadHandlers::handleDisconnect);
        registrar.playToServer(ToggleSubLevelCollisionPayload.TYPE, ToggleSubLevelCollisionPayload.STREAM_CODEC, ConstraintToolPayloadHandlers::handleToggleCollision);
        registrar.playToServer(DeleteSubLevelPayload.TYPE, DeleteSubLevelPayload.STREAM_CODEC, StructureDeletePayloadHandler::handle);
        registrar.playToServer(QueryVehicleActionPayload.TYPE, QueryVehicleActionPayload.STREAM_CODEC, VehicleQueryPayloadHandlers::handleAction);
        registrar.playToServer(RequestQueryVehiclesPayload.TYPE, RequestQueryVehiclesPayload.STREAM_CODEC, VehicleQueryPayloadHandlers::handleQuery);
        registrar.playToServer(RequestQueryVehiclePreviewPayload.TYPE, RequestQueryVehiclePreviewPayload.STREAM_CODEC, VehicleQueryPayloadHandlers::handlePreview);
        registrar.playToServer(MagneticGunStartPayload.TYPE, MagneticGunStartPayload.STREAM_CODEC, MagneticGunPayloadHandlers::handleStart);
        registrar.playToServer(MagneticGunStopPayload.TYPE, MagneticGunStopPayload.STREAM_CODEC, MagneticGunPayloadHandlers::handleStop);
        registrar.playToServer(MagneticGunAdjustDistancePayload.TYPE, MagneticGunAdjustDistancePayload.STREAM_CODEC, MagneticGunPayloadHandlers::handleAdjustDistance);
        registrar.playToServer(MagneticGunRotatePayload.TYPE, MagneticGunRotatePayload.STREAM_CODEC, MagneticGunPayloadHandlers::handleRotate);
        registrar.playToServer(MagneticGunPrecisionPayload.TYPE, MagneticGunPrecisionPayload.STREAM_CODEC, MagneticGunPayloadHandlers::handlePrecision);
        registrar.playToServer(MagneticGunLaunchPayload.TYPE, MagneticGunLaunchPayload.STREAM_CODEC, MagneticGunPayloadHandlers::handleLaunch);
        registrar.playToServer(MagneticGunFreezePayload.TYPE, MagneticGunFreezePayload.STREAM_CODEC, MagneticGunPayloadHandlers::handleFreeze);
        registrar.playToServer(RequestPortableStructurePrinterStatePayload.TYPE, RequestPortableStructurePrinterStatePayload.STREAM_CODEC, PortableStructurePrinterPayloadHandlers::handleStateRequest);
        registrar.playToServer(SelectPortableStructurePrinterBlueprintPayload.TYPE, SelectPortableStructurePrinterBlueprintPayload.STREAM_CODEC, PortableStructurePrinterPayloadHandlers::handleSelect);
        registrar.playToServer(PrintPortableStructurePrinterPayload.TYPE, PrintPortableStructurePrinterPayload.STREAM_CODEC, PortableStructurePrinterPayloadHandlers::handlePrint);
        registrar.playToServer(UsePortableStructurePrinterChecklistSlotPayload.TYPE, UsePortableStructurePrinterChecklistSlotPayload.STREAM_CODEC, PortableStructurePrinterPayloadHandlers::handleChecklistSlot);
        registrar.playToClient(SyncLocalBlueprintPayload.TYPE, SyncLocalBlueprintPayload.STREAM_CODEC, ClientPayloadHandlers::handleSyncLocalBlueprint);
        registrar.playToClient(SyncConstraintVisualsPayload.TYPE, SyncConstraintVisualsPayload.STREAM_CODEC, ConstraintVisualPayloadHandler::handleSync);
        registrar.playToClient(SyncQueryVehiclesPayload.TYPE, SyncQueryVehiclesPayload.STREAM_CODEC, ClientPayloadHandlers::handleSyncQueryVehicles);
        registrar.playToClient(SyncQueryVehiclePreviewPayload.TYPE, SyncQueryVehiclePreviewPayload.STREAM_CODEC, ClientPayloadHandlers::handleSyncQueryVehiclePreview);
        registrar.playToClient(OpenPortableStructurePrinterPayload.TYPE, OpenPortableStructurePrinterPayload.STREAM_CODEC, ClientPayloadHandlers::handleOpenPortableStructurePrinter);
        registrar.playToClient(SyncPortableStructurePrinterStatePayload.TYPE, SyncPortableStructurePrinterStatePayload.STREAM_CODEC, ClientPayloadHandlers::handleSyncPortableStructurePrinterState);
        registrar.playToClient(SpawnPortableStructurePrinterEffectPayload.TYPE, SpawnPortableStructurePrinterEffectPayload.STREAM_CODEC, ClientPayloadHandlers::handleSpawnPortableStructurePrinterEffect);
        registrar.playToClient(CompletePortableStructurePrinterEffectPayload.TYPE, CompletePortableStructurePrinterEffectPayload.STREAM_CODEC, ClientPayloadHandlers::handleCompletePortableStructurePrinterEffect);
        registrar.playToClient(MagneticGunStartResultPayload.TYPE, MagneticGunStartResultPayload.STREAM_CODEC, ClientPayloadHandlers::handleMagneticGunStartResult);
    }

}
