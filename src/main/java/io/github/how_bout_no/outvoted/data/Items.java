package io.github.how_bout_no.outvoted.data;

import io.github.how_bout_no.outvoted.Outvoted;
import io.github.how_bout_no.outvoted.init.ModItems;
import io.github.how_bout_no.outvoted.item.ModItem;
import io.github.how_bout_no.outvoted.item.ModdedSpawnEggItem;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.ExistingFileHelper;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;

public class Items extends ItemModelProvider {

    public Items(DataGenerator generator, ExistingFileHelper existingFileHelper) {
        super(generator, Outvoted.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        /*
          Iterates through each basic item and spawn egg, each type having the same exact layout so it just uses less lines
         */
        ModItems.ITEMS.getEntries().forEach(item -> {
            if (item.get() instanceof ModItem) {
                singleTexture(item.get().getRegistryName().getPath(), new ResourceLocation("item/generated"),
                        "layer0", new ResourceLocation(Outvoted.MOD_ID, "items/" + item.get().getRegistryName().getPath()));
            } else if (item.get() instanceof ModdedSpawnEggItem) {
                withExistingParent(item.get().getRegistryName().getPath(), new ResourceLocation("item/template_spawn_egg"));
            }
        });

        singleTexture(ModItems.WILDFIRE_HELMET.get().getRegistryName().getPath(), new ResourceLocation("item/generated"),
                "layer0", new ResourceLocation(Outvoted.MOD_ID, "items/" + ModItems.WILDFIRE_HELMET.get().getRegistryName().getPath()));

        singleTexture(ModItems.WILDFIRE_SHIELD.get().getRegistryName().getPath(), new ResourceLocation("item/shield"),
                "particles", new ResourceLocation("block/nether_bricks"))
                .override().predicate(new ResourceLocation(Outvoted.MOD_ID, "blocking"), 1)
                .model(new ModelFile(new ResourceLocation(Outvoted.MOD_ID, "item/inferno_shield_blocking")) {
                    @Override
                    protected boolean exists() {
                        return true;
                    }
                });

        singleTexture(ModItems.WILDFIRE_SHIELD.get().getRegistryName().getPath() + "_blocking", new ResourceLocation("item/shield_blocking"),
                "particles", new ResourceLocation("block/nether_bricks"));
    }
}
