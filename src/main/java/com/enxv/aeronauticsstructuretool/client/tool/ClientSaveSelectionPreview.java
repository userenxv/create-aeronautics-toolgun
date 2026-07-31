package com.enxv.aeronauticsstructuretool.client.tool;

import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.StructureToolItem;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class ClientSaveSelectionPreview {
    private ClientSaveSelectionPreview() {
    }

    public static Selection resolve(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return null;
        }
        HitResult target = minecraft.player.pick(StructureToolItem.MAX_USE_DISTANCE, 0.0F, false);
        if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        SubLevel root = Sable.HELPER.getContaining(minecraft.level, hit.getBlockPos());
        if (root == null) {
            return null;
        }
        return resolve(minecraft.level, root, ClientToolState.getConnectedSublevelProximity());
    }

    public static Selection resolve(Level level, SubLevel root, double maximumNeighborGap) {
        if (level == null || root == null || !Double.isFinite(maximumNeighborGap)
                || maximumNeighborGap < 0.0D) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        Set<SubLevel> selected = new LinkedHashSet<>();
        Queue<SubLevel> queue = new ArrayDeque<>();
        addChain(root, selected, queue);
        while (!queue.isEmpty()) {
            SubLevel current = queue.remove();
            if (current.boundingBox() == null) {
                continue;
            }
            BoundingBox3d search = new BoundingBox3d(current.boundingBox()).expand(maximumNeighborGap);
            Iterable<SubLevel> neighbors = container.queryIntersecting(search);
            for (SubLevel neighbor : neighbors) {
                add(neighbor, selected, queue);
            }
        }

        BoundingBox3d bounds = null;
        for (SubLevel subLevel : selected) {
            if (subLevel.boundingBox() == null) {
                continue;
            }
            if (bounds == null) {
                bounds = new BoundingBox3d(subLevel.boundingBox());
            } else {
                bounds.expandTo(subLevel.boundingBox());
            }
        }
        if (bounds == null) {
            return null;
        }
        BoundingBox3d rangeBounds = new BoundingBox3d(bounds).expand(maximumNeighborGap);
        return new Selection(List.copyOf(selected), bounds, rangeBounds, maximumNeighborGap);
    }

    private static void addChain(SubLevel root, Set<SubLevel> selected, Queue<SubLevel> queue) {
        add(root, selected, queue);
        for (SubLevel connected : SubLevelHelper.getConnectedChain(root)) {
            add(connected, selected, queue);
        }
    }

    private static void add(SubLevel subLevel, Set<SubLevel> selected, Queue<SubLevel> queue) {
        if (subLevel != null && !subLevel.isRemoved() && selected.add(subLevel)) {
            queue.add(subLevel);
        }
    }

    public record Selection(
            List<SubLevel> subLevels,
            BoundingBox3d structureBounds,
            BoundingBox3d rangeBounds,
            double maximumNeighborGap
    ) {
        public Selection {
            subLevels = List.copyOf(new ArrayList<>(subLevels));
            structureBounds = new BoundingBox3d(structureBounds);
            rangeBounds = new BoundingBox3d(rangeBounds);
        }
    }
}
