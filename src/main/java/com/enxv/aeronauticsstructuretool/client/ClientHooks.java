package com.enxv.aeronauticsstructuretool.client;

import com.enxv.aeronauticsstructuretool.AeronauticsStructureToolMod;
import com.enxv.aeronauticsstructuretool.ModBlockEntities;
import com.enxv.aeronauticsstructuretool.client.render.ClientBeamRenderer;
import com.enxv.aeronauticsstructuretool.client.render.ClientItemRenderRegistry;
import com.enxv.aeronauticsstructuretool.client.render.PortableStructurePrinterBlockEntityRenderer;
import com.enxv.aeronauticsstructuretool.client.render.ClientToolOverlayRenderer;
import com.enxv.aeronauticsstructuretool.client.tool.MagneticGunClientController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ClientHooks {
    private ClientHooks() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientHooks::registerKeys);
        modBus.addListener(ClientItemRenderRegistry::registerClientExtensions);
        modBus.addListener(ClientItemRenderRegistry::registerAdditionalModels);
        modBus.addListener(ClientToolOverlayRenderer::registerShaders);
        modBus.addListener(ClientHooks::onModelBakingCompleted);
        modBus.addListener(ClientHooks::registerBlockEntityRenderers);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        ClientToolInputController.registerKeyMappings(event);
        MagneticGunClientController.registerKeyMappings(event);
    }

    private static void onModelBakingCompleted(ModelEvent.BakingCompleted event) {
        ClientToolOverlayRenderer.clearCachedModels();
    }

    private static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PORTABLE_STRUCTURE_PRINTER.get(), PortableStructurePrinterBlockEntityRenderer::new);
    }

    public static void showBeam(InteractionHand hand, Vec3 target) {
        ClientBeamRenderer.showPulse(hand, target);
        ClientItemRenderRegistry.pulse(hand);
    }

    public static void showSustainedBeam(InteractionHand hand, Vec3 target) {
        ClientBeamRenderer.showSustained(hand, target);
        ClientItemRenderRegistry.sustain(hand);
    }

    public static void stopMagneticBeam(InteractionHand hand) {
        ClientBeamRenderer.stop(hand);
        ClientItemRenderRegistry.stopSustain(hand);
    }

    public static void showMagneticLaunch(InteractionHand hand, Vec3 target) {
        ClientBeamRenderer.showLaunch(hand, target);
        ClientItemRenderRegistry.launch(hand);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Vec3 playerPosition = minecraft.player.position();
        minecraft.level.playLocalSound(
                playerPosition.x,
                playerPosition.y,
                playerPosition.z,
                SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS,
                0.9F,
                1.55F,
                false
        );
        minecraft.level.playLocalSound(
                playerPosition.x,
                playerPosition.y,
                playerPosition.z,
                SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS,
                0.75F,
                1.25F,
                false
        );
    }

    public static void renderPortableStructureContainerStatic(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay,
            boolean filled
    ) {
        ClientItemRenderRegistry.renderPortableStructureContainerStatic(
                poseStack,
                buffer,
                light,
                overlay,
                filled
        );
    }

    @EventBusSubscriber(modid = AeronauticsStructureToolMod.MOD_ID, value = Dist.CLIENT)
    public static final class RuntimeEvents {
        private RuntimeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            ClientToolInputController.onClientTick();
            ClientBeamRenderer.tick(Minecraft.getInstance());
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            ClientToolOverlayRenderer.renderLevel(event);
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            ClientToolOverlayRenderer.renderGui(event);
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            ClientToolInputController.onMouseScroll(event);
        }

        @SubscribeEvent
        public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
            ClientToolInputController.onInteractionKeyTriggered(event);
        }
    }
}
