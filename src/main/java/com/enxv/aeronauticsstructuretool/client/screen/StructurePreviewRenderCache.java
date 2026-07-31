package com.enxv.aeronauticsstructuretool.client.screen;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.PortableStructurePreviewData;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StructurePreviewRenderCache implements AutoCloseable {
    private static final long BUILD_BUDGET_NANOS = 3_000_000L;
    private static final int MAX_BLOCKS_PER_FRAME = 512;
    private static final int BLOCKS_PER_GPU_BATCH = 4096;
    private static final int MAX_LARGE_PREVIEW_BLOCK_ENTITIES = 256;

    private final List<LayerMesh> layers;
    private final List<CachedBlockEntity> blockEntities;
    private final PortableStructurePreviewData preview;
    private final Map<RenderType, LayerBuilder> pendingBuilders = new LinkedHashMap<>();
    private final PoseStack poseStack = new PoseStack();
    private int nextBlockIndex;
    private int pendingBlockCount;
    private boolean complete;

    private StructurePreviewRenderCache(
            PortableStructurePreviewData preview
    ) {
        this.preview = preview;
        this.layers = new ArrayList<>();
        this.blockEntities = new ArrayList<>();
    }

    static StructurePreviewRenderCache begin(PortableStructurePreviewData preview) {
        return new StructurePreviewRenderCache(preview);
    }

    void advance(Minecraft minecraft) {
        if (this.complete || minecraft.level == null) {
            return;
        }
        long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
        int processed = 0;
        ModelBlockRenderer.enableCaching();
        try {
            List<PortableStructurePreviewData.PreviewBlock> blocks = this.preview.previewBlocks();
            while (this.nextBlockIndex < blocks.size()
                    && processed < MAX_BLOCKS_PER_FRAME
                    && (processed == 0 || System.nanoTime() < deadline)) {
                PortableStructurePreviewData.PreviewBlock block = blocks.get(this.nextBlockIndex++);
                PreviewRenderState renderState = createRenderState(minecraft, block);
                if (renderState.blockEntity() != null) {
                    this.blockEntities.add(new CachedBlockEntity(
                            block.position().x,
                            block.position().y,
                            block.position().z,
                            new Quaternionf(block.orientation()),
                            renderState.blockEntity()
                    ));
                }
                appendBlockModel(minecraft, block, renderState, this.poseStack, this.pendingBuilders);
                this.pendingBlockCount++;
                processed++;
                if (this.pendingBlockCount >= BLOCKS_PER_GPU_BATCH) {
                    flushPendingBatch();
                }
            }
            if (this.nextBlockIndex < blocks.size()) {
                return;
            }
            flushPendingBatch();
            this.complete = true;
            AeronauticsStructureToolMod.LOGGER.debug(
                    "Built GUI preview cache for '{}' with {} blocks, {} GPU mesh batch(es), and {} block entity renderer(s)",
                    this.preview.name(),
                    this.preview.previewBlocks().size(),
                    this.layers.size(),
                    this.blockEntities.size()
            );
        } finally {
            ModelBlockRenderer.clearCache();
        }
    }

    private void flushPendingBatch() {
        if (this.pendingBuilders.isEmpty()) {
            this.pendingBlockCount = 0;
            return;
        }
        try {
            this.layers.addAll(upload(this.pendingBuilders));
        } finally {
            this.pendingBuilders.values().forEach(LayerBuilder::close);
            this.pendingBuilders.clear();
            this.pendingBlockCount = 0;
        }
    }

    void renderModels(Matrix4f modelView, Matrix4f projection) {
        for (LayerMesh layer : this.layers) {
            layer.render(modelView, projection);
        }
    }

    void renderBlockEntities(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            Set<String> skippedBlockEntityTypes
    ) {
        if (this.blockEntities.isEmpty()) {
            return;
        }
        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        var bufferSource = minecraft.renderBuffers().bufferSource();
        int stride = Math.max(1, (int) Math.ceil(
                (double) this.blockEntities.size() / MAX_LARGE_PREVIEW_BLOCK_ENTITIES
        ));
        for (int index = 0; index < this.blockEntities.size(); index += stride) {
            CachedBlockEntity cached = this.blockEntities.get(index);
            String typeId = String.valueOf(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(
                            cached.blockEntity().getType()
                    )
            );
            if (skippedBlockEntityTypes.contains(typeId)) {
                continue;
            }
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(cached.x(), cached.y(), cached.z());
            guiGraphics.pose().mulPose(cached.orientation());
            guiGraphics.pose().translate(-0.5F, -0.5F, -0.5F);
            Lighting.setupForEntityInInventory();
            try {
                dispatcher.renderItem(
                        cached.blockEntity(),
                        guiGraphics.pose(),
                        bufferSource,
                        LightTexture.FULL_BRIGHT,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
                );
            } catch (Throwable throwable) {
                skippedBlockEntityTypes.add(typeId);
                AeronauticsStructureToolMod.LOGGER.warn(
                        "Skipping blueprint preview block entity render for {} after renderer failure",
                        typeId,
                        throwable
                );
            } finally {
                Lighting.setupFor3DItems();
                guiGraphics.pose().popPose();
            }
        }
        bufferSource.endBatch();
    }

    void closeOnRenderThread() {
        if (RenderSystem.isOnRenderThread()) {
            close();
        } else {
            RenderSystem.recordRenderCall(this::close);
        }
    }

    @Override
    public void close() {
        this.layers.forEach(LayerMesh::close);
        this.pendingBuilders.values().forEach(LayerBuilder::close);
        this.pendingBuilders.clear();
    }

    private static void appendBlockModel(
            Minecraft minecraft,
            PortableStructurePreviewData.PreviewBlock block,
            PreviewRenderState renderState,
            PoseStack poseStack,
            Map<RenderType, LayerBuilder> builders
    ) {
        BlockPos renderPos = BlockPos.ZERO;
        BlockState state = block.state();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        ModelData modelData = renderState.modelData();
        PreviewBlockAndTintGetter getter = new PreviewBlockAndTintGetter(
                minecraft.level,
                renderPos,
                state,
                renderState.blockEntity(),
                modelData
        );
        modelData = model.getModelData(getter, renderPos, state, modelData);
        long seed = state.getSeed(renderPos);
        poseStack.pushPose();
        poseStack.translate(block.position().x, block.position().y, block.position().z);
        poseStack.mulPose(new Quaternionf(block.orientation()));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        try {
            for (RenderType renderType : model.getRenderTypes(
                    state,
                    RandomSource.create(seed),
                    modelData
            )) {
                LayerBuilder layer = builders.computeIfAbsent(renderType, LayerBuilder::new);
                minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(
                        getter,
                        model,
                        state,
                        renderPos,
                        poseStack,
                        layer.builder(),
                        false,
                        RandomSource.create(),
                        seed,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        modelData,
                        renderType
                );
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static List<LayerMesh> upload(Map<RenderType, LayerBuilder> builders) {
        List<LayerMesh> meshes = new ArrayList<>(builders.size());
        try {
            for (Map.Entry<RenderType, LayerBuilder> entry : builders.entrySet()) {
                MeshData meshData = entry.getValue().builder().build();
                if (meshData == null) {
                    continue;
                }
                VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try {
                    vertexBuffer.bind();
                    vertexBuffer.upload(meshData);
                    meshes.add(new LayerMesh(entry.getKey(), vertexBuffer));
                } catch (RuntimeException | Error throwable) {
                    vertexBuffer.close();
                    throw throwable;
                } finally {
                    VertexBuffer.unbind();
                }
            }
            return List.copyOf(meshes);
        } catch (RuntimeException | Error throwable) {
            meshes.forEach(LayerMesh::close);
            throw throwable;
        }
    }

    private static PreviewRenderState createRenderState(
            Minecraft minecraft,
            PortableStructurePreviewData.PreviewBlock block
    ) {
        CompoundTag blockEntityTag = block.blockEntityTag();
        if (blockEntityTag == null || blockEntityTag.isEmpty() || minecraft.level == null) {
            return new PreviewRenderState(null, ModelData.EMPTY);
        }
        BlockEntity blockEntity = BlockEntity.loadStatic(
                BlockPos.ZERO,
                block.state(),
                blockEntityTag,
                minecraft.level.registryAccess()
        );
        if (blockEntity == null) {
            return new PreviewRenderState(null, ModelData.EMPTY);
        }
        blockEntity.setLevel(minecraft.level);
        return new PreviewRenderState(blockEntity, blockEntity.getModelData());
    }

    private record PreviewRenderState(@Nullable BlockEntity blockEntity, ModelData modelData) {
    }

    private record CachedBlockEntity(
            double x,
            double y,
            double z,
            Quaternionf orientation,
            BlockEntity blockEntity
    ) {
    }

    private record LayerMesh(RenderType renderType, VertexBuffer vertexBuffer) implements AutoCloseable {
        void render(Matrix4f modelView, Matrix4f projection) {
            this.renderType.setupRenderState();
            try {
                // GUI previews flip Y before applying interactive rotation. Cached vertices therefore
                // need two-sided rasterization; otherwise the visible side swaps as the model turns.
                RenderSystem.disableCull();
                ShaderInstance shader = RenderSystem.getShader();
                if (shader != null) {
                    this.vertexBuffer.bind();
                    this.vertexBuffer.drawWithShader(modelView, projection, shader);
                }
            } finally {
                VertexBuffer.unbind();
                this.renderType.clearRenderState();
                RenderSystem.enableCull();
            }
        }

        @Override
        public void close() {
            this.vertexBuffer.close();
        }
    }

    private static final class LayerBuilder implements AutoCloseable {
        private final ByteBufferBuilder byteBuffer;
        private final BufferBuilder builder;

        private LayerBuilder(RenderType renderType) {
            this.byteBuffer = new ByteBufferBuilder(renderType.bufferSize());
            this.builder = new BufferBuilder(
                    this.byteBuffer,
                    renderType.mode(),
                    renderType.format()
            );
        }

        BufferBuilder builder() {
            return this.builder;
        }

        @Override
        public void close() {
            this.byteBuffer.close();
        }
    }

    private static final class PreviewBlockAndTintGetter implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private final BlockPos renderPos;
        private final BlockState renderState;
        private final @Nullable BlockEntity blockEntity;
        private final ModelData modelData;

        private PreviewBlockAndTintGetter(
                BlockAndTintGetter delegate,
                BlockPos renderPos,
                BlockState renderState,
                @Nullable BlockEntity blockEntity,
                ModelData modelData
        ) {
            this.delegate = delegate;
            this.renderPos = renderPos;
            this.renderState = renderState;
            this.blockEntity = blockEntity;
            this.modelData = modelData;
        }

        @Override
        public float getShade(net.minecraft.core.Direction direction, boolean shade) {
            return this.delegate.getShade(direction, shade);
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.delegate.getBlockTint(pos, colorResolver);
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return pos.equals(this.renderPos) ? this.blockEntity : this.delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return pos.equals(this.renderPos) ? this.renderState : this.delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return pos.equals(this.renderPos) ? this.renderState.getFluidState() : this.delegate.getFluidState(pos);
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
            return pos.equals(this.renderPos) ? this.modelData : ModelData.EMPTY;
        }

        @Override
        public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
            return this.delegate.getLightEngine();
        }
    }
}
