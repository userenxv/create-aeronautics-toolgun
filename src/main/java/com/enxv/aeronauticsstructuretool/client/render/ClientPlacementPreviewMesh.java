package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.PreviewBlueprintData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

final class ClientPlacementPreviewMesh implements AutoCloseable {
    private static final float GHOST_ALPHA = 0.38F;
    private static final long BUILD_BUDGET_NANOS = 3_000_000L;
    private static final int MAX_BLOCKS_PER_FRAME = 512;
    private static PreviewBlueprintData cachedPreview;
    private static double cachedScaleFactor = Double.NaN;
    private static ClientPlacementPreviewMesh cachedMesh;
    private static BuildState pendingBuild;
    private static boolean cachedBuildComplete;

    private final VertexBuffer vertexBuffer;
    private final int blockCount;
    private final Vector3d boundsCenter;
    private final Vector3d halfExtents;

    private ClientPlacementPreviewMesh(
            VertexBuffer vertexBuffer,
            int blockCount,
            Vector3d boundsCenter,
            Vector3d halfExtents
    ) {
        this.vertexBuffer = vertexBuffer;
        this.blockCount = blockCount;
        this.boundsCenter = new Vector3d(boundsCenter);
        this.halfExtents = new Vector3d(halfExtents);
    }

    static boolean render(
            PoseStack poseStack,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            PreviewBlueprintData preview,
            Vector3d target,
            Quaterniond rootOrientation,
            double scaleFactor,
            boolean finalMainTarget
    ) {
        ClientPlacementPreviewMesh mesh = cacheFor(preview, scaleFactor);
        Minecraft minecraft = Minecraft.getInstance();
        if (mesh == null || minecraft.level == null) {
            return false;
        }
        poseStack.pushPose();
        poseStack.translate(target.x, target.y, target.z);
        poseStack.mulPose(new Quaternionf(rootOrientation));
        try {
            return SimpleWeldGhostRenderer.renderEngineeringMesh(
                    poseStack,
                    modelViewMatrix,
                    projectionMatrix,
                    minecraft,
                    mesh.vertexBuffer,
                    new Vector3d(0.0D, 1.0D, 0.0D),
                    new Vector3d(1.0D, 1.0D, 1.0D),
                    mesh.boundsCenter,
                    mesh.halfExtents,
                    finalMainTarget
            );
        } finally {
            poseStack.popPose();
        }
    }

    static boolean isBuilding() {
        return pendingBuild != null && !cachedBuildComplete;
    }

    static void clearCache() {
        ClientPlacementPreviewMesh previous = cachedMesh;
        BuildState previousBuild = pendingBuild;
        cachedMesh = null;
        pendingBuild = null;
        cachedBuildComplete = false;
        cachedPreview = null;
        cachedScaleFactor = Double.NaN;
        if (RenderSystem.isOnRenderThread()) {
            if (previous != null) {
                previous.close();
            }
            if (previousBuild != null) {
                previousBuild.close();
            }
        } else {
            RenderSystem.recordRenderCall(() -> {
                if (previous != null) {
                    previous.close();
                }
                if (previousBuild != null) {
                    previousBuild.close();
                }
            });
        }
    }

    @Override
    public void close() {
        this.vertexBuffer.close();
    }

    private static ClientPlacementPreviewMesh cacheFor(
            PreviewBlueprintData preview,
            double scaleFactor
    ) {
        boolean matchingKey = cachedPreview == preview
                && Double.doubleToLongBits(cachedScaleFactor) == Double.doubleToLongBits(scaleFactor);
        if (matchingKey && cachedBuildComplete) {
            return cachedMesh;
        }
        if (!matchingKey || pendingBuild == null) {
            clearCache();
            cachedPreview = preview;
            cachedScaleFactor = scaleFactor;
            pendingBuild = BuildState.begin(Minecraft.getInstance(), preview, scaleFactor);
        }
        try {
            pendingBuild.advance(Minecraft.getInstance());
            if (pendingBuild.complete()) {
                cachedMesh = pendingBuild.result();
                pendingBuild.close();
                pendingBuild = null;
                cachedBuildComplete = true;
            }
            return cachedMesh;
        } catch (RuntimeException | Error throwable) {
            AeronauticsStructureToolMod.LOGGER.warn(
                    "Failed to build cached engineering placement preview mesh",
                    throwable
            );
            clearCache();
            return null;
        }
    }

    private static final class BuildState implements AutoCloseable {
        private final PreviewBlueprintData preview;
        private final double scaleFactor;
        private final ClientLevel level;
        private final ByteBufferBuilder byteBuffer;
        private final BufferBuilder builder;
        private final VertexConsumer consumer;
        private final PoseStack modelPose = new PoseStack();
        private final Vector3d rootRelativePosition;
        private final Vector3d minimum = new Vector3d(Double.POSITIVE_INFINITY);
        private final Vector3d maximum = new Vector3d(Double.NEGATIVE_INFINITY);
        private int subLevelIndex;
        private int blockIndex;
        private int renderedBlockCount;
        private boolean complete;
        private boolean closed;
        private ClientPlacementPreviewMesh result;

        private BuildState(ClientLevel level, PreviewBlueprintData preview, double scaleFactor) {
            this.level = level;
            this.preview = preview;
            this.scaleFactor = scaleFactor;
            this.rootRelativePosition = preview.rootRelativePosition();
            this.byteBuffer = new ByteBufferBuilder(SimpleWeldGhostRenderer.GHOST_RENDER_TYPE.bufferSize());
            this.builder = new BufferBuilder(
                    this.byteBuffer,
                    SimpleWeldGhostRenderer.GHOST_RENDER_TYPE.mode(),
                    SimpleWeldGhostRenderer.GHOST_RENDER_TYPE.format()
            );
            this.consumer = SimpleWeldGhostRenderer.ghostVertexConsumer(this.builder, GHOST_ALPHA);
        }

        static BuildState begin(Minecraft minecraft, PreviewBlueprintData preview, double scaleFactor) {
            if (minecraft.level == null) {
                throw new IllegalStateException("placement preview requires a client level");
            }
            return new BuildState(minecraft.level, preview, scaleFactor);
        }

        void advance(Minecraft minecraft) {
            if (this.complete) {
                return;
            }
            long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
            int processed = 0;
            ModelBlockRenderer.enableCaching();
            try {
                while (this.subLevelIndex < this.preview.subLevels().size()
                        && processed < MAX_BLOCKS_PER_FRAME
                        && (processed == 0 || System.nanoTime() < deadline)) {
                    PreviewBlueprintData.PreviewSubLevel subLevel =
                            this.preview.subLevels().get(this.subLevelIndex);
                    if (this.blockIndex >= subLevel.previewBlocks().size()) {
                        this.subLevelIndex++;
                        this.blockIndex = 0;
                        continue;
                    }
                    PreviewBlueprintData.PreviewBlock block = subLevel.previewBlocks().get(this.blockIndex++);
                    Vector3d subAnchor = new Vector3d(subLevel.relativePosition())
                            .sub(this.rootRelativePosition)
                            .mul(this.scaleFactor);
                    Quaterniond subOrientation = new Quaterniond(subLevel.relativeOrientation());
                    Vector3d localPlotAnchor = subLevel.localPlotAnchor();
                    Vector3d localOffset = new Vector3d(block.center())
                            .sub(localPlotAnchor)
                            .mul(this.scaleFactor);
                    subOrientation.transform(localOffset);
                    Vector3d meshCenter = new Vector3d(subAnchor).add(localOffset);
                    includeBlockBounds(
                            this.minimum,
                            this.maximum,
                            meshCenter,
                            subOrientation,
                            this.scaleFactor
                    );
                    if (block.state().getRenderShape() == RenderShape.MODEL) {
                        appendBlockModel(
                                minecraft,
                                this.level,
                                this.modelPose,
                                this.consumer,
                                block,
                                meshCenter,
                                subOrientation,
                                this.scaleFactor
                        );
                        this.renderedBlockCount++;
                    }
                    processed++;
                }
            } finally {
                ModelBlockRenderer.clearCache();
            }
            if (this.subLevelIndex < this.preview.subLevels().size()) {
                return;
            }
            finish();
        }

        private void finish() {
            MeshData meshData = this.builder.build();
            if (meshData == null || this.renderedBlockCount == 0) {
                this.complete = true;
                return;
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
            Vector3d boundsCenter = this.minimum.add(this.maximum, new Vector3d()).mul(0.5D);
            Vector3d halfExtents = this.maximum.sub(this.minimum, new Vector3d()).mul(0.5D);
            AeronauticsStructureToolMod.LOGGER.debug(
                    "Built engineering placement preview GPU mesh with {} model blocks and {} vertices",
                    this.renderedBlockCount,
                    vertexCount
            );
            this.result = new ClientPlacementPreviewMesh(
                    vertexBuffer,
                    this.renderedBlockCount,
                    boundsCenter,
                    halfExtents
            );
            this.complete = true;
        }

        boolean complete() {
            return this.complete;
        }

        ClientPlacementPreviewMesh result() {
            return this.result;
        }

        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;
                this.byteBuffer.close();
            }
        }
    }

    private static void appendBlockModel(
            Minecraft minecraft,
            ClientLevel level,
            PoseStack modelPose,
            VertexConsumer consumer,
            PreviewBlueprintData.PreviewBlock block,
            Vector3d center,
            Quaterniond orientation,
            double scaleFactor
    ) {
        BlockPos renderPos = BlockPos.ZERO;
        BlockState state = block.state();
        BlockEntity blockEntity = loadBlockEntity(level, state, block.blockEntityTag());
        ModelData modelData = blockEntity != null ? blockEntity.getModelData() : ModelData.EMPTY;
        PreviewBlockView blockView = new PreviewBlockView(level, state, blockEntity, modelData);
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        modelData = model.getModelData(blockView, renderPos, state, modelData);
        long seed = state.getSeed(renderPos);

        modelPose.pushPose();
        modelPose.translate(center.x, center.y, center.z);
        modelPose.mulPose(new Quaternionf(orientation));
        modelPose.scale((float) scaleFactor, (float) scaleFactor, (float) scaleFactor);
        modelPose.translate(-0.5F, -0.5F, -0.5F);
        try {
            for (var sourceRenderType : model.getRenderTypes(state, RandomSource.create(seed), modelData)) {
                minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(
                        blockView,
                        model,
                        state,
                        renderPos,
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
        } finally {
            modelPose.popPose();
        }
    }

    private static @Nullable BlockEntity loadBlockEntity(
            ClientLevel level,
            BlockState state,
            @Nullable CompoundTag tag
    ) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        BlockEntity blockEntity = BlockEntity.loadStatic(BlockPos.ZERO, state, tag, level.registryAccess());
        if (blockEntity != null) {
            blockEntity.setLevel(level);
        }
        return blockEntity;
    }

    private static void includeBlockBounds(
            Vector3d minimum,
            Vector3d maximum,
            Vector3d center,
            Quaterniond orientation,
            double scaleFactor
    ) {
        double half = scaleFactor * 0.5D;
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    Vector3d corner = new Vector3d(x * half, y * half, z * half);
                    orientation.transform(corner);
                    corner.add(center);
                    minimum.min(corner);
                    maximum.max(corner);
                }
            }
        }
    }

    private static final class PreviewBlockView implements BlockAndTintGetter {
        private final ClientLevel delegate;
        private final BlockState state;
        private final @Nullable BlockEntity blockEntity;
        private final ModelData modelData;

        private PreviewBlockView(
                ClientLevel delegate,
                BlockState state,
                @Nullable BlockEntity blockEntity,
                ModelData modelData
        ) {
            this.delegate = delegate;
            this.state = state;
            this.blockEntity = blockEntity;
            this.modelData = modelData;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.delegate.getShade(direction, shade);
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.delegate.getBlockTint(pos, colorResolver);
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return pos.equals(BlockPos.ZERO) ? this.blockEntity : null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return pos.equals(BlockPos.ZERO) ? this.state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return pos.equals(BlockPos.ZERO) ? this.state.getFluidState() : Fluids.EMPTY.defaultFluidState();
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
            return pos.equals(BlockPos.ZERO) ? this.modelData : ModelData.EMPTY;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.delegate.getLightEngine();
        }
    }
}
