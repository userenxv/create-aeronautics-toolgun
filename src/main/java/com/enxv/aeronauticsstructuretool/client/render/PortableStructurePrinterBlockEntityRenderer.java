package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.client.ClientHooks;
import com.enxv.aeronauticsstructuretool.printer.PortableStructurePrinterBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class PortableStructurePrinterBlockEntityRenderer implements BlockEntityRenderer<PortableStructurePrinterBlockEntity> {
    public PortableStructurePrinterBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PortableStructurePrinterBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 1.25D / 16.0D, 0.0D);
        ClientHooks.renderPortableStructureContainerStatic(poseStack, buffer, packedLight, packedOverlay, blockEntity.hasBlueprint());
        poseStack.popPose();

        PortableStructurePrinterClientEffects.render(
                blockEntity.getBlockPos(),
                poseStack,
                buffer
        );
    }
}
