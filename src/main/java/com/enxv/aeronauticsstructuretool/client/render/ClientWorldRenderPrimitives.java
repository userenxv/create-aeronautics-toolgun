package com.enxv.aeronauticsstructuretool.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;

final class ClientWorldRenderPrimitives {
    static final RenderType PREVIEW_RENDER_TYPE = RenderType.lines();

    private ClientWorldRenderPrimitives() {
    }

    static void renderPreviewCube(PoseStack poseStack, VertexConsumer consumer, VertexConsumer fillConsumer, Vector3d center, Quaterniond orientation, double scaleFactor, float red, float green, float blue, float alpha) {
        double half = 0.48D * scaleFactor;
        Vector3d[] corners = createPreviewCubeCorners(center, orientation, half);
        fillPreviewCube(poseStack.last().pose(), fillConsumer, corners, red, green, blue, alpha * 0.28F);
        renderBoundsEdges(poseStack, fillConsumer, corners, red, green, blue, alpha);
    }

    static void renderBoundsEdges(PoseStack poseStack, VertexConsumer consumer, Vector3d[] corners, float red, float green, float blue, float alpha) {
        renderPreviewLine(poseStack, consumer, corners[0], corners[1], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[1], corners[2], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[2], corners[3], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[3], corners[0], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[4], corners[5], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[5], corners[6], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[6], corners[7], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[7], corners[4], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[0], corners[4], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[1], corners[5], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[2], corners[6], red, green, blue, alpha);
        renderPreviewLine(poseStack, consumer, corners[3], corners[7], red, green, blue, alpha);
    }

    static Vector3d[] createLocalBoundsCorners(BoundingBox3ic bounds) {
        BoundingBox3i safeBounds = bounds instanceof BoundingBox3i box ? box : new BoundingBox3i(new BoundingBox3d(bounds));
        double minX = safeBounds.minX();
        double minY = safeBounds.minY();
        double minZ = safeBounds.minZ();
        double maxX = safeBounds.maxX() + 1.0D;
        double maxY = safeBounds.maxY() + 1.0D;
        double maxZ = safeBounds.maxZ() + 1.0D;
        return new Vector3d[]{
                new Vector3d(minX, minY, minZ),
                new Vector3d(maxX, minY, minZ),
                new Vector3d(maxX, minY, maxZ),
                new Vector3d(minX, minY, maxZ),
                new Vector3d(minX, maxY, minZ),
                new Vector3d(maxX, maxY, minZ),
                new Vector3d(maxX, maxY, maxZ),
                new Vector3d(minX, maxY, maxZ)
        };
    }

    static Vector3d transformLocalPoint(Vector3d position, Quaterniond orientation, Vector3d localPoint) {
        Vector3d transformed = new Vector3d(localPoint);
        orientation.transform(transformed);
        transformed.add(position);
        return transformed;
    }

    static Vector3d transformSubLevelPreviewPoint(SubLevel subLevel, Vector3d position, Quaterniond orientation, Vector3d localPoint) {
        Vector3d transformed = new Vector3d(localPoint)
                .sub(subLevel.logicalPose().rotationPoint())
                .mul(subLevel.logicalPose().scale());
        orientation.transform(transformed);
        transformed.add(position);
        return transformed;
    }

    static Vector3d[] createPreviewCubeCorners(Vector3d center, Quaterniond orientation, double half) {
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

    static void renderPreviewPoint(PoseStack poseStack, VertexConsumer consumer, VertexConsumer fillConsumer, Vector3d center, float red, float green, float blue, float alpha) {
        double arm = 0.0625D;
        renderPreviewLine(poseStack, fillConsumer, new Vector3d(center.x - arm, center.y, center.z), new Vector3d(center.x + arm, center.y, center.z), red, green, blue, alpha);
        renderPreviewLine(poseStack, fillConsumer, new Vector3d(center.x, center.y - arm, center.z), new Vector3d(center.x, center.y + arm, center.z), red, green, blue, alpha);
        renderPreviewLine(poseStack, fillConsumer, new Vector3d(center.x, center.y, center.z - arm), new Vector3d(center.x, center.y, center.z + arm), red, green, blue, alpha);
    }

    static void renderPreviewFallback(PoseStack poseStack, VertexConsumer consumer, VertexConsumer fillConsumer, Vector3d target, Quaterniond orientation) {
        renderPreviewPoint(poseStack, consumer, fillConsumer, target, 1.0F, 0.88F, 0.24F, 0.98F);
        Vector3d xAxis = new Vector3d(1.15D, 0.0D, 0.0D);
        Vector3d yAxis = new Vector3d(0.0D, 1.15D, 0.0D);
        Vector3d zAxis = new Vector3d(0.0D, 0.0D, 1.15D);
        orientation.transform(xAxis);
        orientation.transform(yAxis);
        orientation.transform(zAxis);
        renderPreviewLine(poseStack, fillConsumer, target, new Vector3d(target).add(xAxis), 1.0F, 0.40F, 0.22F, 0.96F);
        renderPreviewLine(poseStack, fillConsumer, target, new Vector3d(target).add(yAxis), 0.40F, 1.0F, 0.36F, 0.96F);
        renderPreviewLine(poseStack, fillConsumer, target, new Vector3d(target).add(zAxis), 0.38F, 0.74F, 1.0F, 0.96F);
    }

    static void renderPreviewLine(PoseStack poseStack, VertexConsumer consumer, Vector3d start, Vector3d end, float red, float green, float blue, float alpha) {
        Vector3d direction = new Vector3d(end).sub(start);
        if (direction.lengthSquared() <= 1.0E-8D) {
            return;
        }
        direction.normalize();

        Vector3d upReference = Math.abs(direction.y) < 0.92D
                ? new Vector3d(0.0D, 1.0D, 0.0D)
                : new Vector3d(1.0D, 0.0D, 0.0D);
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

        Matrix4f matrix = poseStack.last().pose();
        int r = (int) (red * 255.0F);
        int g = (int) (green * 255.0F);
        int b = (int) (blue * 255.0F);
        int a = (int) (alpha * 255.0F);

        addPreviewQuad(consumer, matrix, startA, startB, endB, endA, r, g, b, a);
        addPreviewQuad(consumer, matrix, startB, startC, endC, endB, r, g, b, a);
        addPreviewQuad(consumer, matrix, startC, startD, endD, endC, r, g, b, a);
        addPreviewQuad(consumer, matrix, startD, startA, endA, endD, r, g, b, a);
        addPreviewQuad(consumer, matrix, startA, startD, startC, startB, r, g, b, Math.max(24, a / 3));
        addPreviewQuad(consumer, matrix, endA, endB, endC, endD, r, g, b, Math.max(24, a / 3));
    }

    static void addPreviewQuad(VertexConsumer consumer, Matrix4f matrix, Vector3d first, Vector3d second, Vector3d third, Vector3d fourth, int red, int green, int blue, int alpha) {
        consumer.addVertex(matrix, (float) first.x, (float) first.y, (float) first.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, (float) second.x, (float) second.y, (float) second.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, (float) third.x, (float) third.y, (float) third.z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, (float) fourth.x, (float) fourth.y, (float) fourth.z).setColor(red, green, blue, alpha);
    }

    static void fillPreviewCube(Matrix4f matrix, VertexConsumer consumer, Vector3d[] corners, float red, float green, float blue, float alpha) {
        addFilledQuad(consumer, matrix, corners[0], corners[1], corners[2], corners[3], red, green, blue, alpha);
        addFilledQuad(consumer, matrix, corners[4], corners[5], corners[6], corners[7], red, green, blue, alpha);
        addFilledQuad(consumer, matrix, corners[0], corners[3], corners[7], corners[4], red, green, blue, alpha);
        addFilledQuad(consumer, matrix, corners[1], corners[2], corners[6], corners[5], red, green, blue, alpha);
        addFilledQuad(consumer, matrix, corners[3], corners[2], corners[6], corners[7], red, green, blue, alpha);
        addFilledQuad(consumer, matrix, corners[0], corners[1], corners[5], corners[4], red, green, blue, alpha);
    }

    static void addFilledQuad(VertexConsumer consumer, Matrix4f matrix, Vector3d first, Vector3d second, Vector3d third, Vector3d fourth, float red, float green, float blue, float alpha) {
        addFilledVertex(consumer, matrix, first.x, first.y, first.z, red, green, blue, alpha);
        addFilledVertex(consumer, matrix, second.x, second.y, second.z, red, green, blue, alpha);
        addFilledVertex(consumer, matrix, third.x, third.y, third.z, red, green, blue, alpha);
        addFilledVertex(consumer, matrix, fourth.x, fourth.y, fourth.z, red, green, blue, alpha);
    }

    static void addFilledVertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, (float) x, (float) y, (float) z)
                .setColor((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F), (int) (alpha * 255.0F));
    }

}
