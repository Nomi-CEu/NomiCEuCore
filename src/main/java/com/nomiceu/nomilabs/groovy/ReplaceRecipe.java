package com.nomiceu.nomilabs.groovy;

import com.cleanroommc.groovyscript.api.GroovyBlacklist;
import com.cleanroommc.groovyscript.api.IIngredient;
import com.cleanroommc.groovyscript.helper.ingredient.OreDictIngredient;
import com.cleanroommc.groovyscript.registry.ReloadableRegistryManager;
import com.google.common.collect.ImmutableList;
import com.nomiceu.nomilabs.util.ItemTagMeta;
import com.nomiceu.nomilabs.util.LabsNames;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.RecyclingHandler;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.category.RecipeCategories;
import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.ItemMaterialInfo;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.loaders.recipe.RecyclingRecipes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.cleanroommc.groovyscript.compat.vanilla.VanillaModule.crafting;

@GroovyBlacklist
public class ReplaceRecipe {
    public static void reloadRecyclingRecipes() {
        removeRecipesInCategory(RecipeMaps.ARC_FURNACE_RECIPES, RecipeCategories.ARC_FURNACE_RECYCLING);
        removeRecipesInCategory(RecipeMaps.MACERATOR_RECIPES, RecipeCategories.MACERATOR_RECYCLING);
        removeRecipesInCategory(RecipeMaps.EXTRACTOR_RECIPES, RecipeCategories.EXTRACTOR_RECYCLING);
        RecyclingRecipes.init();
    }

    private static void removeRecipesInCategory(RecipeMap<?> recipeMap, GTRecipeCategory category) {
        if (!recipeMap.getRecipesByCategory().containsKey(category)) return;
        var recipes = new ArrayList<>(recipeMap.getRecipesByCategory().get(category));
        if (recipes.isEmpty()) return;
        for (var recipe : recipes) {
            recipeMap.removeRecipe(recipe);
        }
    }

    public static void replaceRecipeShaped(ResourceLocation name, ItemStack output, List<List<IIngredient>> inputs) {
        validate(name, output, true);
        crafting.remove(name);
        crafting.addShaped(LabsNames.makeGroovyName(name.getPath()), output, inputs);
        registerRecycling(output, inputs);
    }

    public static void replaceRecipeShaped(ItemStack oldOutput, ItemStack newOutput, List<List<IIngredient>> inputs) {
        replaceRecipeShaped(getRecipeName(oldOutput), newOutput, inputs);
    }

    public static void replaceRecipeOutput(ResourceLocation name, ItemStack newOutput) {
        IShapedRecipe originalRecipe = validate(name, newOutput, true);
        var originalCount = originalRecipe.getRecipeOutput().getCount();
        var newCount = newOutput.getCount();

        crafting.remove(name);
        ReloadableRegistryManager.addRegistryEntry(ForgeRegistries.RECIPES, LabsNames.makeGroovyName(name.getPath()), new PartialRecipe(originalRecipe, newOutput.copy()));

        ImmutableList<MaterialStack> originalMaterials = Objects.requireNonNull(OreDictUnifier.getMaterialInfo(newOutput)).getMaterials();
        List<MaterialStack> newMaterials = new ArrayList<>();

        // Multiplies by original then Divides by new as https://github.com/GregTechCEu/GregTech/blob/master/src/main/java/gregtech/api/recipes/RecyclingHandler.java#L82 divides
        originalMaterials.forEach((materialStack -> newMaterials.add(new MaterialStack(materialStack.material, materialStack.amount * originalCount / newCount))));

        LabsVirtualizedRegistries.REPLACE_RECIPE_MANAGER.registerOre(newOutput, new ItemMaterialInfo(newMaterials));
    }

    public static void replaceRecipeOutput(ItemStack oldOutput, ItemStack newOutput) {
        replaceRecipeOutput(getRecipeName(oldOutput), newOutput);
    }

    public static void replaceRecipeInput(ResourceLocation name, List<List<IIngredient>> newInputs) {
        IRecipe originalRecipe = validate(name, ItemStack.EMPTY, false);
        var originalOutput = originalRecipe.getRecipeOutput();
        crafting.remove(name);
        crafting.addShaped(LabsNames.makeGroovyName(name.getPath()), originalOutput, newInputs);
        registerRecycling(originalOutput, newInputs);
    }

    public static void replaceRecipeInput(ItemStack oldOutput, List<List<IIngredient>> newInputs) {
        replaceRecipeInput(getRecipeName(oldOutput), newInputs);
    }

    public static void createRecipe(String name, ItemStack output, List<List<IIngredient>> input) {
        crafting.addShaped(LabsNames.makeGroovyName(name), output, input);
        registerRecycling(output, input);
    }

    public static void createRecipe(ItemStack output, List<List<IIngredient>> input) {
        crafting.addShaped(output, input);
        registerRecycling(output, input);
    }


    public static void changeStackRecycling(ItemStack output, List<IIngredient> ingredients) {
        List<GTRecipeInput> gtInputs = new ArrayList<>();
        for (var input : ingredients) {
            var gtInput = ofGroovyIngredientIncludingFluids(input);
            if (gtInput != null)
                gtInputs.add(gtInput);
        }
        LabsVirtualizedRegistries.REPLACE_RECIPE_MANAGER.registerOre(output, RecyclingHandler.getRecyclingIngredients(gtInputs, output.getCount()));
    }

    private static IShapedRecipe validate(ResourceLocation name, ItemStack output, boolean validateOutput) {
        IRecipe originalRecipe = ForgeRegistries.RECIPES.getValue(name);
        if (originalRecipe == null)
            throw new IllegalArgumentException("Could not find recipe with name " + name + "!");

        if (!(originalRecipe instanceof IShapedRecipe shapedRecipe))
            throw new IllegalArgumentException("Recipe with name " + name + " is not shaped!");

        if (originalRecipe.isDynamic())
            throw new IllegalArgumentException("Cannot replace Dynamic Recipe " + name + "!");

        ItemStack originalOutput = checkAndGetOutput(output, validateOutput, originalRecipe);

        if (OreDictUnifier.getMaterialInfo(originalOutput) == null)
            throw new IllegalArgumentException("Could not find existing Material Info for item " + originalOutput);

        ReloadableRegistryManager.removeRegistryEntry(ForgeRegistries.RECIPES, name);

        return shapedRecipe;
    }

    private static boolean isRecipeValid(ResourceLocation name) {
        IRecipe originalRecipe = ForgeRegistries.RECIPES.getValue(name);
        if (originalRecipe == null)
            return false;

        if (!(originalRecipe instanceof IShapedRecipe))
            return false;

        return !originalRecipe.isDynamic();
    }

    private static void registerRecycling(ItemStack output, List<List<IIngredient>> inputs) {
        List<GTRecipeInput> gtInputs = new ArrayList<>();
        for (var inputList : inputs) {
            for (var input : inputList) {
                var gtInput = ofGroovyIngredient(input);
                if (gtInput != null)
                    gtInputs.add(gtInput);
            }
        }
        LabsVirtualizedRegistries.REPLACE_RECIPE_MANAGER.registerOre(output, RecyclingHandler.getRecyclingIngredients(gtInputs, output.getCount()));
    }

    @NotNull
    private static ItemStack checkAndGetOutput(ItemStack output, boolean validateOutput, IRecipe originalRecipe) {
        ItemStack originalOutput = originalRecipe.getRecipeOutput();
        if (validateOutput) {
            if (!Objects.equals(originalOutput.getItem().getRegistryName(), output.getItem().getRegistryName()))
                throw new IllegalArgumentException("New Recipe Output " + output.getItem().getRegistryName() + " must have same Registry Name as Original Input " + originalOutput.getItem().getRegistryName() + "!");
            if (originalOutput.getMetadata() != output.getMetadata())
                throw new IllegalArgumentException("New Recipe Output's Metadata " + output.getMetadata() + " must be the same as Original Input's Metadata " + originalOutput.getMetadata() + "!");
        }
        return originalOutput;
    }

    @SuppressWarnings("ConstantValue")
    @Nullable
    private static GTRecipeInput ofGroovyIngredient(IIngredient ingredient) {
        if (ingredient instanceof OreDictIngredient oreDictIngredient) {
            return new GTRecipeOreInput(oreDictIngredient.getOreDict(), ingredient.getAmount());
        }
        if ((Object) ingredient instanceof ItemStack stack) {
            return new GTRecipeItemInput(stack);
        }
        return null;
    }

    // TODO Remove? GTRecipeFluidInputs are not included in recycling calculations
    @Nullable
    private static GTRecipeInput ofGroovyIngredientIncludingFluids(IIngredient ingredient) {
        var gtInput = ofGroovyIngredient(ingredient);
        if (gtInput != null) return gtInput;
        if ((Object) ingredient instanceof FluidStack stack) {
            return new GTRecipeFluidInput(stack);
        }
        return null;
    }

    private static ResourceLocation getRecipeName(ItemStack oldOutput) {
        ResourceLocation name = null;
        for (IRecipe recipe : ForgeRegistries.RECIPES) {
            if (recipe.getRegistryName() != null &&
                    isRecipeValid(recipe.getRegistryName()) &&
                    ItemTagMeta.compare(recipe.getRecipeOutput(), oldOutput) &&
                    recipe.getRecipeOutput().getCount() == oldOutput.getCount()) {
                if (name == null) {
                    name = recipe.getRegistryName();
                    continue;
                }
                throw new IllegalArgumentException("Error Finding Recipes for Output " + oldOutput + ":" +
                        "Too Many Recipes for Output. Max: 1.");
            }
        }
        if (name == null)
            throw new IllegalArgumentException("Error Finding Recipes for Output " + oldOutput + ":" +
                    "No recipes for Output. Requires: 1. Recipes must not be dynamic, and have the exact same stack, including count, metadata and tag.");
        return name;
    }
}
