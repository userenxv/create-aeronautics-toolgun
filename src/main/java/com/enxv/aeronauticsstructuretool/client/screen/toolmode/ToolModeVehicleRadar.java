package com.enxv.aeronauticsstructuretool.client.screen.toolmode;

import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.client.screen.NearbyVehicleQueryState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.BRASS_SOFT;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.PANEL_DARK;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_ERROR;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_MUTED;
import static com.enxv.aeronauticsstructuretool.client.screen.ClientPanelStyle.TEXT_PRIMARY;

public final class ToolModeVehicleRadar {
    private static final double SCALE_PADDING = 1.25D;
    private static final double MIN_SCALE_BLOCKS = 1.0D;
    private static final int VIEW_PADDING = 8;
    private static final int POINT_SIZE = 3;
    private static final int HIT_RADIUS = 5;
    private static final double SCAN_RADIANS_PER_SECOND = 1.35D;
    private static final double SCAN_TAIL_RADIANS = Math.toRadians(18.0D);

    private List<NearbyVehicleQueryState.Entry> cachedEntries = List.of();
    private List<RadarPoint> cachedPoints = List.of();
    private int cachedLeft = Integer.MIN_VALUE;
    private int cachedTop = Integer.MIN_VALUE;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedFallbackRange = Integer.MIN_VALUE;
    private double cachedOriginX = Double.NaN;
    private double cachedOriginZ = Double.NaN;
    private double scaleBlocks = MIN_SCALE_BLOCKS;

    public void render(
            GuiGraphics graphics,
            Font font,
            ToolModeLayout layout,
            List<NearbyVehicleQueryState.Entry> entries,
            UUID selectedId,
            int hoveredEntryIndex,
            LocalPlayer player,
            int fallbackRange
    ) {
        RadarBounds bounds = bounds(layout);
        double originX = player == null ? 0.0D : player.getX();
        double originZ = player == null ? 0.0D : player.getZ();
        ensureProjection(bounds, entries, originX, originZ, fallbackRange);

        drawBackground(graphics, bounds);
        double scanAngle = scanAngle();
        drawScanner(graphics, bounds, scanAngle);
        drawPoints(graphics, selectedId, hoveredEntryIndex, scanAngle);
        drawPlayerMarker(graphics, bounds.centerX(), bounds.centerY(), player == null ? 0.0F : player.getYRot());
        drawLabels(graphics, font, bounds, entries.isEmpty());
    }

    public int entryIndexAt(
            ToolModeLayout layout,
            List<NearbyVehicleQueryState.Entry> entries,
            LocalPlayer player,
            int fallbackRange,
            double mouseX,
            double mouseY
    ) {
        RadarBounds bounds = bounds(layout);
        if (!bounds.contains(mouseX, mouseY) || entries.isEmpty()) {
            return -1;
        }
        double originX = player == null ? 0.0D : player.getX();
        double originZ = player == null ? 0.0D : player.getZ();
        ensureProjection(bounds, entries, originX, originZ, fallbackRange);

        RadarPoint nearest = null;
        double nearestDistanceSquared = HIT_RADIUS * HIT_RADIUS + 0.01D;
        for (RadarPoint point : this.cachedPoints) {
            double dx = mouseX - point.x();
            double dy = mouseY - point.y();
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < nearestDistanceSquared
                    || distanceSquared == nearestDistanceSquared
                    && (nearest == null || point.worldDistance() < nearest.worldDistance())) {
                nearest = point;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest == null ? -1 : nearest.entryIndex();
    }

    private void ensureProjection(
            RadarBounds bounds,
            List<NearbyVehicleQueryState.Entry> entries,
            double originX,
            double originZ,
            int fallbackRange
    ) {
        if (this.cachedEntries == entries
                && this.cachedLeft == bounds.left()
                && this.cachedTop == bounds.top()
                && this.cachedWidth == bounds.width()
                && this.cachedHeight == bounds.height()
                && this.cachedFallbackRange == fallbackRange
                && Math.abs(this.cachedOriginX - originX) < 0.01D
                && Math.abs(this.cachedOriginZ - originZ) < 0.01D) {
            return;
        }

        double farthestDistance = 0.0D;
        for (NearbyVehicleQueryState.Entry entry : entries) {
            double dx = entry.position().getX() + 0.5D - originX;
            double dz = entry.position().getZ() + 0.5D - originZ;
            double horizontalDistance = Math.hypot(dx, dz);
            double reportedDistance = Double.isFinite(entry.distance())
                    ? Math.max(0.0D, entry.distance())
                    : 0.0D;
            farthestDistance = Math.max(farthestDistance, Math.max(horizontalDistance, reportedDistance));
        }
        if (entries.isEmpty()) {
            farthestDistance = fallbackRange == ClientToolState.INFINITE_NEARBY_QUERY_RANGE
                    ? ClientToolState.DEFAULT_SURVIVAL_NEARBY_QUERY_RANGE
                    : Math.max(MIN_SCALE_BLOCKS, fallbackRange);
        }
        this.scaleBlocks = Math.max(MIN_SCALE_BLOCKS, farthestDistance * SCALE_PADDING);

        int radiusPixels = Math.max(1, Math.min(bounds.width(), bounds.height()) / 2 - VIEW_PADDING);
        double pixelsPerBlock = radiusPixels / this.scaleBlocks;
        List<RadarPoint> points = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            NearbyVehicleQueryState.Entry entry = entries.get(index);
            double dx = entry.position().getX() + 0.5D - originX;
            double dz = entry.position().getZ() + 0.5D - originZ;
            int pointX = Mth.clamp(
                    (int) Math.round(bounds.centerX() + dx * pixelsPerBlock),
                    bounds.left() + 2,
                    bounds.right() - 3
            );
            int pointY = Mth.clamp(
                    (int) Math.round(bounds.centerY() + dz * pixelsPerBlock),
                    bounds.top() + 2,
                    bounds.bottom() - 3
            );
            points.add(new RadarPoint(
                    index,
                    pointX,
                    pointY,
                    Math.atan2(pointY - bounds.centerY(), pointX - bounds.centerX()),
                    Math.max(0.0D, entry.distance())
            ));
        }

        this.cachedEntries = entries;
        this.cachedPoints = List.copyOf(points);
        this.cachedLeft = bounds.left();
        this.cachedTop = bounds.top();
        this.cachedWidth = bounds.width();
        this.cachedHeight = bounds.height();
        this.cachedFallbackRange = fallbackRange;
        this.cachedOriginX = originX;
        this.cachedOriginZ = originZ;
    }

    private static void drawBackground(GuiGraphics graphics, RadarBounds bounds) {
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xF0100D0A);
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.top() + 1, BRASS_SOFT);
        graphics.fill(bounds.left(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), PANEL_DARK);
        graphics.fill(bounds.left(), bounds.top(), bounds.left() + 1, bounds.bottom(), BRASS_SOFT);
        graphics.fill(bounds.right() - 1, bounds.top(), bounds.right(), bounds.bottom(), PANEL_DARK);

        int grid = 0x263F3220;
        graphics.fill(bounds.centerX(), bounds.top() + 2, bounds.centerX() + 1, bounds.bottom() - 2, grid);
        graphics.fill(bounds.left() + 2, bounds.centerY(), bounds.right() - 2, bounds.centerY() + 1, grid);
        graphics.fill(bounds.left() + bounds.width() / 4, bounds.top() + 2,
                bounds.left() + bounds.width() / 4 + 1, bounds.bottom() - 2, 0x183F3220);
        graphics.fill(bounds.left() + bounds.width() * 3 / 4, bounds.top() + 2,
                bounds.left() + bounds.width() * 3 / 4 + 1, bounds.bottom() - 2, 0x183F3220);

        int radius = Math.max(1, Math.min(bounds.width(), bounds.height()) / 2 - VIEW_PADDING);
        drawDottedCircle(graphics, bounds.centerX(), bounds.centerY(), radius / 2, 0x304F4028);
        drawDottedCircle(graphics, bounds.centerX(), bounds.centerY(), radius, 0x404F4028);
        drawCornerMarks(graphics, bounds);
    }

    private static void drawScanner(GuiGraphics graphics, RadarBounds bounds, double scanAngle) {
        int radius = Math.max(1, Math.min(bounds.width(), bounds.height()) / 2 - VIEW_PADDING);
        for (int tail = 6; tail >= 0; tail--) {
            double angle = scanAngle - Math.toRadians(tail * 2.5D);
            int alpha = 20 + (6 - tail) * 13;
            int color = alpha << 24 | BRASS & 0x00FFFFFF;
            int endX = bounds.centerX() + (int) Math.round(Math.cos(angle) * radius);
            int endY = bounds.centerY() + (int) Math.round(Math.sin(angle) * radius);
            drawLine(graphics, bounds.centerX(), bounds.centerY(), endX, endY, color);
        }
        int tipX = bounds.centerX() + (int) Math.round(Math.cos(scanAngle) * radius);
        int tipY = bounds.centerY() + (int) Math.round(Math.sin(scanAngle) * radius);
        graphics.fill(tipX - 1, tipY - 1, tipX + 2, tipY + 2, 0x88D3B06A);
    }

    private void drawPoints(GuiGraphics graphics, UUID selectedId, int hoveredEntryIndex, double scanAngle) {
        for (RadarPoint point : this.cachedPoints) {
            NearbyVehicleQueryState.Entry entry = this.cachedEntries.get(point.entryIndex());
            boolean selected = selectedId != null && selectedId.equals(entry.id());
            boolean hovered = point.entryIndex() == hoveredEntryIndex;
            double scanTrail = positiveAngle(scanAngle - point.angle());
            boolean scanned = scanTrail <= SCAN_TAIL_RADIANS;

            int color = entry.broken() ? TEXT_ERROR : entry.loaded() ? BRASS : TEXT_MUTED;
            if (scanned) {
                graphics.fill(point.x() - 3, point.y() - 3, point.x() + 4, point.y() + 4, colorWithAlpha(color, 36));
            }
            if (selected || hovered) {
                int outline = selected ? 0xFFFFE4A8 : TEXT_PRIMARY;
                graphics.fill(point.x() - 3, point.y() - 3, point.x() + 4, point.y() - 2, outline);
                graphics.fill(point.x() - 3, point.y() + 3, point.x() + 4, point.y() + 4, outline);
                graphics.fill(point.x() - 3, point.y() - 2, point.x() - 2, point.y() + 3, outline);
                graphics.fill(point.x() + 3, point.y() - 2, point.x() + 4, point.y() + 3, outline);
            }
            int half = POINT_SIZE / 2;
            graphics.fill(point.x() - half, point.y() - half,
                    point.x() - half + POINT_SIZE, point.y() - half + POINT_SIZE, color);
            if (scanned && !entry.broken()) {
                graphics.fill(point.x(), point.y(), point.x() + 1, point.y() + 1, 0xFFFFE4A8);
            }
        }
    }

    private void drawLabels(GuiGraphics graphics, Font font, RadarBounds bounds, boolean empty) {
        graphics.drawCenteredString(font, Component.literal("N"), bounds.centerX(), bounds.top() + 3, TEXT_MUTED);
        String distance = formatDistance(this.scaleBlocks);
        Component scale = Component.translatable("screen.create_aeronautics_toolgun.query.radar_scale", distance);
        String scaleLabel = scale.getString();
        if (font.width(scaleLabel) > bounds.width() - 8) {
            scaleLabel = distance;
        }
        graphics.drawString(font, scaleLabel, bounds.right() - font.width(scaleLabel) - 4, bounds.bottom() - 11, TEXT_MUTED, false);
        if (empty) {
            Component message = Component.translatable("screen.create_aeronautics_toolgun.query.none");
            graphics.drawCenteredString(font, font.plainSubstrByWidth(message.getString(), bounds.width() - 16),
                    bounds.centerX(), bounds.centerY() + 12, TEXT_MUTED);
        }
    }

    private static String formatDistance(double distance) {
        if (distance >= 1_000_000_000.0D) {
            return String.format(Locale.ROOT, "%.1fG", distance / 1_000_000_000.0D);
        }
        if (distance >= 1_000_000.0D) {
            return String.format(Locale.ROOT, "%.1fM", distance / 1_000_000.0D);
        }
        if (distance >= 1_000.0D) {
            return String.format(Locale.ROOT, "%.1fk", distance / 1_000.0D);
        }
        return distance < 10.0D
                ? String.format(Locale.ROOT, "%.1f", distance)
                : String.format(Locale.ROOT, "%.0f", distance);
    }

    private static void drawPlayerMarker(GuiGraphics graphics, int centerX, int centerY, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double directionX = -Math.sin(yaw);
        double directionY = Math.cos(yaw);
        double perpendicularX = -directionY;
        double perpendicularY = directionX;
        int tipX = (int) Math.round(centerX + directionX * 6.0D);
        int tipY = (int) Math.round(centerY + directionY * 6.0D);
        int rearX = (int) Math.round(centerX - directionX * 3.0D);
        int rearY = (int) Math.round(centerY - directionY * 3.0D);
        int leftX = (int) Math.round(rearX + perpendicularX * 4.0D);
        int leftY = (int) Math.round(rearY + perpendicularY * 4.0D);
        int rightX = (int) Math.round(rearX - perpendicularX * 4.0D);
        int rightY = (int) Math.round(rearY - perpendicularY * 4.0D);
        fillTriangle(graphics, tipX, tipY, leftX, leftY, rightX, rightY, 0xFFFFE4A8);
        graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFF4A3820);
    }

    private static void fillTriangle(
            GuiGraphics graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int x3,
            int y3,
            int color
    ) {
        int minX = Math.min(x1, Math.min(x2, x3));
        int maxX = Math.max(x1, Math.max(x2, x3));
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        long area = edge(x1, y1, x2, y2, x3, y3);
        if (area == 0L) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                long first = edge(x1, y1, x2, y2, x, y);
                long second = edge(x2, y2, x3, y3, x, y);
                long third = edge(x3, y3, x1, y1, x, y);
                if (area > 0L ? first >= 0L && second >= 0L && third >= 0L
                        : first <= 0L && second <= 0L && third <= 0L) {
                    graphics.fill(x, y, x + 1, y + 1, color);
                }
            }
        }
    }

    private static long edge(int x1, int y1, int x2, int y2, int x, int y) {
        return (long) (x - x1) * (y2 - y1) - (long) (y - y1) * (x2 - x1);
    }

    private static void drawDottedCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        int steps = Math.max(24, radius * 5);
        for (int step = 0; step < steps; step += 2) {
            double angle = Math.PI * 2.0D * step / steps;
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawCornerMarks(GuiGraphics graphics, RadarBounds bounds) {
        int color = 0xA0D3B06A;
        int length = 7;
        graphics.fill(bounds.left() + 2, bounds.top() + 2, bounds.left() + 2 + length, bounds.top() + 3, color);
        graphics.fill(bounds.left() + 2, bounds.top() + 2, bounds.left() + 3, bounds.top() + 2 + length, color);
        graphics.fill(bounds.right() - 2 - length, bounds.top() + 2, bounds.right() - 2, bounds.top() + 3, color);
        graphics.fill(bounds.right() - 3, bounds.top() + 2, bounds.right() - 2, bounds.top() + 2 + length, color);
        graphics.fill(bounds.left() + 2, bounds.bottom() - 3, bounds.left() + 2 + length, bounds.bottom() - 2, color);
        graphics.fill(bounds.left() + 2, bounds.bottom() - 2 - length, bounds.left() + 3, bounds.bottom() - 2, color);
        graphics.fill(bounds.right() - 2 - length, bounds.bottom() - 3, bounds.right() - 2, bounds.bottom() - 2, color);
        graphics.fill(bounds.right() - 3, bounds.bottom() - 2 - length, bounds.right() - 2, bounds.bottom() - 2, color);
    }

    private static void drawLine(GuiGraphics graphics, int startX, int startY, int endX, int endY, int color) {
        int dx = Math.abs(endX - startX);
        int dy = Math.abs(endY - startY);
        int stepX = startX < endX ? 1 : -1;
        int stepY = startY < endY ? 1 : -1;
        int error = dx - dy;
        int x = startX;
        int y = startY;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == endX && y == endY) {
                return;
            }
            int doubledError = error * 2;
            if (doubledError > -dy) {
                error -= dy;
                x += stepX;
            }
            if (doubledError < dx) {
                error += dx;
                y += stepY;
            }
        }
    }

    private static int colorWithAlpha(int color, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | color & 0x00FFFFFF;
    }

    private static double scanAngle() {
        return positiveAngle(System.nanoTime() / 1_000_000_000.0D * SCAN_RADIANS_PER_SECOND);
    }

    private static double positiveAngle(double angle) {
        double fullTurn = Math.PI * 2.0D;
        double normalized = angle % fullTurn;
        return normalized < 0.0D ? normalized + fullTurn : normalized;
    }

    private static RadarBounds bounds(ToolModeLayout layout) {
        return new RadarBounds(
                layout.rightPanelLeft(),
                layout.windowTop() + 78,
                layout.queryListWidth(),
                98
        );
    }

    private record RadarPoint(int entryIndex, int x, int y, double angle, double worldDistance) {
    }

    private record RadarBounds(int left, int top, int width, int height) {
        int right() {
            return this.left + this.width;
        }

        int bottom() {
            return this.top + this.height;
        }

        int centerX() {
            return this.left + this.width / 2;
        }

        int centerY() {
            return this.top + this.height / 2;
        }

        boolean contains(double x, double y) {
            return x >= this.left && x < right() && y >= this.top && y < bottom();
        }
    }
}
