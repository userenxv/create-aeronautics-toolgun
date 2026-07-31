package com.enxv.aeronauticsstructuretool.client.render;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.ModItems;
import com.enxv.aeronauticsstructuretool.PortableStructureContainerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ClientItemRenderRegistry {
    private static final ModelResourceLocation PRINT_BODY_MODEL = model("item/print_gun_body");
    private static final ModelResourceLocation PRINT_ENERGY_MODEL = model("item/print_gun_energy");
    private static final ModelResourceLocation PRINT_FOCUS_MODEL = model("item/print_gun_focus");
    private static final ModelResourceLocation PRINT_RIGHT_LOWER_RAIL_MODEL = model("item/print_gun_right_lower_rail");
    private static final ModelResourceLocation PRINT_RIGHT_UPPER_RAIL_MODEL = model("item/print_gun_right_upper_rail");
    private static final ModelResourceLocation SURVIVAL_PRINT_BODY_MODEL = model("item/survival_structure_tool_body");
    private static final ModelResourceLocation SURVIVAL_PRINT_ENERGY_MODEL = model("item/survival_structure_tool_energy");
    private static final ModelResourceLocation SURVIVAL_PRINT_FOCUS_MODEL = model("item/survival_structure_tool_focus");
    private static final ModelResourceLocation SURVIVAL_PRINT_RIGHT_LOWER_RAIL_MODEL = model("item/survival_structure_tool_right_lower_rail");
    private static final ModelResourceLocation SURVIVAL_PRINT_RIGHT_UPPER_RAIL_MODEL = model("item/survival_structure_tool_right_upper_rail");
    private static final ModelResourceLocation MAGNETIC_BODY_MODEL = model("item/magnetic_gun_body");
    private static final ModelResourceLocation MAGNETIC_ENERGY_MODEL = model("item/magnetic_gun_energy");
    private static final ModelResourceLocation MAGNETIC_TOP_CLAW_MODEL = model("item/magnetic_gun_claw_top");
    private static final ModelResourceLocation MAGNETIC_LEFT_CLAW_MODEL = model("item/magnetic_gun_claw_left");
    private static final ModelResourceLocation MAGNETIC_RIGHT_CLAW_MODEL = model("item/magnetic_gun_claw_right");
    private static final ModelResourceLocation CREATIVE_MAGNETIC_BODY_MODEL = model("item/creative_magnetic_gun_body");
    private static final ModelResourceLocation CREATIVE_MAGNETIC_ENERGY_MODEL = model("item/creative_magnetic_gun_energy");
    private static final ModelResourceLocation CREATIVE_MAGNETIC_TOP_CLAW_MODEL = model("item/creative_magnetic_gun_claw_top");
    private static final ModelResourceLocation CREATIVE_MAGNETIC_LEFT_CLAW_MODEL = model("item/creative_magnetic_gun_claw_left");
    private static final ModelResourceLocation CREATIVE_MAGNETIC_RIGHT_CLAW_MODEL = model("item/creative_magnetic_gun_claw_right");
    private static final Vector3f ENERGY_PIVOT = pixels(8.0F, 8.75F, 13.0F);
    private static final Vector3f RIGHT_LOWER_RAIL_PIVOT = pixels(11.0F, 6.75F, 0.0F);
    private static final Vector3f RIGHT_UPPER_RAIL_PIVOT = pixels(11.0F, 11.0F, 0.0F);
    private static final Vector3f TOP_CLAW_PIVOT = pixels(8.0F, 11.2F, 6.6F);
    private static final Vector3f LEFT_CLAW_PIVOT = pixels(5.1F, 6.3F, 5.0F);
    private static final Vector3f RIGHT_CLAW_PIVOT = pixels(10.9F, 6.3F, 5.0F);
    private static final Vector3f TOOL_MUZZLE_POINT = pixels(8.0F, 8.875F, -0.85F);
    private static final Vector3f MAGNETIC_MUZZLE_POINT = pixels(8.0F, 8.93F, -0.9F);
    private static final Vector3f MODEL_ORIGIN = new Vector3f();
    private static final float ENERGY_IDLE_SPEED_DEGREES_PER_SECOND = 90.0F;
    private static final float FOCUS_TRAVEL_PIXELS = 1.1F;
    private static final float FOCUS_ACTIVE_TRAVEL_PIXELS = 1.75F;
    private static final float FOCUS_CYCLES_PER_SECOND = 0.72F;
    private static final float TOP_CLAW_OPEN_DEGREES = 38.0F;
    private static final float SIDE_CLAW_OPEN_DEGREES = 32.0F;

    private ClientItemRenderRegistry() {
    }

    public static void pulse(InteractionHand hand) {
        RotationPulseState.pulse(hand);
    }

    public static void sustain(InteractionHand hand) {
        RotationPulseState.sustain(hand);
    }

    public static void stopSustain(InteractionHand hand) {
        RotationPulseState.stopSustain(hand);
    }

    public static void launch(InteractionHand hand) {
        RotationPulseState.stopSustain(hand);
        RotationPulseState.pulse(hand);
    }


    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(clientExtensions(toolGunRenderer(
                PRINT_BODY_MODEL,
                PRINT_ENERGY_MODEL,
                PRINT_FOCUS_MODEL,
                PRINT_RIGHT_LOWER_RAIL_MODEL,
                PRINT_RIGHT_UPPER_RAIL_MODEL,
                "print_gun"
        )), ModItems.STRUCTURE_TOOL.get());
        event.registerItem(clientExtensions(toolGunRenderer(
                SURVIVAL_PRINT_BODY_MODEL,
                SURVIVAL_PRINT_ENERGY_MODEL,
                SURVIVAL_PRINT_FOCUS_MODEL,
                SURVIVAL_PRINT_RIGHT_LOWER_RAIL_MODEL,
                SURVIVAL_PRINT_RIGHT_UPPER_RAIL_MODEL,
                "survival_print_gun"
        )), ModItems.SURVIVAL_STRUCTURE_TOOL.get());
        event.registerItem(clientExtensions(magneticGunRenderer(
                MAGNETIC_BODY_MODEL,
                MAGNETIC_ENERGY_MODEL,
                MAGNETIC_TOP_CLAW_MODEL,
                MAGNETIC_LEFT_CLAW_MODEL,
                MAGNETIC_RIGHT_CLAW_MODEL,
                "magnetic_gun"
        )), ModItems.MAGNETIC_GUN.get());
        event.registerItem(clientExtensions(magneticGunRenderer(
                CREATIVE_MAGNETIC_BODY_MODEL,
                CREATIVE_MAGNETIC_ENERGY_MODEL,
                CREATIVE_MAGNETIC_TOP_CLAW_MODEL,
                CREATIVE_MAGNETIC_LEFT_CLAW_MODEL,
                CREATIVE_MAGNETIC_RIGHT_CLAW_MODEL,
                "creative_magnetic_gun"
        )), ModItems.CREATIVE_MAGNETIC_GUN.get());
        event.registerItem(
                clientExtensions(new PortableStructureContainerRenderer()),
                ModItems.PORTABLE_STRUCTURE_CONTAINER.get(),
                ModItems.DISPOSABLE_VEHICLE_CONTAINER.get()
        );
    }

    private static IClientItemExtensions clientExtensions(BlockEntityWithoutLevelRenderer renderer) {
        return new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }

            @Override
            public boolean applyForgeHandTransform(
                    PoseStack poseStack,
                    LocalPlayer player,
                    HumanoidArm arm,
                    ItemStack itemInHand,
                    float partialTick,
                    float equipProcess,
                    float swingProcess
            ) {
                applyStableHandPose(poseStack, arm);
                return true;
            }
        };
    }

    private static ToolGunRenderer toolGunRenderer(
            ModelResourceLocation body,
            ModelResourceLocation energy,
            ModelResourceLocation focus,
            ModelResourceLocation rightLowerRail,
            ModelResourceLocation rightUpperRail,
            String animationKey
    ) {
        return new ToolGunRenderer(body, energy, focus, rightLowerRail, rightUpperRail, animationKey);
    }

    private static MagneticGunRenderer magneticGunRenderer(
            ModelResourceLocation body,
            ModelResourceLocation energy,
            ModelResourceLocation topClaw,
            ModelResourceLocation leftClaw,
            ModelResourceLocation rightClaw,
            String animationKey
    ) {
        return new MagneticGunRenderer(body, energy, topClaw, leftClaw, rightClaw, animationKey);
    }

    private static void applyStableHandPose(PoseStack poseStack, HumanoidArm arm) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(side * 0.56F, -0.52F, -0.72F);
        poseStack.translate(side * -0.02F, -0.02F, 0.02F);
    }

    private static void captureMuzzlePosition(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            Vector3f localMuzzlePoint
    ) {
        if (displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return;
        }
        InteractionHand renderedHand = resolveRenderedHand(stack, displayContext);
        if (renderedHand != null) {
            ClientBeamRenderer.captureMuzzle(renderedHand, poseStack.last().pose(), localMuzzlePoint);
        }
    }

    private static InteractionHand resolveRenderedHand(ItemStack stack, ItemDisplayContext displayContext) {
        if (displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && displayContext != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                && displayContext != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        if (stack == minecraft.player.getMainHandItem()) {
            return InteractionHand.MAIN_HAND;
        }
        if (stack == minecraft.player.getOffhandItem()) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, path)
        );
    }

    private static Vector3f pixels(float x, float y, float z) {
        return new Vector3f(x / 16.0F, y / 16.0F, z / 16.0F);
    }

    private static void renderModel(
            ItemRenderer itemRenderer,
            BakedModel model,
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay
    ) {
        for (BakedModel renderPass : model.getRenderPasses(stack, true)) {
            for (RenderType renderType : renderPass.getRenderTypes(stack, true)) {
                VertexConsumer vertexConsumer = ItemRenderer.getFoilBufferDirect(
                        buffer,
                        renderType,
                        true,
                        stack.hasFoil()
                );
                itemRenderer.renderModelLists(renderPass, stack, light, overlay, poseStack, vertexConsumer);
            }
        }
    }

    private static void renderTransformedModel(
            Minecraft minecraft,
            ModelManager modelManager,
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay,
            ModelResourceLocation modelLocation,
            Vector3f pivot,
            float offsetX,
            float offsetY,
            float offsetZ,
            float rotationX,
            float rotationY,
            float rotationZ
    ) {
        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, offsetZ);
        poseStack.translate(pivot.x(), pivot.y(), pivot.z());
        if (rotationY != 0.0F) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotationY));
        }
        if (rotationX != 0.0F) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotationX));
        }
        if (rotationZ != 0.0F) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotationZ));
        }
        poseStack.translate(-pivot.x(), -pivot.y(), -pivot.z());
        renderModel(
                minecraft.getItemRenderer(),
                modelManager.getModel(modelLocation),
                stack,
                poseStack,
                buffer,
                light,
                overlay
        );
        poseStack.popPose();
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(PRINT_BODY_MODEL);
        event.register(PRINT_ENERGY_MODEL);
        event.register(PRINT_FOCUS_MODEL);
        event.register(PRINT_RIGHT_LOWER_RAIL_MODEL);
        event.register(PRINT_RIGHT_UPPER_RAIL_MODEL);
        event.register(SURVIVAL_PRINT_BODY_MODEL);
        event.register(SURVIVAL_PRINT_ENERGY_MODEL);
        event.register(SURVIVAL_PRINT_FOCUS_MODEL);
        event.register(SURVIVAL_PRINT_RIGHT_LOWER_RAIL_MODEL);
        event.register(SURVIVAL_PRINT_RIGHT_UPPER_RAIL_MODEL);
        event.register(MAGNETIC_BODY_MODEL);
        event.register(MAGNETIC_ENERGY_MODEL);
        event.register(MAGNETIC_TOP_CLAW_MODEL);
        event.register(MAGNETIC_LEFT_CLAW_MODEL);
        event.register(MAGNETIC_RIGHT_CLAW_MODEL);
        event.register(CREATIVE_MAGNETIC_BODY_MODEL);
        event.register(CREATIVE_MAGNETIC_ENERGY_MODEL);
        event.register(CREATIVE_MAGNETIC_TOP_CLAW_MODEL);
        event.register(CREATIVE_MAGNETIC_LEFT_CLAW_MODEL);
        event.register(CREATIVE_MAGNETIC_RIGHT_CLAW_MODEL);
        event.register(PortableStructureContainerRenderer.GROUP1_MODEL);
        event.register(PortableStructureContainerRenderer.GROUP2_MODEL);
        event.register(PortableStructureContainerRenderer.GROUP3_MODEL);
        event.register(PortableStructureContainerRenderer.DISPOSABLE_GROUP1_MODEL);
        event.register(PortableStructureContainerRenderer.DISPOSABLE_GROUP2_MODEL);
        event.register(PortableStructureContainerRenderer.DISPOSABLE_GROUP3_MODEL);
    }


    private static final class ToolGunRenderer extends BlockEntityWithoutLevelRenderer {
        private final ModelResourceLocation bodyModel;
        private final ModelResourceLocation energyModel;
        private final ModelResourceLocation focusModel;
        private final ModelResourceLocation rightLowerRailModel;
        private final ModelResourceLocation rightUpperRailModel;
        private final String animationKey;

        private ToolGunRenderer(
                ModelResourceLocation bodyModel,
                ModelResourceLocation energyModel,
                ModelResourceLocation focusModel,
                ModelResourceLocation rightLowerRailModel,
                ModelResourceLocation rightUpperRailModel,
                String animationKey
        ) {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
            this.bodyModel = bodyModel;
            this.energyModel = energyModel;
            this.focusModel = focusModel;
            this.rightLowerRailModel = rightLowerRailModel;
            this.rightUpperRailModel = rightUpperRailModel;
            this.animationKey = animationKey;
        }

        @Override
        public void renderByItem(
                ItemStack stack,
                ItemDisplayContext displayContext,
                PoseStack poseStack,
                MultiBufferSource buffer,
                int light,
                int overlay
        ) {
            Minecraft minecraft = Minecraft.getInstance();
            ModelManager modelManager = minecraft.getItemRenderer().getItemModelShaper().getModelManager();
            captureMuzzlePosition(stack, displayContext, poseStack, TOOL_MUZZLE_POINT);
            renderModel(minecraft.getItemRenderer(), modelManager.getModel(this.bodyModel),
                    stack, poseStack, buffer, light, overlay);
            renderTransformedModel(minecraft, modelManager, stack, poseStack, buffer, light, overlay,
                    this.rightLowerRailModel, RIGHT_LOWER_RAIL_PIVOT,
                    0.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, 157.5F);
            renderTransformedModel(minecraft, modelManager, stack, poseStack, buffer, light, overlay,
                    this.rightUpperRailModel, RIGHT_UPPER_RAIL_PIVOT,
                    0.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, -157.5F);
            AnimationSample animation = RotationPulseState.sample(
                    this.animationKey, ENERGY_IDLE_SPEED_DEGREES_PER_SECOND, stack, displayContext
            );
            renderTransformedModel(minecraft, modelManager, stack, poseStack, buffer, light, overlay,
                    this.energyModel, ENERGY_PIVOT,
                    0.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, animation.angleDegrees());

            float timeSeconds = Util.getMillis() / 1000.0F;
            float travelPixels = Mth.lerp(
                    animation.activity(), FOCUS_TRAVEL_PIXELS, FOCUS_ACTIVE_TRAVEL_PIXELS
            );
            float focusOffset = Mth.sin(timeSeconds * Mth.TWO_PI * FOCUS_CYCLES_PER_SECOND)
                    * travelPixels / 16.0F;
            renderTransformedModel(minecraft, modelManager, stack, poseStack, buffer, light, overlay,
                    this.focusModel, MODEL_ORIGIN,
                    0.0F, 0.0F, focusOffset,
                    0.0F, 0.0F, 0.0F);
        }
    }

    private static final class MagneticGunRenderer extends BlockEntityWithoutLevelRenderer {
        private final ModelResourceLocation bodyModel;
        private final ModelResourceLocation energyModel;
        private final ModelResourceLocation topClawModel;
        private final ModelResourceLocation leftClawModel;
        private final ModelResourceLocation rightClawModel;
        private final String animationKey;

        private MagneticGunRenderer(
                ModelResourceLocation bodyModel,
                ModelResourceLocation energyModel,
                ModelResourceLocation topClawModel,
                ModelResourceLocation leftClawModel,
                ModelResourceLocation rightClawModel,
                String animationKey
        ) {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
            this.bodyModel = bodyModel;
            this.energyModel = energyModel;
            this.topClawModel = topClawModel;
            this.leftClawModel = leftClawModel;
            this.rightClawModel = rightClawModel;
            this.animationKey = animationKey;
        }

        @Override
        public void renderByItem(
                ItemStack stack,
                ItemDisplayContext displayContext,
                PoseStack poseStack,
                MultiBufferSource buffer,
                int light,
                int overlay
        ) {
            Minecraft minecraft = Minecraft.getInstance();
            ModelManager modelManager = minecraft.getItemRenderer().getItemModelShaper().getModelManager();
            captureMuzzlePosition(stack, displayContext, poseStack, MAGNETIC_MUZZLE_POINT);
            renderModel(minecraft.getItemRenderer(), modelManager.getModel(this.bodyModel),
                    stack, poseStack, buffer, light, overlay);

            AnimationSample animation = RotationPulseState.sample(
                    this.animationKey, ENERGY_IDLE_SPEED_DEGREES_PER_SECOND, stack, displayContext
            );
            renderTransformedModel(minecraft, modelManager, stack, poseStack, buffer, light, overlay,
                    this.energyModel, ENERGY_PIVOT,
                    0.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, animation.angleDegrees());

            float topAngle = TOP_CLAW_OPEN_DEGREES * animation.activity();
            float sideAngle = SIDE_CLAW_OPEN_DEGREES * animation.activity();
            renderTransformedModel(minecraft, modelManager, stack, poseStack, buffer, light, overlay,
                    this.topClawModel, TOP_CLAW_PIVOT,
                    0.0F, 0.0F, 0.0F,
                    topAngle, 0.0F, 0.0F);
            renderTransformedModel(minecraft, modelManager, stack, poseStack, buffer, light, overlay,
                    this.leftClawModel, LEFT_CLAW_PIVOT,
                    0.0F, 0.0F, 0.0F,
                    0.0F, sideAngle, 0.0F);
            renderTransformedModel(minecraft, modelManager, stack, poseStack, buffer, light, overlay,
                    this.rightClawModel, RIGHT_CLAW_PIVOT,
                    0.0F, 0.0F, 0.0F,
                    0.0F, -sideAngle, 0.0F);
        }
    }

    private record AnimationSample(float angleDegrees, float activity) {
    }

    private static final class PortableStructureContainerRenderer extends BlockEntityWithoutLevelRenderer {
        private static final ModelResourceLocation GROUP1_MODEL = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "item/container_group1")

        );
        private static final ModelResourceLocation GROUP2_MODEL = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "item/container_group2")
        );
        private static final ModelResourceLocation GROUP3_MODEL = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "item/container_group3")
        );
        private static final ModelResourceLocation DISPOSABLE_GROUP1_MODEL = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "item/disposable_container_group1")
        );
        private static final ModelResourceLocation DISPOSABLE_GROUP2_MODEL = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "item/disposable_container_group2")
        );
        private static final ModelResourceLocation DISPOSABLE_GROUP3_MODEL = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(AeronauticsStructureToolMod.MOD_ID, "item/disposable_container_group3")
        );
        private static final Vector3f GROUP1_PIVOT = new Vector3f(6.0F / 16.0F, 0.0F / 16.0F, 7.0F / 16.0F);
        private static final Vector3f GROUP2_PIVOT = new Vector3f(8.0F / 16.0F, 8.0F / 16.0F, 8.0F / 16.0F);
        private static final Vector3f GROUP3_PIVOT = new Vector3f(8.0F / 16.0F, 4.0F / 16.0F, 8.0F / 16.0F);
        private static final float BASE_FLOAT_AMPLITUDE_PX = 1.0F;
        private static final float FILLED_FLOAT_AMPLITUDE_PX = 1.75F;
        private static final float BASE_FLOAT_CYCLES_PER_SECOND = 0.6F;
        private static final float FILLED_FLOAT_CYCLES_PER_SECOND = 1.15F;
        private static final float BASE_CORE_SPIN_DEGREES_PER_SECOND = 360.0F;
        private static final float FILLED_CORE_SPIN_DEGREES_PER_SECOND = 720.0F;

        private PortableStructureContainerRenderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
            boolean filled = PortableStructureContainerItem.hasStoredVehicle(stack);
            boolean disposable = stack.is(ModItems.DISPOSABLE_VEHICLE_CONTAINER.get());
            renderPortableStructureContainerModel(stack, poseStack, buffer, light, overlay, filled, disposable);
        }

        private static void renderPortableStructureContainerModel(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, boolean filled, boolean disposable) {
            Minecraft minecraft = Minecraft.getInstance();
            ModelManager modelManager = minecraft.getItemRenderer().getItemModelShaper().getModelManager();
            float timeSeconds = Util.getMillis() / 1000.0F;
            renderPortableStructureContainerModelAtTime(minecraft, modelManager, stack, poseStack, buffer, light, overlay, filled, disposable, timeSeconds);
        }

        private static void renderPortableStructureContainerModelAtTime(
                Minecraft minecraft,
                ModelManager modelManager,
                ItemStack stack,
                PoseStack poseStack,
                MultiBufferSource buffer,
                int light,
                int overlay,
                boolean filled,
                boolean disposable,
                float timeSeconds
        ) {
            float floatAmplitude = (filled ? FILLED_FLOAT_AMPLITUDE_PX : BASE_FLOAT_AMPLITUDE_PX) / 16.0F;
            float floatSpeed = filled ? FILLED_FLOAT_CYCLES_PER_SECOND : BASE_FLOAT_CYCLES_PER_SECOND;
            float floatPhase = Mth.sin(timeSeconds * Mth.TWO_PI * floatSpeed);
            float bottomOffset = floatPhase * floatAmplitude;
            float topOffset = -floatPhase * floatAmplitude;

            ModelResourceLocation group1 = disposable ? DISPOSABLE_GROUP1_MODEL : GROUP1_MODEL;
            ModelResourceLocation group2 = disposable ? DISPOSABLE_GROUP2_MODEL : GROUP2_MODEL;
            ModelResourceLocation group3 = disposable ? DISPOSABLE_GROUP3_MODEL : GROUP3_MODEL;

            renderAnimatedPart(minecraft, modelManager, stack, poseStack, buffer, light, overlay, group1, GROUP1_PIVOT, 0.0F, bottomOffset, 0.0F, 0.0F, 0.0F, 0.0F);
            renderAnimatedPart(minecraft, modelManager, stack, poseStack, buffer, light, overlay, group2, GROUP2_PIVOT, 0.0F, topOffset, 0.0F, 0.0F, 0.0F, 0.0F);

            float baseCoreSpin = timeSeconds * (filled ? FILLED_CORE_SPIN_DEGREES_PER_SECOND : BASE_CORE_SPIN_DEGREES_PER_SECOND);
            renderAnimatedPart(minecraft, modelManager, stack, poseStack, buffer, light, overlay, group3, GROUP3_PIVOT, 0.0F, 0.0F, 0.0F, 0.0F, baseCoreSpin, 0.0F);

            if (filled) {
                renderContainmentCore(poseStack, buffer, timeSeconds);
            }
        }

        private static void renderAnimatedPart(
                Minecraft minecraft,
                ModelManager modelManager,
                ItemStack stack,
                PoseStack poseStack,
                MultiBufferSource buffer,
                int light,
                int overlay,
                ModelResourceLocation modelLocation,
                Vector3f pivot,
                float offsetX,
                float offsetY,
                float offsetZ,
                float rotX,
                float rotY,
                float rotZ
        ) {
            poseStack.pushPose();
            poseStack.translate(offsetX, offsetY, offsetZ);
            poseStack.translate(pivot.x(), pivot.y(), pivot.z());
            if (rotY != 0.0F) {
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotY));
            }
            if (rotX != 0.0F) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotX));
            }
            if (rotZ != 0.0F) {
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotZ));
            }
            poseStack.translate(-pivot.x(), -pivot.y(), -pivot.z());
            renderModel(
                    minecraft.getItemRenderer(),
                    modelManager.getModel(modelLocation),
                    stack,
                    poseStack,
                    buffer,
                    light,
                    overlay
            );
            poseStack.popPose();
        }

        private static void renderContainmentCore(PoseStack poseStack, MultiBufferSource buffer, float timeSeconds) {
            VertexConsumer fillConsumer = buffer.getBuffer(RenderType.debugQuads());

            poseStack.pushPose();
            poseStack.translate(8.0F / 16.0F, 4.0F / 16.0F, 8.0F / 16.0F);
            applyRandomizedCubeRotation(poseStack, timeSeconds, 0.91F, 1.37F, 1.83F, 24.0F, 38.0F, 31.0F, 80.0F, 120.0F, 96.0F);
            fillCube(poseStack.last().pose(), fillConsumer, cubeCorners(0.16F), 0.98F, 0.86F, 0.26F, 0.32F);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(8.0F / 16.0F, 4.0F / 16.0F, 8.0F / 16.0F);
            applyRandomizedCubeRotation(poseStack, timeSeconds, 1.21F, 0.77F, 1.59F, 42.0F, 25.0F, 33.0F, 160.0F, 210.0F, 185.0F);
            fillCube(poseStack.last().pose(), fillConsumer, cubeCorners(0.07F), 1.0F, 1.0F, 1.0F, 0.72F);
            poseStack.popPose();
        }

        private static void applyRandomizedCubeRotation(
                PoseStack poseStack,
                float timeSeconds,
                float speedX,
                float speedY,
                float speedZ,
                float wobbleX,
                float wobbleY,
                float wobbleZ,
                float baseX,
                float baseY,
                float baseZ
        ) {
            float angleX = timeSeconds * baseX + Mth.sin(timeSeconds * speedX) * wobbleX + Mth.sin(timeSeconds * speedX * 0.37F + 1.2F) * (wobbleX * 0.45F);
            float angleY = timeSeconds * baseY + Mth.sin(timeSeconds * speedY + 2.1F) * wobbleY + Mth.sin(timeSeconds * speedY * 0.41F + 0.4F) * (wobbleY * 0.5F);
            float angleZ = timeSeconds * baseZ + Mth.sin(timeSeconds * speedZ + 0.8F) * wobbleZ + Mth.sin(timeSeconds * speedZ * 0.53F + 2.3F) * (wobbleZ * 0.42F);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(angleX));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angleY));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angleZ));
        }

        private static Vector3d[] cubeCorners(float halfExtent) {
            return new Vector3d[] {
                    new Vector3d(-halfExtent, -halfExtent, -halfExtent),
                    new Vector3d(halfExtent, -halfExtent, -halfExtent),
                    new Vector3d(halfExtent, halfExtent, -halfExtent),
                    new Vector3d(-halfExtent, halfExtent, -halfExtent),
                    new Vector3d(-halfExtent, -halfExtent, halfExtent),
                    new Vector3d(halfExtent, -halfExtent, halfExtent),
                    new Vector3d(halfExtent, halfExtent, halfExtent),
                    new Vector3d(-halfExtent, halfExtent, halfExtent)
            };
        }

        private static void fillCube(Matrix4f matrix, VertexConsumer consumer, Vector3d[] corners, float red, float green, float blue, float alpha) {
            addQuad(consumer, matrix, corners[0], corners[1], corners[2], corners[3], red, green, blue, alpha);
            addQuad(consumer, matrix, corners[4], corners[5], corners[6], corners[7], red, green, blue, alpha);
            addQuad(consumer, matrix, corners[0], corners[3], corners[7], corners[4], red, green, blue, alpha);
            addQuad(consumer, matrix, corners[1], corners[2], corners[6], corners[5], red, green, blue, alpha);
            addQuad(consumer, matrix, corners[3], corners[2], corners[6], corners[7], red, green, blue, alpha);
            addQuad(consumer, matrix, corners[0], corners[1], corners[5], corners[4], red, green, blue, alpha);
        }

        private static void addQuad(VertexConsumer consumer, Matrix4f matrix, Vector3d first, Vector3d second, Vector3d third, Vector3d fourth, float red, float green, float blue, float alpha) {
            addVertex(consumer, matrix, first.x, first.y, first.z, red, green, blue, alpha);
            addVertex(consumer, matrix, second.x, second.y, second.z, red, green, blue, alpha);
            addVertex(consumer, matrix, third.x, third.y, third.z, red, green, blue, alpha);
            addVertex(consumer, matrix, fourth.x, fourth.y, fourth.z, red, green, blue, alpha);
        }

        private static void addVertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, float red, float green, float blue, float alpha) {
            consumer.addVertex(matrix, (float) x, (float) y, (float) z)
                    .setColor((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F), (int) (alpha * 255.0F));
        }
    }

    public static void renderPortableStructureContainerStatic(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, boolean filled) {

        ItemStack stack = new ItemStack(ModItems.PORTABLE_STRUCTURE_CONTAINER.get());
        Minecraft minecraft = Minecraft.getInstance();
        ModelManager modelManager = minecraft.getItemRenderer().getItemModelShaper().getModelManager();
        PortableStructureContainerRenderer.renderPortableStructureContainerModelAtTime(
                minecraft,
                modelManager,
                stack,
                poseStack,
                buffer,
                light,
                overlay,
                filled,
                false,
                Util.getMillis() / 1000.0F
        );
    }

    private static final class RotationPulseState {
        private static final long ATTACK_MILLIS = 130L;
        private static final long RELEASE_MILLIS = 650L;
        private static final long SUSTAIN_TIMEOUT_MILLIS = 150L;
        private static final float PEAK_MULTIPLIER = 12.0F;
        private static final float SUSTAINED_MULTIPLIER = 8.0F;
        private static final java.util.EnumMap<InteractionHand, java.util.ArrayDeque<Long>> PULSE_TIMES = new java.util.EnumMap<>(InteractionHand.class);
        private static final java.util.EnumMap<InteractionHand, SustainTracker> SUSTAIN_TRACKERS = new java.util.EnumMap<>(InteractionHand.class);
        private static final java.util.HashMap<String, RotationTracker> TRACKERS = new java.util.HashMap<>();

        private RotationPulseState() {
        }

        static void pulse(InteractionHand hand) {
            long now = Util.getMillis();
            java.util.ArrayDeque<Long> pulses = PULSE_TIMES.computeIfAbsent(hand, ignored -> new java.util.ArrayDeque<>());
            pruneOldPulses(pulses, now);
            pulses.addLast(now);
        }

        static void sustain(InteractionHand hand) {
            long now = Util.getMillis();
            SustainTracker tracker = SUSTAIN_TRACKERS.computeIfAbsent(hand, ignored -> new SustainTracker());
            tracker.markActive(now + SUSTAIN_TIMEOUT_MILLIS);
        }

        static void stopSustain(InteractionHand hand) {
            long now = Util.getMillis();
            SustainTracker tracker = SUSTAIN_TRACKERS.computeIfAbsent(hand, ignored -> new SustainTracker());
            tracker.stop(now);
        }

        static AnimationSample sample(
                String key,
                float baseSpeedDegreesPerSecond,
                ItemStack stack,
                ItemDisplayContext displayContext
        ) {
            long now = Util.getMillis();
            InteractionHand pulsedHand = resolveRenderedHand(stack, displayContext);
            float multiplier = 1.0F;
            float activity = 0.0F;
            String trackerKey = key + "#static";
            if (pulsedHand != null) {
                java.util.ArrayDeque<Long> pulses = PULSE_TIMES.computeIfAbsent(pulsedHand, ignored -> new java.util.ArrayDeque<>());
                pruneOldPulses(pulses, now);
                float pulseEnvelope = currentEnvelope(pulses, now);
                SustainTracker sustainTracker = SUSTAIN_TRACKERS.computeIfAbsent(pulsedHand, ignored -> new SustainTracker());
                float sustainedEnvelope = sustainTracker.update(now);
                activity = Math.max(pulseEnvelope, sustainedEnvelope);
                multiplier = Math.max(
                        1.0F + (PEAK_MULTIPLIER - 1.0F) * pulseEnvelope,
                        1.0F + (SUSTAINED_MULTIPLIER - 1.0F) * sustainedEnvelope
                );
                trackerKey = key + "#" + pulsedHand.name().toLowerCase(java.util.Locale.ROOT);
            }
            RotationTracker tracker = TRACKERS.computeIfAbsent(trackerKey, ignored -> new RotationTracker());
            return new AnimationSample(
                    tracker.update(now, baseSpeedDegreesPerSecond * multiplier),
                    activity
            );
        }

        private static float currentEnvelope(java.util.ArrayDeque<Long> pulses, long now) {
            float strongest = 0.0F;
            for (Long pulseTime : pulses) {
                long elapsed = now - pulseTime;
                if (elapsed < 0L || elapsed > ATTACK_MILLIS + RELEASE_MILLIS) {
                    continue;
                }
                strongest = Math.max(strongest, envelope(elapsed));
            }
            return strongest;
        }

        private static float envelope(long elapsed) {
            if (elapsed <= ATTACK_MILLIS) {
                return smoothStep(elapsed / (float) ATTACK_MILLIS);
            }
            float releaseProgress = (elapsed - ATTACK_MILLIS) / (float) RELEASE_MILLIS;
            return 1.0F - smoothStep(Mth.clamp(releaseProgress, 0.0F, 1.0F));
        }

        private static void pruneOldPulses(java.util.ArrayDeque<Long> pulses, long now) {
            while (!pulses.isEmpty() && now - pulses.peekFirst() > ATTACK_MILLIS + RELEASE_MILLIS) {
                pulses.removeFirst();
            }
        }

        private static float smoothStep(float value) {
            float clamped = Mth.clamp(value, 0.0F, 1.0F);
            return clamped * clamped * (3.0F - 2.0F * clamped);
        }

        private static final class RotationTracker {
            private long lastTime = Long.MIN_VALUE;
            private float angleDegrees = 0.0F;

            private float update(long now, float speedDegreesPerSecond) {
                if (this.lastTime != Long.MIN_VALUE) {
                    float deltaSeconds = (now - this.lastTime) / 1000.0F;
                    this.angleDegrees += speedDegreesPerSecond * Math.max(deltaSeconds, 0.0F);
                }
                this.lastTime = now;
                return this.angleDegrees;
            }
        }

        private static final class SustainTracker {
            private long activeUntil = Long.MIN_VALUE;
            private long lastUpdate = Long.MIN_VALUE;
            private float strength = 0.0F;

            private void markActive(long newActiveUntil) {
                this.activeUntil = Math.max(this.activeUntil, newActiveUntil);
            }

            private void stop(long now) {
                this.activeUntil = Long.MIN_VALUE;
                this.lastUpdate = now;
                this.strength = 0.0F;
            }

            private float update(long now) {
                if (this.lastUpdate == Long.MIN_VALUE) {
                    this.lastUpdate = now;
                }
                float deltaMillis = Math.max(0L, now - this.lastUpdate);
                this.lastUpdate = now;
                boolean active = now <= this.activeUntil;
                float step = active
                        ? deltaMillis / (float) ATTACK_MILLIS
                        : deltaMillis / (float) RELEASE_MILLIS;
                if (active) {
                    this.strength = Math.min(1.0F, this.strength + step);
                } else {
                    this.strength = Math.max(0.0F, this.strength - step);
                }
                return smoothStep(this.strength);
            }
        }
    }

}
