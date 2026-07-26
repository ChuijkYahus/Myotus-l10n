package me.myogoo.myotus.data.recipe.extendedae;

import me.myogoo.myotus.data.builder.extendedae.MyoExtendedAECircuitCutterRecipeBuilder;
import me.myogoo.myotus.data.builder.extendedae.MyoExtendedAECrystalAssemblerRecipeBuilder;
import me.myogoo.myotus.data.recipe.JsonRecipeProvider;
import appeng.core.definitions.AEItems;
import me.myogoo.myotus.init.MyoItems;
import net.minecraft.data.PackOutput;

import static me.myogoo.myotus.data.recipe.ExternalRecipeBuilder.conditions;

public final class ExtendedAERecipes extends JsonRecipeProvider {
    public ExtendedAERecipes(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(JsonRecipeOutput output) {
        MyoExtendedAECrystalAssemblerRecipeBuilder
                .create(id("extendedae/crystal_assembler/aee_compat_processor"))
                .conditions(conditions("extendedae"))
                .inputItem(MyoItems.PRINTED_COMPAT_PROCESSOR.get(), 4)
                .inputItem(AEItems.SILICON_PRINT.asItem(), 4)
                .inputTag("c:dusts/redstone", 4)
                .output(MyoItems.COMPAT_PROCESSOR.get(), 4)
                .save(output);

        MyoExtendedAECircuitCutterRecipeBuilder
                .create(id("extendedae/circuit_cutter/aee_printed_compat_processor"))
                .conditions(conditions("extendedae"))
                .input(MyoItems.CHARGED_ENDER_PEARL_BLOCK.get())
                .output(MyoItems.PRINTED_COMPAT_PROCESSOR.get(), 9)
                .save(output);
    }

    @Override
    public String getName() {
        return "Myotus ExtendedAE recipes";
    }
}
