package com.enxv.aeronauticsstructuretool.vehicle.container;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class PortableContainerRepairIdTracker {
    private static final String SAVE_ID = "portable_structure_repair_ids";
    private static final String ENTRIES_TAG = "Entries";
    private static final String ROOT_STRUCTURE_ID_TAG = "RootStructureId";
    private static final String REPAIR_VEHICLE_ID_TAG = "RepairVehicleId";

    private PortableContainerRepairIdTracker() {
    }

    static void remember(ServerLevel level, UUID rootStructureId, String repairVehicleId) {
        if (rootStructureId == null || repairVehicleId == null || repairVehicleId.isBlank()) {
            return;
        }
        RepairIdSavedData data = data(level);
        String previous = data.repairIds.put(rootStructureId, repairVehicleId);
        if (!repairVehicleId.equals(previous)) {
            data.setDirty();
        }
    }

    static String consume(ServerLevel level, UUID rootStructureId) {
        if (rootStructureId == null) {
            return "";
        }
        RepairIdSavedData data = data(level);
        String value = data.repairIds.remove(rootStructureId);
        if (value == null) {
            return "";
        }
        data.setDirty();
        return value;
    }

    private static RepairIdSavedData data(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        ServerLevel storageLevel = overworld == null ? level : overworld;
        return storageLevel.getDataStorage().computeIfAbsent(
                RepairIdSavedData.factory(),
                SAVE_ID
        );
    }

    private static final class RepairIdSavedData extends SavedData {
        private final Map<UUID, String> repairIds = new LinkedHashMap<>();

        static Factory<RepairIdSavedData> factory() {
            return new Factory<>(RepairIdSavedData::new, RepairIdSavedData::load);
        }

        static RepairIdSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
            RepairIdSavedData data = new RepairIdSavedData();
            ListTag entries = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                if (!entry.hasUUID(ROOT_STRUCTURE_ID_TAG)) {
                    AeronauticsStructureToolMod.LOGGER.warn(
                            "Ignoring portable-container repair ID entry {} because {} is missing",
                            i,
                            ROOT_STRUCTURE_ID_TAG
                    );
                    continue;
                }
                if (!entry.contains(REPAIR_VEHICLE_ID_TAG, Tag.TAG_STRING)) {
                    AeronauticsStructureToolMod.LOGGER.warn(
                            "Ignoring portable-container repair ID entry {} because {} is not a string",
                            i,
                            REPAIR_VEHICLE_ID_TAG
                    );
                    continue;
                }
                String repairVehicleId = entry.getString(REPAIR_VEHICLE_ID_TAG);
                if (repairVehicleId.isBlank()) {
                    AeronauticsStructureToolMod.LOGGER.warn(
                            "Ignoring portable-container repair ID entry {} because its vehicle ID is blank",
                            i
                    );
                    continue;
                }
                data.repairIds.put(entry.getUUID(ROOT_STRUCTURE_ID_TAG), repairVehicleId);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag entries = new ListTag();
            for (Map.Entry<UUID, String> entry : this.repairIds.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    AeronauticsStructureToolMod.LOGGER.warn(
                            "Portable-container repair ID {} has no value and will not be saved",
                            entry.getKey()
                    );
                    continue;
                }
                CompoundTag entryTag = new CompoundTag();
                entryTag.putUUID(ROOT_STRUCTURE_ID_TAG, entry.getKey());
                entryTag.putString(REPAIR_VEHICLE_ID_TAG, entry.getValue());
                entries.add(entryTag);
            }
            tag.put(ENTRIES_TAG, entries);
            return tag;
        }
    }
}
