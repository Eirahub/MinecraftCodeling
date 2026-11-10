package codeling.robot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
//? if >=26.1 {
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
//?} else {
/*import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.crafting.Ingredient;
*///?}
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
//? if >=26.1
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Crafting (issue #93) and the stonecutter (issue #115) from the robot's own inventory, with the server's recipe
 * manager. Every craft runs on a copy of the inventory; the robot's inventory only changes when a craft fully worked
 * and its output (plus remainders such as empty buckets) fit. So missing ingredients consume nothing, and a full robot
 * makes only what fits and answers that number.
 */
final class RobotCrafting {
    private static RecipeManager indexed;
    private static Map<Item, List<Recipe<?>>> byResult = Map.of();

    private RobotCrafting() {
    }

    /**
     * Crafts whole recipes until at least {@code count} items are made, the ingredients run out or the output no
     * longer fits. Answers how many items were made. With {@code commit} false nothing changes (canCraft).
     */
    static int craft(RobotEntity robot, Item item, int count, boolean commit, boolean stonecutter) {
        ServerLevel level = (ServerLevel) robot.level();
        Block station = stonecutter ? Blocks.STONECUTTER : Blocks.CRAFTING_TABLE;
        boolean atStation = nextTo(robot, station);
        if (stonecutter && !atStation) return 0;
        List<Recipe<?>> recipes = recipesFor(level, item).stream()
                .filter(r -> stonecutter ? r instanceof StonecutterRecipe : r instanceof CraftingRecipe)
                .filter(r -> atStation || fitsTwoByTwo(r))
                .toList();
        SimpleContainer sim = new SimpleContainer(robot.inventory().getContainerSize());
        for (int i = 0; i < sim.getContainerSize(); i++) sim.setItem(i, robot.inventory().getItem(i).copy());
        int made = 0;
        while (made < count) {
            int n = 0;
            for (Recipe<?> recipe : recipes) {
                n = once(level, sim, recipe, item);
                if (n > 0) break;
            }
            if (n == 0) break;
            made += n;
        }
        if (commit && made > 0) {
            for (int i = 0; i < sim.getContainerSize(); i++) robot.inventory().setItem(i, sim.getItem(i));
        }
        return made;
    }

    /**
     * The crafting and stonecutter recipes that make {@code item}, sorted by id so the same inventory always picks the
     * same recipe. Indexed once per recipe manager (a /reload makes a new one): sorting and filtering every recipe on
     * each call cost about a millisecond, so 20 crafting robots used most of a tick (issue #138).
     */
    private static List<Recipe<?>> recipesFor(ServerLevel level, Item item) {
        //? if >=26.1 {
        RecipeManager manager = level.recipeAccess();
        //?} else
        /*RecipeManager manager = level.getRecipeManager();*/
        if (manager != indexed) {
            //? if >=26.1 {
            Map<Item, List<Recipe<?>>> map = new HashMap<>();
            manager.getRecipes().stream()
                    .sorted(Comparator.comparing(h -> h.id().identifier().toString()))
                    .map(RecipeHolder::value)
                    .filter(r -> (r instanceof CraftingRecipe || r instanceof StonecutterRecipe) && !r.isSpecial() && !r.placementInfo().isImpossibleToPlace())
                    .forEach(r -> r.display().forEach(d -> {
                        Item made = d.result() instanceof SlotDisplay.ItemStackSlotDisplay s ? s.stack().item().value()
                                : d.result() instanceof SlotDisplay.ItemSlotDisplay s2 ? s2.item().value() : null;
                        List<Recipe<?>> list = made == null ? null : map.computeIfAbsent(made, k -> new ArrayList<>());
                        if (list != null && !list.contains(r)) list.add(r);
                    }));
            //?} else {
            /*// 1.21.1 has no recipe displays: a recipe makes exactly its result item. isIncomplete is the old
            // "an ingredient matches nothing" check that isImpossibleToPlace replaced.
            Map<Item, List<Recipe<?>>> map = new HashMap<>();
            manager.getRecipes().stream()
                    .sorted(Comparator.comparing(h -> h.id().toString()))
                    .map(RecipeHolder::value)
                    .filter(r -> (r instanceof CraftingRecipe || r instanceof StonecutterRecipe) && !r.isSpecial() && !r.isIncomplete())
                    .forEach(r -> {
                        ItemStack made = r.getResultItem(level.registryAccess());
                        List<Recipe<?>> list = made.isEmpty() ? null : map.computeIfAbsent(made.getItem(), k -> new ArrayList<>());
                        if (list != null && !list.contains(r)) list.add(r);
                    });
            *///?}
            byResult = map;
            indexed = manager;
        }
        return byResult.getOrDefault(item, List.of());
    }

    // One craft on the copy: take one of each ingredient, assemble, put output and remainders back. On any failure the
    // copy is restored and 0 is answered.
    private static int once(ServerLevel level, SimpleContainer sim, Recipe<?> recipe, Item item) {
        //? if >=26.1 {
        StackedItemContents available = new StackedItemContents();
        for (int i = 0; i < sim.getContainerSize(); i++) available.accountSimpleStack(sim.getItem(i));
        List<Holder<Item>> picked = new ArrayList<>();
        if (!available.canCraft(recipe, picked::add)) return 0;
        //?} else {
        /*StackedContents available = new StackedContents();
        for (int i = 0; i < sim.getContainerSize(); i++) available.accountSimpleStack(sim.getItem(i));
        IntList ids = new IntArrayList();
        if (!available.canCraft(recipe, ids)) return 0;
        List<Holder<Item>> picked = new ArrayList<>();
        // 1.21.1 answers one id per grid slot of a shaped recipe, 0 (air) for the empty ones; keep the real items.
        for (int id : ids) if (id != 0) picked.add(StackedContents.fromStackingIndex(id).getItemHolder());
        *///?}
        List<ItemStack> backup = new ArrayList<>();
        for (int i = 0; i < sim.getContainerSize(); i++) backup.add(sim.getItem(i).copy());
        List<ItemStack> taken = new ArrayList<>();
        for (Holder<Item> holder : picked) taken.add(takeOne(sim, holder));
        ItemStack result;
        NonNullList<ItemStack> remainders;
        if (recipe instanceof StonecutterRecipe cut) {
            SingleRecipeInput input = new SingleRecipeInput(taken.getFirst());
            //? if >=26.1 {
            result = cut.matches(input, level) ? cut.assemble(input) : ItemStack.EMPTY;
            //?} else
            /*result = cut.matches(input, level) ? cut.assemble(input, level.registryAccess()) : ItemStack.EMPTY;*/
            remainders = NonNullList.create();
        } else {
            CraftingRecipe crafting = (CraftingRecipe) recipe;
            CraftingInput input = input(crafting, taken);
            //? if >=26.1 {
            result = crafting.matches(input, level) ? crafting.assemble(input) : ItemStack.EMPTY;
            //?} else
            /*result = crafting.matches(input, level) ? crafting.assemble(input, level.registryAccess()) : ItemStack.EMPTY;*/
            remainders = crafting.getRemainingItems(input);
        }
        boolean fits = !result.isEmpty() && result.is(item);
        int made = result.getCount();
        if (fits) fits = sim.addItem(result).isEmpty();
        for (ItemStack rest : remainders) if (fits && !rest.isEmpty()) fits = sim.addItem(rest).isEmpty();
        if (fits) return made;
        for (int i = 0; i < sim.getContainerSize(); i++) sim.setItem(i, backup.get(i));
        return 0;
    }

    // One item of that kind from the first slot a player's recipe book would use (no damaged, enchanted or named items).
    private static ItemStack takeOne(SimpleContainer sim, Holder<Item> item) {
        for (int i = 0; i < sim.getContainerSize(); i++) {
            ItemStack stack = sim.getItem(i);
            //? if >=26.1 {
            if (stack.is(item) && Inventory.isUsableForCrafting(stack)) return stack.split(1);
            //?} else {
            /*// The stacks 1.21.1's StackedContents.accountSimpleStack counts.
            boolean usable = !stack.isDamaged() && !stack.isEnchanted() && !stack.has(DataComponents.CUSTOM_NAME);
            if (stack.is(item) && usable) return stack.split(1);
            *///?}
        }
        throw new IllegalStateException("picked item " + item + " is not in the inventory");
    }

    // The grid the recipe was written for: a shaped recipe in its pattern's slots, a shapeless one in a row.
    private static CraftingInput input(CraftingRecipe recipe, List<ItemStack> taken) {
        if (recipe instanceof ShapedRecipe shaped) {
            List<ItemStack> grid = new ArrayList<>();
            //? if >=26.1 {
            for (int index : recipe.placementInfo().slotsToIngredientIndex()) grid.add(index < 0 ? ItemStack.EMPTY : taken.get(index));
            //?} else {
            /*// The pattern's ingredients, row by row; the picked items follow the non-empty ones in order.
            int next = 0;
            for (Ingredient ingredient : shaped.getIngredients()) grid.add(ingredient.isEmpty() ? ItemStack.EMPTY : taken.get(next++));
            *///?}
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
        }
        List<ItemStack> grid = new ArrayList<>(taken);
        while (grid.size() < 9) grid.add(ItemStack.EMPTY);
        return CraftingInput.of(3, 3, grid);
    }

    // The player's own 2x2 grid.
    private static boolean fitsTwoByTwo(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) return shaped.getWidth() <= 2 && shaped.getHeight() <= 2;
        //? if >=26.1 {
        return !(recipe instanceof CraftingRecipe) || recipe.placementInfo().ingredients().size() <= 4;
        //?} else
        /*return !(recipe instanceof CraftingRecipe) || recipe.getIngredients().stream().filter(i -> !i.isEmpty()).count() <= 4;*/
    }

    // A crafting table or stonecutter in any of the six neighbouring blocks, as a player would reach one.
    private static boolean nextTo(RobotEntity robot, Block block) {
        BlockPos pos = robot.blockPosition();
        for (Direction d : Direction.values()) {
            if (robot.level().getBlockState(pos.relative(d)).is(block)) return true;
        }
        return false;
    }
}
