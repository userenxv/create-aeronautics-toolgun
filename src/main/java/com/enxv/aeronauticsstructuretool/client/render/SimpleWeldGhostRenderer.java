package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

final class SimpleWeldGhostRenderer {
    private static final float GHOST_ALPHA = 0.38F;
    private static final float SCAN_PERIOD_SECONDS = 1.5F;
    private static final float SCAN_WIDTH_BLOCKS = 0.28F;
    static final RenderType GHOST_RENDER_TYPE = NeoForgeRenderTypes.TRANSLUCENT_ON_PARTICLES_TARGET.get();

    private static volatile ShaderInstance engineeringShader;
    private static GhostMesh cachedMesh;
    private static UUID failedSubLevelId;
    private static ClientLevel failedLevel;
    private SimpleWeldGhostRenderer() {
    }

    static void registerShaders(RegisterShadersEvent event) {
        engineeringShader = null;
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "simple_weld_ghost"),
                            DefaultVertexFormat.BLOCK
                    ),
                    shader -> engineeringShader = shader
            );
        } catch (IOException exception) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Engineering simple-weld ghost shader is unavailable; using the textured fallback",
                    exception
            );
        }
    }

    static boolean render(
            PoseStack poseStack,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            Minecraft minecraft,
            SubLevel subLevel,
            Vector3d previewPosition,
            Quaterniond previewOrientation,
            Vector3d scanAxisLocal,
            boolean finalMainTarget
    ) {
        if (minecraft.level == null || subLevel.getPlot() == null || subLevel.getPlot().getBoundingBox() == null) {
            clear();
            return false;
        }

        UUID subLevelId = subLevel.getUniqueId();
        ClientLevel level = minecraft.level;
        if (cachedMesh != null && (!cachedMesh.subLevelId().equals(subLevelId) || cachedMesh.level() != level)) {
            clear();
        }
        if (cachedMesh == null && (failedLevel != level || !subLevelId.equals(failedSubLevelId))) {
            try {
                GhostMesh builtMesh = buildMesh(minecraft, subLevel);
                if (builtMesh != null) {
                    cachedMesh = builtMesh;
                    failedSubLevelId = null;
                    failedLevel = null;
                } else {
                    failedSubLevelId = subLevelId;
                    failedLevel = level;
                }
            } catch (Throwable throwable) {
                failedSubLevelId = subLevelId;
                failedLevel = level;
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Unable to build textured simple-weld ghost for sublevel {}",
                        subLevelId,
                        throwable
                );
            }
        }
        if (cachedMesh == null) {
            return false;
        }
        return cachedMesh.render(
                poseStack,
                modelViewMatrix,
                projectionMatrix,
                minecraft,
                subLevel,
                previewPosition,
                previewOrientation,
                scanAxisLocal,
                finalMainTarget,
                EffectProfile.GHOST
        );
    }

    static boolean shouldRenderAfterLevel() {
        return IrisCompat.isShaderPackInUse();
    }

    static void clear() {
        clearSimpleWeld();
    }

    static void clearSimpleWeld() {
        GhostMesh mesh = cachedMesh;
        cachedMesh = null;
        failedSubLevelId = null;
        failedLevel = null;
        if (mesh == null) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            mesh.close();
        } else {
            RenderSystem.recordRenderCall(mesh::close);
        }
    }

    static VertexConsumer ghostVertexConsumer(VertexConsumer delegate, float alphaScale) {
        return new GhostVertexConsumer(delegate, alphaScale, 0.0D, 0.0D, 0.0D);
    }

    static boolean renderEngineeringMesh(
            PoseStack poseStack,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            Minecraft minecraft,
            VertexBuffer vertexBuffer,
            Vector3d requestedScanAxis,
            Vector3d scale,
            Vector3d boundsCenter,
            Vector3d halfExtents,
            boolean finalMainTarget
    ) {
        return renderEngineeringMesh(
                poseStack,
                modelViewMatrix,
                projectionMatrix,
                minecraft,
                vertexBuffer,
                requestedScanAxis,
                scale,
                boundsCenter,
                halfExtents,
                finalMainTarget,
                EffectProfile.GHOST
        );
    }

    private static boolean renderEngineeringMesh(
            PoseStack poseStack,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            Minecraft minecraft,
            VertexBuffer vertexBuffer,
            Vector3d requestedScanAxis,
            Vector3d scale,
            Vector3d boundsCenter,
            Vector3d halfExtents,
            boolean finalMainTarget,
            EffectProfile profile
    ) {
        Vector3d scanAxis = requestedScanAxis != null
                ? new Vector3d(requestedScanAxis)
                : new Vector3d(0.0D, 1.0D, 0.0D);
        if (scanAxis.lengthSquared() <= 1.0E-8D) {
            scanAxis.set(0.0D, 1.0D, 0.0D);
        } else {
            scanAxis.normalize();
        }

        float[] previousColor = RenderSystem.getShaderColor();
        float previousRed = previousColor[0];
        float previousGreen = previousColor[1];
        float previousBlue = previousColor[2];
        float previousAlpha = previousColor[3];
        boolean rendered = false;
        if (finalMainTarget) {
            setupFinalMainTarget(minecraft);
        } else {
            GHOST_RENDER_TYPE.setupRenderState();
        }
        try {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            ShaderInstance shader = engineeringShader;
            if (shader != null) {
                ShaderInstance selectedShader = shader;
                RenderSystem.setShader(() -> selectedShader);
                configureEngineeringShader(shader, scanAxis, scale, boundsCenter, halfExtents, profile);
            } else if (!finalMainTarget) {
                shader = RenderSystem.getShader();
            }
            if (shader != null) {
                vertexBuffer.bind();
                Matrix4f combinedModelView = new Matrix4f(modelViewMatrix).mul(poseStack.last().pose());
                vertexBuffer.drawWithShader(combinedModelView, projectionMatrix, shader);
                rendered = true;
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(previousRed, previousGreen, previousBlue, previousAlpha);
            if (finalMainTarget) {
                clearFinalMainTarget(minecraft);
            } else {
                GHOST_RENDER_TYPE.clearRenderState();
            }
        }
        return rendered;
    }

    private static GhostMesh buildMesh(Minecraft minecraft, SubLevel subLevel) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }

        var bounds = subLevel.getPlot().getBoundingBox();
        Vector3d anchor = new Vector3d(
                (bounds.minX() + bounds.maxX() + 1.0D) * 0.5D,
                (bounds.minY() + bounds.maxY() + 1.0D) * 0.5D,
                (bounds.minZ() + bounds.maxZ() + 1.0D) * 0.5D
        );
        Vector3d halfExtents = new Vector3d(
                (bounds.maxX() - bounds.minX() + 1.0D) * 0.5D,
                (bounds.maxY() - bounds.minY() + 1.0D) * 0.5D,
                (bounds.maxZ() - bounds.minZ() + 1.0D) * 0.5D
        );
        FullBrightBlockView blockView = new FullBrightBlockView(level);
        int renderedBlocks = 0;

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(GHOST_RENDER_TYPE.bufferSize())) {
            BufferBuilder builder = new BufferBuilder(byteBuffer, GHOST_RENDER_TYPE.mode(), GHOST_RENDER_TYPE.format());
            VertexConsumer modelConsumer = new GhostVertexConsumer(builder, GHOST_ALPHA, 0.0D, 0.0D, 0.0D);
            PoseStack modelPose = new PoseStack();
            ModelBlockRenderer.enableCaching();
            try {
                for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
                    LevelChunk chunk = holder.getChunk();
                    LevelChunkSection[] sections = chunk.getSections();
                    int chunkX = chunk.getPos().getMinBlockX();
                    int chunkZ = chunk.getPos().getMinBlockZ();
                    for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                        LevelChunkSection section = sections[sectionIndex];
                        if (section == null || section.hasOnlyAir()) {
                            continue;
                        }
                        int sectionY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
                        VertexConsumer fluidConsumer = new GhostVertexConsumer(
                                builder,
                                GHOST_ALPHA,
                                chunkX - anchor.x,
                                sectionY - anchor.y,
                                chunkZ - anchor.z
                        );
                        for (int localY = 0; localY < 16; localY++) {
                            for (int localZ = 0; localZ < 16; localZ++) {
                                for (int localX = 0; localX < 16; localX++) {
                                    BlockState state = section.getBlockState(localX, localY, localZ);
                                    FluidState fluidState = state.getFluidState();
                                    if (state.isAir() && fluidState.isEmpty()) {
                                        continue;
                                    }

                                    BlockPos blockPos = new BlockPos(chunkX + localX, sectionY + localY, chunkZ + localZ);
                                    if (state.getRenderShape() == RenderShape.MODEL) {
                                        renderBlockModel(
                                                minecraft,
                                                blockView,
                                                modelPose,
                                                modelConsumer,
                                                blockPos,
                                                state,
                                                anchor
                                        );
                                        renderedBlocks++;
                                    }
                                    if (!fluidState.isEmpty()) {
                                        minecraft.getBlockRenderer().getLiquidBlockRenderer().tesselate(
                                                blockView,
                                                blockPos,
                                                fluidConsumer,
                                                state,
                                                fluidState
                                        );
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                ModelBlockRenderer.clearCache();
            }

            MeshData meshData = builder.build();
            if (meshData == null) {
                return null;
            }
            int vertexCount = meshData.drawState().vertexCount();
            VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            try {
                vertexBuffer.bind();
                vertexBuffer.upload(meshData);
            } catch (RuntimeException | Error throwable) {
                vertexBuffer.close();
                throw throwable;
            } finally {
                VertexBuffer.unbind();
            }
            AeronauticsStructureToolMod.LOGGER.debug(
                    "Built simple-weld ghost for sublevel {} with {} model blocks and {} vertices",
                    subLevel.getUniqueId(),
                    renderedBlocks,
                    vertexCount
            );
            return new GhostMesh(subLevel.getUniqueId(), level, anchor, halfExtents, vertexBuffer);
        }
    }

    private static void renderBlockModel(
            Minecraft minecraft,
            FullBrightBlockView blockView,
            PoseStack modelPose,
            VertexConsumer consumer,
            BlockPos blockPos,
            BlockState state,
            Vector3d anchor
    ) {
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        ModelData modelData = minecraft.level != null ? minecraft.level.getModelData(blockPos) : ModelData.EMPTY;
        modelData = model.getModelData(blockView, blockPos, state, modelData);
        long seed = state.getSeed(blockPos);

        modelPose.pushPose();
        modelPose.translate(blockPos.getX() - anchor.x, blockPos.getY() - anchor.y, blockPos.getZ() - anchor.z);
        for (RenderType sourceRenderType : model.getRenderTypes(state, RandomSource.create(seed), modelData)) {
            minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(
                    blockView,
                    model,
                    state,
                    blockPos,
                    modelPose,
                    consumer,
                    false,
                    RandomSource.create(),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    modelData,
                    sourceRenderType
            );
        }
        modelPose.popPose();
    }

    private record GhostMesh(
            UUID subLevelId,
            ClientLevel level,
            Vector3d anchor,
            Vector3d halfExtents,
            VertexBuffer vertexBuffer
    ) implements AutoCloseable {
        boolean render(
                PoseStack poseStack,
                Matrix4f modelViewMatrix,
                Matrix4f projectionMatrix,
                Minecraft minecraft,
                SubLevel subLevel,
                Vector3d previewPosition,
                Quaterniond previewOrientation,
                Vector3d requestedScanAxis,
                boolean finalMainTarget,
                EffectProfile profile
        ) {
            Vector3d scale = new Vector3d(subLevel.logicalPose().scale());
            Vector3d worldAnchor = new Vector3d(this.anchor)
                    .sub(subLevel.logicalPose().rotationPoint())
                    .mul(scale);
            previewOrientation.transform(worldAnchor);
            worldAnchor.add(previewPosition);

            poseStack.pushPose();
            poseStack.translate(worldAnchor.x, worldAnchor.y, worldAnchor.z);
            poseStack.mulPose(new Quaternionf(previewOrientation));
            poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);
            try {
                return renderEngineeringMesh(
                        poseStack,
                        modelViewMatrix,
                        projectionMatrix,
                        minecraft,
                        this.vertexBuffer,
                        requestedScanAxis,
                        scale,
                        new Vector3d(),
                        this.halfExtents,
                        finalMainTarget,
                        profile
                );
            } finally {
                poseStack.popPose();
            }
        }

        @Override
        public void close() {
            this.vertexBuffer.close();
        }
    }

    private static void setupFinalMainTarget(Minecraft minecraft) {
        minecraft.getMainRenderTarget().bindWrite(false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        minecraft.gameRenderer.lightTexture().turnOnLightLayer();
    }

    private static void clearFinalMainTarget(Minecraft minecraft) {
        minecraft.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
    }

    private static void configureEngineeringShader(
            ShaderInstance shader,
            Vector3d scanAxis,
            Vector3d scale,
            Vector3d boundsCenter,
            Vector3d halfExtents,
            EffectProfile profile
    ) {
        float timeSeconds = (System.nanoTime() % 60_000_000_000L) / 1_000_000_000.0F;
        float phase = (timeSeconds % SCAN_PERIOD_SECONDS) / SCAN_PERIOD_SECONDS;
        double scanScale = new Vector3d(scanAxis).mul(scale).length();
        float scanWidth = (float) (SCAN_WIDTH_BLOCKS / Math.max(scanScale, 1.0E-4D));
        float extent = (float) (
                Math.abs(scanAxis.x) * halfExtents.x
                        + Math.abs(scanAxis.y) * halfExtents.y
                        + Math.abs(scanAxis.z) * halfExtents.z
        );
        float scanCenter = (float) scanAxis.dot(boundsCenter);
        float scanOffset = scanCenter + Mth.lerp(phase, -extent - scanWidth, extent + scanWidth);

        setUniform(shader, "GhostTime", timeSeconds);
        setUniform(shader, "ScanAxis", (float) scanAxis.x, (float) scanAxis.y, (float) scanAxis.z);
        setUniform(shader, "ScanOffset", scanOffset);
        setUniform(shader, "ScanWidth", scanWidth);
        setUniform(shader, "EdgeColor", profile.edgeRed, profile.edgeGreen, profile.edgeBlue);
        setUniform(shader, "EffectStrength", profile.effectStrength);
        setUniform(shader, "BaseAlpha", profile.baseAlpha);
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setUniform(ShaderInstance shader, String name, float x, float y, float z) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    private enum EffectProfile {
        GHOST(0.333F, 0.902F, 1.0F, 0.88F, 1.0F);

        private final float edgeRed;
        private final float edgeGreen;
        private final float edgeBlue;
        private final float effectStrength;
        private final float baseAlpha;

        EffectProfile(float edgeRed, float edgeGreen, float edgeBlue, float effectStrength, float baseAlpha) {
            this.edgeRed = edgeRed;
            this.edgeGreen = edgeGreen;
            this.edgeBlue = edgeBlue;
            this.effectStrength = effectStrength;
            this.baseAlpha = baseAlpha;
        }
    }

    private static final class IrisCompat {
        private static final IrisApiAccess ACCESS = findApi();
        private static boolean invocationFailed;

        private IrisCompat() {
        }

        private static boolean isShaderPackInUse() {
            if (ACCESS == null || invocationFailed) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(ACCESS.isShaderPackInUse().invoke(ACCESS.instance()));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                invocationFailed = true;
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Unable to query Iris shader-pack state; using the standard ghost render stage",
                        exception
                );
                return false;
            }
        }

        private static IrisApiAccess findApi() {
            try {
                Class<?> apiClass = Class.forName(
                        "net.irisshaders.iris.api.v0.IrisApi",
                        false,
                        SimpleWeldGhostRenderer.class.getClassLoader()
                );
                Method getInstance = apiClass.getMethod("getInstance");
                Method isShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
                return new IrisApiAccess(getInstance.invoke(null), isShaderPackInUse);
            } catch (ClassNotFoundException exception) {
                return null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Iris was found but its shader-pack API could not be initialized",
                        exception
                );
                return null;
            }
        }

        private record IrisApiAccess(Object instance, Method isShaderPackInUse) {
        }
    }

    private static final class GhostVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alphaScale;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;

        private GhostVertexConsumer(VertexConsumer delegate, float alphaScale, double offsetX, double offsetY, double offsetZ) {
            this.delegate = delegate;
            this.alphaScale = alphaScale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex((float) (x + this.offsetX), (float) (y + this.offsetY), (float) (z + this.offsetZ));
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, Mth.clamp(Math.round(alpha * this.alphaScale), 0, 255));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }

    private static final class FullBrightBlockView implements BlockAndTintGetter {
        private final ClientLevel delegate;

        private FullBrightBlockView(ClientLevel delegate) {
            this.delegate = delegate;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.delegate.getBlockTint(pos, colorResolver);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.delegate.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return this.delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.delegate.getMinBuildHeight();
        }

        @Override
        public int getBrightness(LightLayer lightLayer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return 15;
        }

        @Override
        public boolean canSeeSky(BlockPos pos) {
            return true;
        }

        @Override
        public ModelData getModelData(BlockPos pos) {
            return this.delegate.getModelData(pos);
        }
    }
}
