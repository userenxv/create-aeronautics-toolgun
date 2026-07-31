package com.enxv.aeronauticsstructuretool.compat.mianbao;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashMap;
import java.util.Map;

public final class MianbaoModernWarfareBlueprintCompat {
    private static final String MOD_ID = "mianbaos_modernwarfare";
    private static final Map<String, Integer> INITIAL_TICK_DELAYS = createInitialTickDelays();

    private MianbaoModernWarfareBlueprintCompat() {
    }

    public static void restoreRecurringTicks(ServerLevel level, ServerSubLevel subLevel) {
        if (level == null || subLevel == null || subLevel.getPlot() == null) {
            return;
        }
        int restored = 0;
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            LevelChunkSection[] sections = chunk.getSections();
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
                for (int localY = 0; localY < 16; localY++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int localX = 0; localX < 16; localX++) {
                            BlockState state = section.getBlockState(localX, localY, localZ);
                            Integer delay = initialTickDelay(state);
                            if (delay == null) {
                                continue;
                            }
                            BlockPos pos = new BlockPos(baseX + localX, baseY + localY, baseZ + localZ);
                            Block block = state.getBlock();
                            if (!level.getBlockTicks().hasScheduledTick(pos, block)) {
                                level.scheduleTick(pos, block, delay);
                                restored++;
                            }
                        }
                    }
                }
            }
        }
        if (restored > 0) {
            AeronauticsStructureToolMod.LOGGER.info(
                    "Restored {} missing mianbao ModernWarfare recurring block ticks in sublevel {}",
                    restored,
                    subLevel.getUniqueId()
            );
        }
    }

    static Integer initialTickDelay(BlockState state) {
        if (state == null || state.isAir()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return initialTickDelay(id);
    }

    static Integer initialTickDelay(ResourceLocation id) {
        if (id == null || !MOD_ID.equals(id.getNamespace())) {
            return null;
        }
        return INITIAL_TICK_DELAYS.get(id.getPath());
    }

    static int supportedRecurringBlockCount() {
        return INITIAL_TICK_DELAYS.size();
    }

    private static Map<String, Integer> createInitialTickDelays() {
        Map<String, Integer> delays = new HashMap<>();
        add(delays, 1,
                "aps_1roundson", "aps_1roundsonshase", "aps_2roundson", "aps_2roundsonshase",
                "enginesmokelauncheron", "flamelauncher", "headattackmine", "integratedredstoneterminal",
                "laserpressingsystemonlvse", "laserpressingsystemonshase", "medicalkitsbenti",
                "medicalkitsbenti_1", "movementtracker", "nuke", "portable_aps",
                "portableantimissileradaron", "portableantimissilesystemon", "radar",
                "rotarycannonbreech", "second_aps_1roundson", "second_aps_1roundsonshase",
                "second_aps_2roundson", "second_aps_2roundsonshase", "second_aps_3roundson",
                "second_aps_3roundsonshase", "second_aps_4roundson", "second_aps_4roundsonshase",
                "terminalboardredstone", "terminalreceiverredstone", "third_aps_1roundson",
                "third_aps_1roundsonshase", "third_aps_2roundson", "third_aps_2roundsonshase",
                "trailsmokerleftdown", "trailsmokerleftup", "trailsmokerrightdown", "trailsmokerrightup"
        );
        add(delays, 2, "laserpointerblock", "laserpointerblockshase");
        add(delays, 3, "autocannonbreech");
        add(delays, 5, "grenadecannon", "mortar");
        add(delays, 10, "antimanmine", "countermeasurelauncher_2");
        add(delays, 16, "heli_enginjukebox");
        add(delays, 20,
                "antitankmine", "enginesmokelauncheroff", "firemine", "gasmine",
                "portableantiairsystem", "redstoneair", "smokelaunchersixing",
                "smokelaunchersixing_h_2", "smokelaunchersixing_h_6", "smokelaunchersixingleft",
                "smokelaunchersixingleft_h_2", "smokelaunchersixingleft_h_6",
                "smokelaunchersixingright", "smokelaunchersixingright_h_2",
                "smokelaunchersixingright_h_6", "tnt"
        );
        return Map.copyOf(delays);
    }

    private static void add(Map<String, Integer> delays, int delay, String... blockIds) {
        for (String blockId : blockIds) {
            Integer previous = delays.put(blockId, delay);
            if (previous != null) {
                throw new IllegalStateException("Duplicate ModernWarfare recurring block id: " + blockId);
            }
        }
    }
}
