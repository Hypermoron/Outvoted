package com.hbn.outvoted;

import com.hbn.outvoted.config.OutvotedConfig;
import com.hbn.outvoted.init.ModEntityTypes;
import com.hbn.outvoted.init.ModItems;
import com.hbn.outvoted.init.ModRecipes;
import com.hbn.outvoted.util.ServerEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import software.bernie.geckolib3.GeckoLib;

@Mod("outvoted")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class Outvoted {
    public static final String MOD_ID = "outvoted";
    public static ItemGroup TAB_COMBAT = ItemGroup.COMBAT;
    public static ItemGroup TAB_MISC = ItemGroup.MISC;

    public Outvoted() {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::clientSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, OutvotedConfig.COMMON_SPEC);

        GeckoLib.initialize();
        ModItems.ITEMS.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModRecipes.RECIPES.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(new ServerEvents());
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        if (OutvotedConfig.COMMON.creativetab.get()) {
            ItemGroup TAB = new ItemGroup(-1, "modTab") {
                @OnlyIn(Dist.CLIENT)
                public ItemStack createIcon() {
                    return new ItemStack(ModItems.INFERNO_HELMET.get());
                }
            };
            TAB_COMBAT = TAB;
            TAB_MISC = TAB;
        }
    }


}
