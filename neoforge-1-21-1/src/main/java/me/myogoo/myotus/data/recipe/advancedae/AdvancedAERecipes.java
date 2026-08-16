package me.myogoo.myotus.data.recipe.advancedae;

import me.myogoo.myotus.data.builder.advancedae.MyoAdvancedAEReactionRecipeBuilder;
import me.myogoo.myotus.data.recipe.JsonRecipeProvider;
import me.myogoo.myotus.init.MyoItems;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.ItemLike;

import static me.myogoo.myotus.data.recipe.ExternalRecipeBuilder.conditions;

public final class AdvancedAERecipes extends JsonRecipeProvider {
    public AdvancedAERecipes(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(JsonRecipeOutput output) {
        MyoAdvancedAEReactionRecipeBuilder
                .create(id("advanced_ae/reaction_chamer/aae_charged_ender_pearl"))
                .conditions(conditions("advanced_ae"))
                .energy(400_000)
                .fluid("minecraft:water", 250)
                .inputTag("c:ender_pearls", 16)
                .output(MyoItems.CHARGED_ENDER_PEARL.get(), 16)
                .save(output);

        MyoAdvancedAEReactionRecipeBuilder
                .create(id("advanced_ae/reaction_chamer/aae_charged_ender_pearl_block"))
                .conditions(conditions("advanced_ae"))
                .energy(13_000_000)
                .fluid("minecraft:water", 10_000)
                .inputTag("c:storage_blocks/ender_pearl", 64)
                .output(MyoItems.CHARGED_ENDER_PEARL_BLOCK.get(), 64)
                .save(output);
        
    }

    @Override
    public String getName() {
        return "Myotus Advanced AE recipes";
    }

    private static MyoAdvancedAEReactionRecipeBuilder reaction(String path, String inputTag, int inputAmount,
                                                               int energy, int waterAmount, ItemLike outputItem) {
        return MyoAdvancedAEReactionRecipeBuilder
                .create(id(path))
                .conditions(conditions("advanced_ae"))
                .energy(energy)
                .fluid("minecraft:water", waterAmount)
                .inputTag(inputTag, inputAmount)
                .output(outputItem, 64);
    }
}
