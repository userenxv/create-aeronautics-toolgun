package com.enxv.aeronauticsstructuretool.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ClientBeamRenderer {
    private static final RenderType RIBBON_RENDER_TYPE = RenderType.debugQuads();
    private static final int PULSE_LIFETIME_TICKS = 8;
    private static final int SUSTAINED_LIFETIME_TICKS = 8;
    private static final int LAUNCH_LIFETIME_TICKS = 3;
    private static final float[] LAUNCH_INTENSITIES = {1.0F, 0.72F, 0.32F, 0.08F, 0.0F};
    private static final double BURST_RADIUS = 0.085D;
    private static final double MUZZLE_FADE_LENGTH = 0.52D;
    private static final double OUTER_BEAM_HALF_WIDTH = 0.040D;
    private static final double INNER_BEAM_HALF_WIDTH = 0.019D;
    private static final double CORE_BEAM_HALF_WIDTH = 0.009D;
    private static final double MUZZLE_RING_OUTER_HALF_WIDTH = 0.098D;
    private static final double MUZZLE_RING_INNER_HALF_WIDTH = 0.058D;
    private static final double MUZZLE_RING_FORWARD_OFFSET = 0.045D;
    private static final double BEAM_START_FORWARD_OFFSET = 0.080D;
    private static final float PULSE_FADE_IN_TICKS = 4.0F;
    private static final float PULSE_FADE_OUT_TICKS = 5.5F;
    private static final float SUSTAINED_FADE_IN_TICKS = 6.5F;
    private static final float SUSTAINED_FADE_OUT_TICKS = 4.5F;
    private static final float SUSTAINED_INTENSITY = 0.52F;
    private static final float PULSE_ROTATION_SPEED = 300.0F;
    private static final float SUSTAINED_ROTATION_SPEED = 300.0F;
    private static final long MUZZLE_SAMPLE_LIFETIME_MILLIS = 250L;
    private static final java.util.EnumMap<InteractionHand, MuzzleSample> MUZZLE_SAMPLES =
            new java.util.EnumMap<>(InteractionHand.class);

    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static Vec3 target = Vec3.ZERO;
    private static long startGameTime = Long.MIN_VALUE;
    private static long expireGameTime = Long.MIN_VALUE;
    private static BeamVisual visual = BeamVisual.PULSE;

    private ClientBeamRenderer() {
    }

    public static void showPulse(InteractionHand newHand, Vec3 newTarget) {
        show(newHand, newTarget, BeamVisual.PULSE);
    }

    public static void showSustained(InteractionHand newHand, Vec3 newTarget) {
        show(newHand, newTarget, BeamVisual.SUSTAINED);
    }

    public static void showLaunch(InteractionHand newHand, Vec3 newTarget) {
        show(newHand, newTarget, BeamVisual.LAUNCH);
    }

    public static void stop(InteractionHand stoppedHand) {
        if (hand == stoppedHand) {
            expireGameTime = Long.MIN_VALUE;
        }
    }

    public static void captureMuzzle(
            InteractionHand renderedHand,
            Matrix4f itemPose,
            Vector3f localMuzzlePoint
    ) {
        Matrix4f viewItemPose = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(itemPose);
        Vector3f viewRelative = viewItemPose.transformPosition(new Vector3f(localMuzzlePoint));
        MUZZLE_SAMPLES.put(renderedHand, new MuzzleSample(viewRelative, Util.getMillis()));
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            startGameTime = Long.MIN_VALUE;
            expireGameTime = Long.MIN_VALUE;
            MUZZLE_SAMPLES.clear();
        }
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.level.getGameTime() > expireGameTime) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPos = event.getCamera().getPosition();
        Vec3 start = computeMuzzlePosition(minecraft.player, partialTick);
        Vec3 end = target;
        Vec3 segment = end.subtract(start);
        double length = segment.length();
        if (length < 1.0E-4D) {
            return;
        }

        Vec3 direction = segment.scale(1.0D / length);
        Vector3f cameraLookVector = event.getCamera().getLookVector();
        Vec3 cameraLook = new Vec3(cameraLookVector.x(), cameraLookVector.y(), cameraLookVector.z());
        Vec3 perpendicular = normalizeOrFallback(
                direction.cross(cameraLook),
                direction.cross(new Vec3(0.0D, 1.0D, 0.0D)),
                new Vec3(1.0D, 0.0D, 0.0D)
        );
        Vec3 secondPerpendicular = normalizeOrFallback(
                direction.cross(perpendicular),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D)
        );
        float alphaScale = computeAlphaScale(minecraft, partialTick);
        if (alphaScale <= 0.01F) {
            return;
        }
        float intensityScale = visual == BeamVisual.SUSTAINED ? SUSTAINED_INTENSITY : 1.0F;
        float age = (minecraft.level.getGameTime() - startGameTime) + partialTick;
        float launchFrame = age * 1.5F;
        float launchProgress = Mth.clamp(launchFrame / 3.0F, 0.0F, 1.0F);
        double rotationRadians = Math.toRadians(computeBeamRotationDegrees(minecraft, partialTick));
        Vec3 rotatedPerpendicular = perpendicular.scale(Math.cos(rotationRadians))
                .add(secondPerpendicular.scale(Math.sin(rotationRadians)));
        Vec3 rotatedSecondPerpendicular = secondPerpendicular.scale(Math.cos(rotationRadians))
                .subtract(perpendicular.scale(Math.sin(rotationRadians)));

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        if (visual == BeamVisual.LAUNCH) {
            VertexConsumer launchConsumer = bufferSource.getBuffer(RIBBON_RENDER_TYPE);
            drawLaunchBeam(
                    launchConsumer,
                    poseStack.last(),
                    start,
                    end,
                    direction,
                    rotatedPerpendicular,
                    rotatedSecondPerpendicular,
                    alphaScale,
                    launchProgress
            );
            poseStack.popPose();
            bufferSource.endBatch(RIBBON_RENDER_TYPE);
            return;
        }
        VertexConsumer ribbonConsumer = bufferSource.getBuffer(RIBBON_RENDER_TYPE);
        drawPrismBeam(
                ribbonConsumer,
                poseStack.last(),
                start,
                end,
                direction,
                rotatedPerpendicular,
                rotatedSecondPerpendicular,
                alphaScale,
                intensityScale
        );
        poseStack.popPose();
        bufferSource.endBatch(RIBBON_RENDER_TYPE);
    }

    private static void show(InteractionHand newHand, Vec3 newTarget, BeamVisual newVisual) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        boolean refreshExistingSustained = newVisual == BeamVisual.SUSTAINED
                && visual == BeamVisual.SUSTAINED
                && hand == newHand
                && gameTime <= expireGameTime;
        hand = newHand;
        target = newTarget;
        visual = newVisual;
        if (!refreshExistingSustained) {
            startGameTime = gameTime;
        }
        expireGameTime = gameTime
                + switch (newVisual) {
                    case SUSTAINED -> SUSTAINED_LIFETIME_TICKS;
                    case LAUNCH -> LAUNCH_LIFETIME_TICKS;
                    case PULSE -> PULSE_LIFETIME_TICKS;
                };
    }

    private static Vec3 computeMuzzlePosition(LocalPlayer player, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType().isFirstPerson() && minecraft.getCameraEntity() == player) {
            MuzzleSample sample = MUZZLE_SAMPLES.get(hand);
            if (sample != null && Util.getMillis() - sample.capturedAtMillis() <= MUZZLE_SAMPLE_LIFETIME_MILLIS) {
                Vector3f worldOffset = new Vector3f(sample.viewRelative());
                minecraft.gameRenderer.getMainCamera().rotation().transform(worldOffset);
                Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
                return cameraPosition.add(worldOffset.x(), worldOffset.y(), worldOffset.z());
            }
            var camera = minecraft.gameRenderer.getMainCamera();
            Vec3 cameraPosition = camera.getPosition();
            Vector3f cameraForward = camera.getLookVector();
            Vector3f cameraLeft = camera.getLeftVector();
            Vector3f cameraUp = camera.getUpVector();
            Vec3 forward = new Vec3(cameraForward.x(), cameraForward.y(), cameraForward.z());
            Vec3 right = new Vec3(-cameraLeft.x(), -cameraLeft.y(), -cameraLeft.z());
            Vec3 up = new Vec3(cameraUp.x(), cameraUp.y(), cameraUp.z());
            double side = hand == InteractionHand.MAIN_HAND ? 1.0D : -1.0D;
            return cameraPosition
                    .add(forward.scale(0.62D))
                    .add(right.scale(0.39D * side))
                    .add(up.scale(-0.36D));
        }

        HumanoidArm arm = resolveArm(player);
        double armOffset = 0.22D * (arm == HumanoidArm.RIGHT ? -1.0D : 1.0D);
        float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * ((float) Math.PI / 180F);
        double yOffset = player.getBoundingBox().getYsize() - 1.08D;
        double zOffset = player.isCrouching() ? -0.2D : 0.07D;
        Vec3 ropeHold = player.getPosition(partialTick)
                .add(new Vec3(armOffset, yOffset, zOffset).yRot(-bodyYaw));
        return ropeHold.add(player.getViewVector(partialTick).normalize().scale(0.55D));
    }

    private static HumanoidArm resolveArm(LocalPlayer player) {
        HumanoidArm mainArm = player.getMainArm();
        return hand == InteractionHand.OFF_HAND ? mainArm.getOpposite() : mainArm;
    }

    private static float computeAlphaScale(Minecraft minecraft, float partialTick) {
        float age = (minecraft.level.getGameTime() - startGameTime) + partialTick;
        float remaining = (expireGameTime - minecraft.level.getGameTime()) - partialTick;
        if (visual == BeamVisual.LAUNCH) {
            return launchIntensity(age);
        }
        if (visual == BeamVisual.SUSTAINED) {
            float fadeIn = smoothStep(Mth.clamp(age / SUSTAINED_FADE_IN_TICKS, 0.0F, 1.0F));
            float fadeOut = smoothStep(Mth.clamp(remaining / SUSTAINED_FADE_OUT_TICKS, 0.0F, 1.0F));
            return fadeIn * fadeOut;
        }
        float fadeIn = smoothStep(Mth.clamp(age / PULSE_FADE_IN_TICKS, 0.0F, 1.0F));
        float fadeOut = smoothStep(Mth.clamp(remaining / PULSE_FADE_OUT_TICKS, 0.0F, 1.0F));
        return fadeIn * fadeOut;
    }

    private static float computeBeamRotationDegrees(Minecraft minecraft, float partialTick) {
        float gameTime = (minecraft.level.getGameTime() + partialTick) / 20.0F;
        float speed = visual == BeamVisual.SUSTAINED ? SUSTAINED_ROTATION_SPEED : PULSE_ROTATION_SPEED;
        return gameTime * speed;
    }

    private static float launchIntensity(float ageTicks) {
        float frame = Math.max(0.0F, ageTicks * 1.5F);
        int first = Mth.floor(frame);
        if (first >= LAUNCH_INTENSITIES.length - 1) {
            return 0.0F;
        }
        float fraction = frame - first;
        return Mth.lerp(fraction, LAUNCH_INTENSITIES[first], LAUNCH_INTENSITIES[first + 1]);
    }

    private static void drawPrismBeam(
            VertexConsumer vertexConsumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 direction,
            Vec3 right,
            Vec3 up,
            float alphaScale,
            float intensityScale
    ) {
        Matrix4f matrix = pose.pose();
        int outerAlpha = scaleAlpha(58, alphaScale * intensityScale);
        int innerAlpha = scaleAlpha(96, alphaScale * intensityScale);
        int coreAlpha = scaleAlpha(156, alphaScale * intensityScale);
        int pulseAlpha = scaleAlpha(54, alphaScale * intensityScale);
        int muzzleAlpha = scaleAlpha(178, alphaScale * intensityScale);
        Vec3 ringCenter = start.add(direction.scale(MUZZLE_RING_FORWARD_OFFSET));
        Vec3 beamStart = start.add(direction.scale(BEAM_START_FORWARD_OFFSET));

        drawMuzzleRing(vertexConsumer, matrix, ringCenter, right, up, 255, 226, 168, muzzleAlpha);
        drawSquarePrism(vertexConsumer, matrix, beamStart, end, direction, right.scale(OUTER_BEAM_HALF_WIDTH), up.scale(OUTER_BEAM_HALF_WIDTH), 255, 210, 112, outerAlpha);
        drawSquarePrism(vertexConsumer, matrix, beamStart, end, direction, right.scale(INNER_BEAM_HALF_WIDTH), up.scale(INNER_BEAM_HALF_WIDTH), 255, 245, 214, innerAlpha);
        drawSquarePrism(vertexConsumer, matrix, beamStart, end, direction, right.scale(CORE_BEAM_HALF_WIDTH), up.scale(CORE_BEAM_HALF_WIDTH), 255, 252, 240, coreAlpha);
        if (visual == BeamVisual.PULSE) {
            drawSquareBurst(vertexConsumer, matrix, end, right.scale(BURST_RADIUS), up.scale(BURST_RADIUS), 255, 232, 170, pulseAlpha);
        }
    }

    private static void drawLaunchBeam(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 direction,
            Vec3 right,
            Vec3 up,
            float alphaScale,
            float progress
    ) {
        double expansion = 1.0D - Math.pow(1.0D - progress, 2.0D);
        double outerWidth = 0.055D + 0.125D * expansion;
        double innerWidth = 0.028D + 0.082D * expansion;
        double coreWidth = 0.012D + 0.033D * expansion;
        Matrix4f matrix = pose.pose();
        Vec3 beamStart = start.add(direction.scale(0.035D));
        drawSquarePrism(consumer, matrix, beamStart, end, direction,
                right.scale(outerWidth), up.scale(outerWidth),
                255, 255, 255, scaleAlpha(105, alphaScale));
        drawSquarePrism(consumer, matrix, beamStart, end, direction,
                right.scale(innerWidth), up.scale(innerWidth),
                255, 255, 255, scaleAlpha(190, alphaScale));
        drawSquarePrism(consumer, matrix, beamStart, end, direction,
                right.scale(coreWidth), up.scale(coreWidth),
                255, 255, 255, scaleAlpha(255, alphaScale));
    }

    private static void drawMuzzleRing(VertexConsumer consumer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, int red, int green, int blue, int alpha) {
        Vec3 outerRight = right.scale(MUZZLE_RING_OUTER_HALF_WIDTH);
        Vec3 outerUp = up.scale(MUZZLE_RING_OUTER_HALF_WIDTH);
        Vec3 innerRight = right.scale(MUZZLE_RING_INNER_HALF_WIDTH);
        Vec3 innerUp = up.scale(MUZZLE_RING_INNER_HALF_WIDTH);
        Vec3 outerBottomLeft = center.subtract(outerRight).subtract(outerUp);
        Vec3 outerBottomRight = center.add(outerRight).subtract(outerUp);
        Vec3 outerTopRight = center.add(outerRight).add(outerUp);
        Vec3 outerTopLeft = center.subtract(outerRight).add(outerUp);
        Vec3 innerBottomLeft = center.subtract(innerRight).subtract(innerUp);
        Vec3 innerBottomRight = center.add(innerRight).subtract(innerUp);
        Vec3 innerTopRight = center.add(innerRight).add(innerUp);
        Vec3 innerTopLeft = center.subtract(innerRight).add(innerUp);
        drawQuad(consumer, matrix, outerBottomLeft, outerBottomRight, innerBottomRight, innerBottomLeft, red, green, blue, scaleAlpha(alpha, 0.88F));
        drawQuad(consumer, matrix, outerBottomRight, outerTopRight, innerTopRight, innerBottomRight, red, green, blue, alpha);
        drawQuad(consumer, matrix, outerTopRight, outerTopLeft, innerTopLeft, innerTopRight, red, green, blue, alpha);
        drawQuad(consumer, matrix, outerTopLeft, outerBottomLeft, innerBottomLeft, innerTopLeft, red, green, blue, scaleAlpha(alpha, 0.88F));
    }

    private static void drawSquarePrism(VertexConsumer consumer, Matrix4f matrix, Vec3 start, Vec3 end, Vec3 direction, Vec3 right, Vec3 up, int red, int green, int blue, int alpha) {
        Vec3 startBottomLeft = start.subtract(right).subtract(up);
        Vec3 startBottomRight = start.add(right).subtract(up);
        Vec3 startTopRight = start.add(right).add(up);
        Vec3 startTopLeft = start.subtract(right).add(up);
        Vec3 endBottomLeft = end.subtract(right).subtract(up);
        Vec3 endBottomRight = end.add(right).subtract(up);
        Vec3 endTopRight = end.add(right).add(up);
        Vec3 endTopLeft = end.subtract(right).add(up);
        Vec3 fadeEndCenter = start.add(direction.scale(Math.min(start.distanceTo(end), MUZZLE_FADE_LENGTH)));
        Vec3 fadeEndBottomLeft = fadeEndCenter.subtract(right).subtract(up);
        Vec3 fadeEndBottomRight = fadeEndCenter.add(right).subtract(up);
        Vec3 fadeEndTopRight = fadeEndCenter.add(right).add(up);
        Vec3 fadeEndTopLeft = fadeEndCenter.subtract(right).add(up);
        drawGradientQuad(consumer, matrix, startBottomLeft, startBottomRight, fadeEndBottomRight, fadeEndBottomLeft, red, green, blue, 0, alpha);
        drawGradientQuad(consumer, matrix, startBottomRight, startTopRight, fadeEndTopRight, fadeEndBottomRight, red, green, blue, 0, alpha);
        drawGradientQuad(consumer, matrix, startTopRight, startTopLeft, fadeEndTopLeft, fadeEndTopRight, red, green, blue, 0, alpha);
        drawGradientQuad(consumer, matrix, startTopLeft, startBottomLeft, fadeEndBottomLeft, fadeEndTopLeft, red, green, blue, 0, alpha);
        if (fadeEndCenter.distanceTo(end) > 1.0E-4D) {
            drawQuad(consumer, matrix, fadeEndBottomLeft, fadeEndBottomRight, endBottomRight, endBottomLeft, red, green, blue, alpha);
            drawQuad(consumer, matrix, fadeEndBottomRight, fadeEndTopRight, endTopRight, endBottomRight, red, green, blue, alpha);
            drawQuad(consumer, matrix, fadeEndTopRight, fadeEndTopLeft, endTopLeft, endTopRight, red, green, blue, alpha);
            drawQuad(consumer, matrix, fadeEndTopLeft, fadeEndBottomLeft, endBottomLeft, endTopLeft, red, green, blue, alpha);
        }
    }

    private static void drawSquareBurst(VertexConsumer consumer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up, int red, int green, int blue, int alpha) {
        Vec3 outerBottomLeft = center.subtract(right).subtract(up);
        Vec3 outerBottomRight = center.add(right).subtract(up);
        Vec3 outerTopRight = center.add(right).add(up);
        Vec3 outerTopLeft = center.subtract(right).add(up);
        Vec3 innerRight = right.scale(0.48D);
        Vec3 innerUp = up.scale(0.48D);
        Vec3 innerBottomLeft = center.subtract(innerRight).subtract(innerUp);
        Vec3 innerBottomRight = center.add(innerRight).subtract(innerUp);
        Vec3 innerTopRight = center.add(innerRight).add(innerUp);
        Vec3 innerTopLeft = center.subtract(innerRight).add(innerUp);
        drawQuad(consumer, matrix, outerBottomLeft, outerBottomRight, innerBottomRight, innerBottomLeft, red, green, blue, scaleAlpha(alpha, 0.52F));
        drawQuad(consumer, matrix, outerBottomRight, outerTopRight, innerTopRight, innerBottomRight, red, green, blue, scaleAlpha(alpha, 0.52F));
        drawQuad(consumer, matrix, outerTopRight, outerTopLeft, innerTopLeft, innerTopRight, red, green, blue, scaleAlpha(alpha, 0.52F));
        drawQuad(consumer, matrix, outerTopLeft, outerBottomLeft, innerBottomLeft, innerTopLeft, red, green, blue, scaleAlpha(alpha, 0.52F));
        drawQuad(consumer, matrix, innerBottomLeft, innerBottomRight, innerTopRight, innerTopLeft, red, green, blue, scaleAlpha(alpha, 0.82F));
    }

    private static Vec3 normalizeOrFallback(Vec3 primary, Vec3 secondary, Vec3 fallback) {
        if (primary.lengthSqr() >= 1.0E-4D) {
            return primary.normalize();
        }
        if (secondary.lengthSqr() >= 1.0E-4D) {
            return secondary.normalize();
        }
        return fallback.normalize();
    }

    private static int scaleAlpha(int alpha, float scale) {
        return Math.max(0, Math.min(255, Math.round(alpha * scale)));
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static void drawQuad(VertexConsumer consumer, Matrix4f matrix, Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, int red, int green, int blue, int alpha) {
        addQuadVertex(consumer, matrix, first, red, green, blue, alpha);
        addQuadVertex(consumer, matrix, second, red, green, blue, alpha);
        addQuadVertex(consumer, matrix, third, red, green, blue, alpha);
        addQuadVertex(consumer, matrix, fourth, red, green, blue, alpha);
    }

    private static void drawGradientQuad(VertexConsumer consumer, Matrix4f matrix, Vec3 startFirst, Vec3 startSecond, Vec3 endSecond, Vec3 endFirst, int red, int green, int blue, int startAlpha, int endAlpha) {
        addQuadVertex(consumer, matrix, startFirst, red, green, blue, startAlpha);
        addQuadVertex(consumer, matrix, startSecond, red, green, blue, startAlpha);
        addQuadVertex(consumer, matrix, endSecond, red, green, blue, endAlpha);
        addQuadVertex(consumer, matrix, endFirst, red, green, blue, endAlpha);
    }

    private static void addQuadVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 position, int red, int green, int blue, int alpha) {
        consumer.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .setColor(red, green, blue, alpha);
    }

    private enum BeamVisual {
        PULSE,
        SUSTAINED,
        LAUNCH
    }

    private record MuzzleSample(Vector3f viewRelative, long capturedAtMillis) {
    }
}
