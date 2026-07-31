package com.enxv.aeronauticsstructuretool.blueprint.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Set;

public final class BlueprintBlockEntitySanitizer {
    private static final int MAX_DEPTH = 64;
    private static final Set<String> SIGN_BLOCK_ENTITY_IDS = Set.of(
            "minecraft:sign",
            "minecraft:hanging_sign"
    );
    private static final String POWERED_ENGINE_SHAFT_BLOCK_ENTITY_ID =
            "createdieselgenerators:powered_engine_shaft_block_entity";

    private BlueprintBlockEntitySanitizer() {
    }

    public static Result sanitize(Tag root) {
        MutableResult result = new MutableResult();
        sanitizeTag(root, result, 0);
        return result.freeze();
    }

    private static void sanitizeTag(Tag tag, MutableResult result, int depth) {
        if (tag == null || depth > MAX_DEPTH) {
            return;
        }
        if (tag instanceof CompoundTag compound) {
            String blockEntityId = compound.getString("id");
            if (SIGN_BLOCK_ENTITY_IDS.contains(blockEntityId)) {
                sanitizeSignBlockEntity(compound, result);
            } else if (POWERED_ENGINE_SHAFT_BLOCK_ENTITY_ID.equals(blockEntityId)) {
                sanitizePoweredEngineShaft(compound, result);
            }
            for (String key : List.copyOf(compound.getAllKeys())) {
                sanitizeTag(compound.get(key), result, depth + 1);
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                sanitizeTag(list.get(i), result, depth + 1);
            }
        }
    }

    private static void sanitizeSignBlockEntity(CompoundTag blockEntityTag, MutableResult result) {
        sanitizeSignText(blockEntityTag.getCompound("front_text"), result);
        sanitizeSignText(blockEntityTag.getCompound("back_text"), result);
        sanitizeLegacySignLines(blockEntityTag, result);
    }

    private static void sanitizeSignText(CompoundTag textTag, MutableResult result) {
        sanitizeMessageList(textTag, "messages", result);
        sanitizeMessageList(textTag, "filtered_messages", result);
    }

    private static void sanitizeMessageList(CompoundTag textTag, String key, MutableResult result) {
        if (!textTag.contains(key, Tag.TAG_LIST)) {
            return;
        }
        ListTag messages = textTag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < messages.size(); i++) {
            String sanitized = sanitizeComponentJson(messages.getString(i), result);
            messages.set(i, StringTag.valueOf(sanitized));
        }
    }

    private static void sanitizeLegacySignLines(CompoundTag blockEntityTag, MutableResult result) {
        for (int line = 1; line <= 4; line++) {
            sanitizeStringField(blockEntityTag, "Text" + line, result);
            sanitizeStringField(blockEntityTag, "FilteredText" + line, result);
        }
    }

    private static void sanitizeStringField(CompoundTag tag, String key, MutableResult result) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return;
        }
        tag.putString(key, sanitizeComponentJson(tag.getString(key), result));
    }

    private static String sanitizeComponentJson(String rawJson, MutableResult result) {
        JsonElement component;
        try {
            component = JsonParser.parseString(rawJson);
        } catch (RuntimeException exception) {
            result.invalidSignMessagesCleared++;
            return new JsonPrimitive("").toString();
        }
        int removed = removeClickEvents(component);
        if (removed == 0) {
            return rawJson;
        }
        result.signClickEventsRemoved += removed;
        return component.toString();
    }

    private static int removeClickEvents(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
            return 0;
        }
        if (element instanceof JsonArray array) {
            int removed = 0;
            for (JsonElement child : array) {
                removed += removeClickEvents(child);
            }
            return removed;
        }
        JsonObject object = element.getAsJsonObject();
        int removed = 0;
        if (object.remove("clickEvent") != null) {
            removed++;
        }
        if (object.remove("click_event") != null) {
            removed++;
        }
        for (JsonElement child : List.copyOf(object.asMap().values())) {
            removed += removeClickEvents(child);
        }
        return removed;
    }

    private static void sanitizePoweredEngineShaft(CompoundTag blockEntityTag, MutableResult result) {
        ListTag engines = blockEntityTag.getList("Engines", Tag.TAG_COMPOUND);
        boolean changed = !engines.isEmpty()
                || blockEntityTag.getFloat("GeneratedSpeed") != 0.0F
                || blockEntityTag.getInt("Direction") != 0
                || blockEntityTag.contains("Warmup");
        blockEntityTag.put("Engines", new ListTag());
        blockEntityTag.putFloat("GeneratedSpeed", 0.0F);
        blockEntityTag.putInt("Direction", 0);
        blockEntityTag.remove("Warmup");
        if (changed) {
            result.dieselShaftsReset++;
            result.dieselEngineReferencesRemoved += engines.size();
        }
    }

    public record Result(
            int signClickEventsRemoved,
            int invalidSignMessagesCleared,
            int dieselShaftsReset,
            int dieselEngineReferencesRemoved
    ) {
        public boolean changed() {
            return this.signClickEventsRemoved > 0
                    || this.invalidSignMessagesCleared > 0
                    || this.dieselShaftsReset > 0;
        }
    }

    private static final class MutableResult {
        private int signClickEventsRemoved;
        private int invalidSignMessagesCleared;
        private int dieselShaftsReset;
        private int dieselEngineReferencesRemoved;

        private Result freeze() {
            return new Result(
                    this.signClickEventsRemoved,
                    this.invalidSignMessagesCleared,
                    this.dieselShaftsReset,
                    this.dieselEngineReferencesRemoved
            );
        }
    }
}
