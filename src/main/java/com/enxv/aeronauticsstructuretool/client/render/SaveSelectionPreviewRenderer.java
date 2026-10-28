package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.ClientToolState;
import com.enxv.aeronauticsstructuretool.ToolMode;
import com.enxv.aeronauticsstructuretool.client.tool.ClientHeldToolState;
import com.enxv.aeronauticsstructuretool.client.tool.ClientSaveSelectionPreview;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.io.IOException;
import java.io.UncheckedIOException;

final class SaveSelectionPreviewRenderer {
    private static ShaderInstance shader;
    private static ByteBufferBuilder bytes;
    private static VertexBuffer vertices;

    private SaveSelectionPreviewRenderer() {
    }

    static void registerShader(RegisterShadersEvent event) {
        shader = null;
        clear();
        try {
            event.registerShader(new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "save_selection"),
                    DefaultVertexFormat.POSITION_COLOR
            ), loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load save-selection shader", exception);
        }
    }

    static void clear() {
        if (vertices != null) {
            vertices.close();
            vertices = null;
        }
        if (bytes != null) {
            bytes.close();
            bytes = null;
        }
    }

    static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (shader == null || minecraft.player == null || minecraft.level == null
                || !ClientHeldToolState.holdsStructureTool(minecraft.player)
                || ClientToolState.getMode() != ToolMode.SAVE) {
            return;
        }
        ClientSaveSelectionPreview.Selection selection = ClientSaveSelectionPreview.resolve(minecraft);
        if (selection == null) {
            return;
        }

        var bounds = selection.rangeBounds();
        Vector3d[] corners = {
                new Vector3d(bounds.minX(), bounds.minY(), bounds.minZ()),
                new Vector3d(bounds.maxX(), bounds.minY(), bounds.minZ()),
                new Vector3d(bounds.maxX(), bounds.minY(), bounds.maxZ()),
                new Vector3d(bounds.minX(), bounds.minY(), bounds.maxZ()),
                new Vector3d(bounds.minX(), bounds.maxY(), bounds.minZ()),
                new Vector3d(bounds.maxX(), bounds.maxY(), bounds.minZ()),
                new Vector3d(bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                new Vector3d(bounds.minX(), bounds.maxY(), bounds.maxZ())
        };
        Vec3 camera = event.getCamera().getPosition();
        // Subtract the camera in double precision before storing vertices as floats.
        for (Vector3d corner : corners) {
            corner.sub(camera.x, camera.y, camera.z);
        }
        PoseStack pose = new PoseStack();
        if (vertices == null) {
            bytes = new ByteBufferBuilder(12 * 24 * 16);
            vertices = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        ClientWorldRenderPrimitives.renderBoundsEdges(pose, builder, corners, 1.0F, 0.78F, 0.16F, 0.96F);
        var mesh = builder.build();
        if (mesh == null) {
            return;
        }
        RenderType renderType = RenderType.debugQuads();
        renderType.setupRenderState();
        try {
            RenderSystem.setShader(() -> shader);
            vertices.bind();
            vertices.upload(mesh);
            // Iris's final pass can leave fullscreen matrices active; use this world's matrices explicitly.
            vertices.drawWithShader(new Matrix4f(event.getModelViewMatrix()), event.getProjectionMatrix(), shader);
        } finally {
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }
}
