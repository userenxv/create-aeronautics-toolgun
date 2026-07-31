package com.enxv.aeronauticsstructuretool.blueprint.security;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;

public final class BlueprintSanitizationReporter {
    private BlueprintSanitizationReporter() {
    }

    public static void report(
            String phase,
            String blueprintName,
            BlueprintBlockEntitySanitizer.Result result
    ) {
        if (result == null || !result.changed()) {
            return;
        }
        AeronauticsStructureToolMod.LOGGER.warn(
                "Sanitized blueprint during {}: name='{}', signClickEventsRemoved={}, invalidSignMessagesCleared={}, dieselShaftsReset={}, dieselEngineReferencesRemoved={}",
                phase,
                blueprintName,
                result.signClickEventsRemoved(),
                result.invalidSignMessagesCleared(),
                result.dieselShaftsReset(),
                result.dieselEngineReferencesRemoved()
        );
    }
}
