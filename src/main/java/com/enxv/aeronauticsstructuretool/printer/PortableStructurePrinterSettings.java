package com.enxv.aeronauticsstructuretool.printer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class PortableStructurePrinterSettings {
    private static final String SAVE_ID = "create_aeronautics_toolgun_printer_settings";
    private static final String STRIP_LINKS_TAG = "StripPortablePrinterToolgunLinks";
    private static final String PORTABLE_CONTAINER_CRAFTING_TAG = "PortableStructureContainerCraftingEnabled";

    private PortableStructurePrinterSettings() {
    }

    static boolean shouldStripToolgunLinks(ServerLevel level) {
        return data(level).stripToolgunLinksOnPrint;
    }

    static boolean setStripToolgunLinks(ServerLevel level, boolean enabled) {
        PrinterSettingsSavedData data = data(level);
        data.stripToolgunLinksOnPrint = enabled;
        data.setDirty();
        return data.stripToolgunLinksOnPrint;
    }

    public static boolean canCraftPortableStructureContainer(ServerLevel level) {
        return data(level).portableStructureContainerCraftingEnabled;
    }

    static boolean setPortableStructureContainerCraftingEnabled(ServerLevel level, boolean enabled) {
        PrinterSettingsSavedData data = data(level);
        data.portableStructureContainerCraftingEnabled = enabled;
        data.setDirty();
        return data.portableStructureContainerCraftingEnabled;
    }

    private static PrinterSettingsSavedData data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PrinterSettingsSavedData::new, PrinterSettingsSavedData::load),
                SAVE_ID
        );
    }

    private static final class PrinterSettingsSavedData extends SavedData {
        private boolean stripToolgunLinksOnPrint;
        private boolean portableStructureContainerCraftingEnabled;

        private PrinterSettingsSavedData() {
            this.stripToolgunLinksOnPrint = false;
            this.portableStructureContainerCraftingEnabled = true;
        }

        private static PrinterSettingsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
            PrinterSettingsSavedData data = new PrinterSettingsSavedData();
            data.stripToolgunLinksOnPrint = tag.getBoolean(STRIP_LINKS_TAG);
            if (tag.contains(PORTABLE_CONTAINER_CRAFTING_TAG)) {
                data.portableStructureContainerCraftingEnabled = tag.getBoolean(PORTABLE_CONTAINER_CRAFTING_TAG);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean(STRIP_LINKS_TAG, this.stripToolgunLinksOnPrint);
            tag.putBoolean(PORTABLE_CONTAINER_CRAFTING_TAG, this.portableStructureContainerCraftingEnabled);
            return tag;
        }
    }
}
