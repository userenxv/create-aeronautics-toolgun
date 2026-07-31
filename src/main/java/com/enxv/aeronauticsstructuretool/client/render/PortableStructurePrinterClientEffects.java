package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.SpawnPortableStructurePrinterEffectPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class PortableStructurePrinterClientEffects {
    private static final Map<BlockPos, List<PrintEffect>> ACTIVE_EFFECTS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, List<PlacedPreview>> PLACED_PREVIEWS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, CompletionBurst> COMPLETION_BURSTS = new ConcurrentHashMap<>();
    private static final Vector3f TRAIL_COLOR = new Vector3f(1.0F, 0.87F, 0.25F);
    private static final long DURATION_MILLIS = 700L;
    private static final long LANDING_MILLIS = 280L;
    private static final long COMPLETION_MILLIS = 1400L;
    private static final long COMPLETION_FLASH_MILLIS = 220L;
    private static final long COMPLETION_PULL_MILLIS = 520L;

    private PortableStructurePrinterClientEffects() {
    }

    public static void spawn(SpawnPortableStructurePrinterEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        Vec3 start = new Vec3(payload.printerPos().getX() + 0.5D, payload.printerPos().getY() + 0.95D, payload.printerPos().getZ() + 0.5D);
        Vec3 target = new Vec3(payload.targetX(), payload.targetY(), payload.targetZ());
        Quaterniond orientation = new Quaterniond(payload.orientationX(), payload.orientationY(), payload.orientationZ(), payload.orientationW());
        Random random = new Random(payload.seed());
        Vec3 midpoint = start.add(target).scale(0.5D);
        double sideX = (random.nextDouble() - 0.5D) * 1.25D;
        double sideY = 0.45D + random.nextDouble() * 0.8D;
        double sideZ = (random.nextDouble() - 0.5D) * 1.25D;
        Vec3 control = midpoint.add(sideX, sideY, sideZ);
        ACTIVE_EFFECTS.computeIfAbsent(payload.printerPos(), ignored -> new ArrayList<>())
                .add(new PrintEffect(start, control, target, orientation, now, now + DURATION_MILLIS, now + DURATION_MILLIS + LANDING_MILLIS, random.nextFloat() * 360.0F));
        if (minecraft.level != null) {
            minecraft.level.addParticle(new DustParticleOptions(TRAIL_COLOR, 1.15F), start.x, start.y, start.z, 0.0D, 0.0D, 0.0D);
        }
    }

    static void render(BlockPos printerPos, PoseStack poseStack, MultiBufferSource buffer) {
        List<PrintEffect> effects = ACTIVE_EFFECTS.get(printerPos);
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        if (effects != null && !effects.isEmpty()) {
            Iterator<PrintEffect> iterator = effects.iterator();
            while (iterator.hasNext()) {
                PrintEffect effect = iterator.next();
                if (now >= effect.landingEndMillis) {
                    PLACED_PREVIEWS.computeIfAbsent(printerPos, ignored -> new ArrayList<>())
                            .add(new PlacedPreview(effect.target, effect.orientation));
                    iterator.remove();
                    continue;
                }
                if (now < effect.endMillis) {
                    float progress = Mth.clamp((float) (now - effect.startMillis) / (float) (effect.endMillis - effect.startMillis), 0.0F, 1.0F);
                    Vec3 current = quadraticBezier(effect.start, effect.control, effect.target, progress);
                    Vec3 previous = quadraticBezier(effect.start, effect.control, effect.target, Math.max(0.0F, progress - 0.08F));
                    if (minecraft.level != null && now % 2L == 0L) {
                        minecraft.level.addParticle(new DustParticleOptions(TRAIL_COLOR, 0.85F), previous.x, previous.y, previous.z, 0.0D, 0.0D, 0.0D);
                    }
                    poseStack.pushPose();
                    poseStack.translate(
                            current.x - printerPos.getX(),
                            current.y - printerPos.getY(),
                            current.z - printerPos.getZ()
                    );
                    poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(effect.spinBase + progress * 420.0F));
                    poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(effect.spinBase * 0.5F + progress * 280.0F));
                    drawCube(consumer, poseStack.last().pose(), 0.095F, 255, 255, 255, 144);
                    poseStack.popPose();
                } else {
                    float landingProgress = Mth.clamp((float) (now - effect.endMillis) / (float) (effect.landingEndMillis - effect.endMillis), 0.0F, 1.0F);
                    float alpha = 1.0F - landingProgress;
                    renderLandingPreview(poseStack, consumer, printerPos, effect.target, effect.orientation, alpha);
                }
            }
        }
        List<PlacedPreview> placedPreviews = PLACED_PREVIEWS.get(printerPos);
        if (placedPreviews != null) {
            for (PlacedPreview preview : placedPreviews) {
                renderLandingPreview(poseStack, consumer, printerPos, preview.target, preview.orientation, 1.0F);
            }
        }
        CompletionBurst completionBurst = COMPLETION_BURSTS.get(printerPos);
        if (completionBurst != null) {
            renderCompletionBurst(printerPos, poseStack, consumer, completionBurst, now);
            if (now >= completionBurst.endMillis) {
                COMPLETION_BURSTS.remove(printerPos);
            }
        }
        if (effects != null && effects.isEmpty()) {
            ACTIVE_EFFECTS.remove(printerPos);
        }
    }

    public static void complete(BlockPos printerPos) {
        long now = System.currentTimeMillis();
        List<PlacedPreview> previews = new ArrayList<>();
        List<PlacedPreview> landed = PLACED_PREVIEWS.remove(printerPos);
        if (landed != null) {
            previews.addAll(landed);
        }
        List<PrintEffect> active = ACTIVE_EFFECTS.remove(printerPos);
        if (active != null) {
            for (PrintEffect effect : active) {
                previews.add(new PlacedPreview(effect.target, effect.orientation));
            }
        }
        if (previews.isEmpty()) {
            COMPLETION_BURSTS.remove(printerPos);
            return;
        }

        Vec3 center = Vec3.ZERO;
        for (PlacedPreview preview : previews) {
            center = center.add(preview.target);
        }
        center = center.scale(1.0D / previews.size());

        Random random = new Random(printerPos.asLong() ^ now);
        List<BurstParticle> particles = new ArrayList<>();
        for (PlacedPreview preview : previews) {
            for (int i = 0; i < 4; i++) {
                Vec3 start = preview.target.add(
                        (random.nextDouble() - 0.5D) * 1.1D,
                        (random.nextDouble() - 0.5D) * 1.1D,
                        (random.nextDouble() - 0.5D) * 1.1D
                );
                Vec3 burstDirection = preview.target.subtract(center);
                if (burstDirection.lengthSqr() < 1.0E-5D) {
                    burstDirection = new Vec3(
                            random.nextDouble() - 0.5D,
                            random.nextDouble() - 0.15D,
                            random.nextDouble() - 0.5D
                    );
                }
                burstDirection = burstDirection.normalize().scale(0.55D + random.nextDouble() * 0.75D);
                particles.add(new BurstParticle(
                        start,
                        preview.target,
                        burstDirection,
                        0.035F + random.nextFloat() * 0.03F,
                        random.nextFloat() * 360.0F,
                        random.nextFloat() * 13.0F
                ));
            }
        }
        COMPLETION_BURSTS.put(printerPos, new CompletionBurst(previews, particles, now, now + COMPLETION_MILLIS));
    }

    public static void clear(BlockPos printerPos) {
        ACTIVE_EFFECTS.remove(printerPos);
        PLACED_PREVIEWS.remove(printerPos);
        COMPLETION_BURSTS.remove(printerPos);
    }

    private static Vec3 quadraticBezier(Vec3 start, Vec3 control, Vec3 end, float t) {
        double inv = 1.0D - t;
        return start.scale(inv * inv)
                .add(control.scale(2.0D * inv * t))
                .add(end.scale(t * t));
    }

    private static void renderLandingPreview(PoseStack poseStack, VertexConsumer consumer, BlockPos printerPos, Vec3 target, Quaterniond orientation, float alpha) {
        renderLandingPreviewColored(poseStack, consumer, printerPos, target, orientation, alpha, 1.0F, 0.88F, 0.24F);
    }

    private static void renderLandingPreviewColored(PoseStack poseStack, VertexConsumer consumer, BlockPos printerPos, Vec3 target, Quaterniond orientation, float alpha, float red, float green, float blue) {
        float fillAlpha = alpha * 0.28F;
        Vector3d center = new Vector3d(
                target.x - printerPos.getX(),
                target.y - printerPos.getY(),
                target.z - printerPos.getZ()
        );
        Vector3d[] corners = createPreviewCubeCorners(center, orientation, 0.48D);
        fillPreviewCube(poseStack.last().pose(), consumer, corners, red, green, blue, fillAlpha);
        for (int[] edge : PREVIEW_EDGES) {
            renderPreviewLine(poseStack, consumer, corners[edge[0]], corners[edge[1]], red, green, blue, alpha);
        }
    }

    private static final int[][] PREVIEW_EDGES = new int[][]{
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private static Vector3d[] createPreviewCubeCorners(Vector3d center, Quaterniond orientation, double half) {
        Quaterniond safeOrientation = orientation == null ? new Quaterniond() : orientation;
        Vector3d[] corners = new Vector3d[]{
                new Vector3d(-half, -half, -half),
                new Vector3d(half, -half, -half),
                new Vector3d(half, -half, half),
                new Vector3d(-half, -half, half),
                new Vector3d(-half, half, -half),
                new Vector3d(half, half, -half),
                new Vector3d(half, half, half),
                new Vector3d(-half, half, half)
        };
        for (Vector3d corner : corners) {
            safeOrientation.transform(corner);
            corner.add(center);
        }
        return corners;
    }

    private static void fillPreviewCube(Matrix4f matrix, VertexConsumer consumer, Vector3d[] corners, float red, float green, float blue, float alpha) {
        fillQuad(matrix, consumer, corners[0], corners[1], corners[2], corners[3], red, green, blue, alpha);
        fillQuad(matrix, consumer, corners[4], corners[5], corners[6], corners[7], red, green, blue, alpha);
        fillQuad(matrix, consumer, corners[0], corners[1], corners[5], corners[4], red, green, blue, alpha);
        fillQuad(matrix, consumer, corners[1], corners[2], corners[6], corners[5], red, green, blue, alpha);
        fillQuad(matrix, consumer, corners[2], corners[3], corners[7], corners[6], red, green, blue, alpha);
        fillQuad(matrix, consumer, corners[3], corners[0], corners[4], corners[7], red, green, blue, alpha);
    }

    private static void fillQuad(Matrix4f matrix, VertexConsumer consumer, Vector3d a, Vector3d b, Vector3d c, Vector3d d, float red, float green, float blue, float alpha) {
        int ir = Math.round(red * 255.0F);
        int ig = Math.round(green * 255.0F);
        int ib = Math.round(blue * 255.0F);
        int ia = Math.round(alpha * 255.0F);
        addVertex(consumer, matrix, new Vec3(a.x, a.y, a.z), ir, ig, ib, ia);
        addVertex(consumer, matrix, new Vec3(b.x, b.y, b.z), ir, ig, ib, ia);
        addVertex(consumer, matrix, new Vec3(c.x, c.y, c.z), ir, ig, ib, ia);
        addVertex(consumer, matrix, new Vec3(d.x, d.y, d.z), ir, ig, ib, ia);
    }

    private static void renderPreviewLine(PoseStack poseStack, VertexConsumer consumer, Vector3d start, Vector3d end, float red, float green, float blue, float alpha) {
        Vector3d direction = new Vector3d(end).sub(start);
        if (direction.lengthSquared() <= 1.0E-8D) {
            return;
        }
        direction.normalize();
        Vector3d upReference = Math.abs(direction.y) < 0.92D ? new Vector3d(0.0D, 1.0D, 0.0D) : new Vector3d(1.0D, 0.0D, 0.0D);
        Vector3d side = direction.cross(upReference, new Vector3d());
        if (side.lengthSquared() <= 1.0E-8D) {
            side = direction.cross(new Vector3d(0.0D, 0.0D, 1.0D), new Vector3d());
        }
        if (side.lengthSquared() <= 1.0E-8D) {
            return;
        }
        side.normalize(0.012D);
        Vector3d up = new Vector3d(side).cross(direction).normalize(0.012D);
        Vector3d startA = new Vector3d(start).add(side).add(up);
        Vector3d startB = new Vector3d(start).add(side).sub(up);
        Vector3d startC = new Vector3d(start).sub(side).sub(up);
        Vector3d startD = new Vector3d(start).sub(side).add(up);
        Vector3d endA = new Vector3d(end).add(side).add(up);
        Vector3d endB = new Vector3d(end).add(side).sub(up);
        Vector3d endC = new Vector3d(end).sub(side).sub(up);
        Vector3d endD = new Vector3d(end).sub(side).add(up);
        fillQuad(poseStack.last().pose(), consumer, startA, endA, endB, startB, red, green, blue, alpha);
        fillQuad(poseStack.last().pose(), consumer, startB, endB, endC, startC, red, green, blue, alpha);
        fillQuad(poseStack.last().pose(), consumer, startC, endC, endD, startD, red, green, blue, alpha);
        fillQuad(poseStack.last().pose(), consumer, startD, endD, endA, startA, red, green, blue, alpha);
    }

    private static void renderCompletionBurst(BlockPos printerPos, PoseStack poseStack, VertexConsumer consumer, CompletionBurst burst, long now) {
        float elapsed = (float) (now - burst.startMillis);
        float progress = Mth.clamp(elapsed / (float) (burst.endMillis - burst.startMillis), 0.0F, 1.0F);
        float flashProgress = Mth.clamp(elapsed / (float) COMPLETION_FLASH_MILLIS, 0.0F, 1.0F);
        float pullProgress = elapsed <= COMPLETION_PULL_MILLIS
                ? Mth.clamp(elapsed / (float) COMPLETION_PULL_MILLIS, 0.0F, 1.0F)
                : 1.0F;
        float burstProgress = elapsed <= COMPLETION_PULL_MILLIS
                ? 0.0F
                : Mth.clamp((elapsed - COMPLETION_PULL_MILLIS) / (float) (COMPLETION_MILLIS - COMPLETION_PULL_MILLIS), 0.0F, 1.0F);

        for (PlacedPreview preview : burst.previews) {
            float fadeAlpha = 1.0F - Mth.clamp((elapsed - COMPLETION_PULL_MILLIS * 0.55F) / (float) (COMPLETION_MILLIS - COMPLETION_PULL_MILLIS * 0.55F), 0.0F, 1.0F);
            float red = 1.0F;
            float green = Mth.lerp(flashProgress, 0.88F, 1.0F);
            float blue = Mth.lerp(flashProgress, 0.24F, 0.95F);
            renderLandingPreviewColored(poseStack, consumer, printerPos, preview.target, preview.orientation, fadeAlpha, red, green, blue);
        }

        for (BurstParticle particle : burst.particles) {
            Vec3 current = elapsed <= COMPLETION_PULL_MILLIS
                    ? particle.start.lerp(particle.pullTarget, pullProgress)
                    : particle.pullTarget.add(particle.burstVelocity.scale(burstProgress * (1.1D + burstProgress * 0.9D)));
            float flicker = 0.72F + 0.28F * Mth.sin((progress * 20.0F) + particle.flickerOffset);
            float alpha = elapsed <= COMPLETION_PULL_MILLIS
                    ? 0.82F * flicker
                    : (1.0F - burstProgress) * 0.92F * flicker;
            float size = elapsed <= COMPLETION_PULL_MILLIS
                    ? Mth.lerp(pullProgress, particle.size * 1.35F, particle.size)
                    : Mth.lerp(burstProgress, particle.size, particle.size * 0.18F);
            poseStack.pushPose();
            poseStack.translate(
                    current.x - printerPos.getX(),
                    current.y - printerPos.getY(),
                    current.z - printerPos.getZ()
            );
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(particle.rotationBase + progress * 280.0F));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees((particle.rotationBase * 0.4F) + progress * 210.0F));
            drawCube(
                    consumer,
                    poseStack.last().pose(),
                    size,
                    255,
                    255,
                    255,
                    Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F)
            );
            poseStack.popPose();
        }
    }

    private static void drawCube(VertexConsumer consumer, Matrix4f matrix, float halfSize, int red, int green, int blue, int alpha) {
        Vec3 p000 = new Vec3(-halfSize, -halfSize, -halfSize);
        Vec3 p001 = new Vec3(-halfSize, -halfSize, halfSize);
        Vec3 p010 = new Vec3(-halfSize, halfSize, -halfSize);
        Vec3 p011 = new Vec3(-halfSize, halfSize, halfSize);
        Vec3 p100 = new Vec3(halfSize, -halfSize, -halfSize);
        Vec3 p101 = new Vec3(halfSize, -halfSize, halfSize);
        Vec3 p110 = new Vec3(halfSize, halfSize, -halfSize);
        Vec3 p111 = new Vec3(halfSize, halfSize, halfSize);
        drawQuad(consumer, matrix, p001, p101, p111, p011, red, green, blue, alpha);
        drawQuad(consumer, matrix, p100, p000, p010, p110, red, green, blue, alpha);
        drawQuad(consumer, matrix, p000, p001, p011, p010, red, green, blue, alpha);
        drawQuad(consumer, matrix, p101, p100, p110, p111, red, green, blue, alpha);
        drawQuad(consumer, matrix, p010, p011, p111, p110, red, green, blue, alpha);
        drawQuad(consumer, matrix, p000, p100, p101, p001, red, green, blue, alpha);
    }

    private static void drawQuad(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int red, int green, int blue, int alpha) {
        addVertex(consumer, matrix, a, red, green, blue, alpha);
        addVertex(consumer, matrix, b, red, green, blue, alpha);
        addVertex(consumer, matrix, c, red, green, blue, alpha);
        addVertex(consumer, matrix, d, red, green, blue, alpha);
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, int red, int green, int blue, int alpha) {
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).setColor(red, green, blue, alpha);
    }

    private record PrintEffect(Vec3 start, Vec3 control, Vec3 target, Quaterniond orientation, long startMillis, long endMillis, long landingEndMillis, float spinBase) {
    }

    private record PlacedPreview(Vec3 target, Quaterniond orientation) {
    }

    private record CompletionBurst(List<PlacedPreview> previews, List<BurstParticle> particles, long startMillis, long endMillis) {
    }

    private record BurstParticle(Vec3 start, Vec3 pullTarget, Vec3 burstVelocity, float size, float rotationBase, float flickerOffset) {
    }
}
