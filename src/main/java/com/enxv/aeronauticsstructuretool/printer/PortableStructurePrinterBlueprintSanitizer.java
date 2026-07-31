package com.enxv.aeronauticsstructuretool.printer;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.ToolgunConstraintTracker;
import com.enxv.aeronauticsstructuretool.blueprint.security.BlueprintBlockEntitySanitizer;
import com.enxv.aeronauticsstructuretool.blueprint.codec.BlueprintArchiveCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;

final class PortableStructurePrinterBlueprintSanitizer {
    private static final String NO_COLLISION_TAG = "AstDisableStructureCollision";

    private PortableStructurePrinterBlueprintSanitizer() {
    }

    static byte[] sanitizeForPortablePrinter(byte[] blueprintBytes, boolean stripToolgunLinks) throws IOException {
        if (blueprintBytes == null || blueprintBytes.length == 0) {
            return blueprintBytes;
        }

        CompoundTag root = BlueprintArchiveCodec.decode(blueprintBytes);
        if (root == null) {
            throw new IOException("invalid blueprint");
        }

        BlueprintBlockEntitySanitizer.Result securityResult = BlueprintBlockEntitySanitizer.sanitize(root);
        if (stripToolgunLinks) {
            root.remove(ToolgunConstraintTracker.constraintsTagName());
            stripNoCollisionFlag(root);
        }

        if (securityResult.changed()) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Sanitized portable-printer blueprint: signClickEventsRemoved={}, invalidSignMessagesCleared={}, dieselShaftsReset={}, dieselEngineReferencesRemoved={}",
                    securityResult.signClickEventsRemoved(),
                    securityResult.invalidSignMessagesCleared(),
                    securityResult.dieselShaftsReset(),
                    securityResult.dieselEngineReferencesRemoved()
            );
        }

        return BlueprintArchiveCodec.encode(root);
    }

    private static void stripNoCollisionFlag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            compound.remove(NO_COLLISION_TAG);
            for (String key : java.util.List.copyOf(compound.getAllKeys())) {
                stripNoCollisionFlag(compound.get(key));
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                stripNoCollisionFlag(list.get(i));
            }
        }
    }
}
