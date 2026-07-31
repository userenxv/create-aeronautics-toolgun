package com.enxv.aeronauticsstructuretool;

import com.enxv.aeronauticsstructuretool.core.ModConstants;
import com.enxv.aeronauticsstructuretool.server.ServerServices;
import com.enxv.aeronauticsstructuretool.printer.PortableStructurePrinterCommands;
import com.enxv.aeronauticsstructuretool.printer.PortableStructurePrinterSettings;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(AeronauticsStructureToolMod.MOD_ID)
public final class AeronauticsStructureToolMod {
    public static final String MOD_ID = ModConstants.MOD_ID;
    public static final Logger LOGGER = ModConstants.LOGGER;

    public AeronauticsStructureToolMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SurvivalToolgunConfig.SPEC);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);
        ModPayloads.register(modBus);
        modBus.addListener(ModSetup::onCommonSetup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientHooks(modBus);
        }
        NeoForge.EVENT_BUS.addListener(AeronauticsStructureToolMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(AeronauticsStructureToolMod::onItemCrafted);
        ServerServices.register(NeoForge.EVENT_BUS);
    }

    private static void registerClientHooks(IEventBus modBus) {
        try {
            Class<?> clientHooks = Class.forName("com.enxv.aeronauticsstructuretool.client.ClientHooks");
            clientHooks.getMethod("register", IEventBus.class).invoke(null, modBus);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to register client hooks", exception);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        PortableStructurePrinterCommands.register(event);
    }

    private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!event.getCrafting().is(ModItems.PORTABLE_STRUCTURE_CONTAINER.get())) {
            return;
        }
        if (PortableStructurePrinterSettings.canCraftPortableStructureContainer(serverPlayer.serverLevel())) {
            return;
        }

        event.getCrafting().setCount(0);
        if (serverPlayer.containerMenu.getCarried().is(ModItems.PORTABLE_STRUCTURE_CONTAINER.get())) {
            serverPlayer.containerMenu.setCarried(ItemStack.EMPTY);
        }
        refundPortableStructureContainerIngredients(serverPlayer);
        serverPlayer.containerMenu.broadcastChanges();
        serverPlayer.sendSystemMessage(Component.translatable("message.create_aeronautics_toolgun.portable_container_crafting_disabled"));
    }

    private static void refundPortableStructureContainerIngredients(ServerPlayer player) {
        giveOrDrop(player, new ItemStack(Items.IRON_INGOT, 4));
        Item transmitter = BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:transmitter"));
        if (transmitter != Items.AIR) {
            giveOrDrop(player, new ItemStack(transmitter, 4));
        }
        giveOrDrop(player, new ItemStack(Items.NETHER_STAR));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        boolean added = player.getInventory().add(stack);
        if (!added || !stack.isEmpty()) {
            player.drop(stack.copy(), false);
        }
    }
}
