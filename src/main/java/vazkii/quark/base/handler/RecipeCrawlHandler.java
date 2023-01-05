package vazkii.quark.base.handler;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.quark.api.event.RecipeCrawlEvent;
import vazkii.quark.api.event.RecipeCrawlEvent.Visit;
import vazkii.quark.base.Quark;

@EventBusSubscriber(bus = Bus.FORGE, modid = Quark.MOD_ID)
public class RecipeCrawlHandler {

	private static Multimap<Item, ItemStack> recipeDigestion = HashMultimap.create();
	
	private static boolean lock = false;

	// We don't actually need to register a reload listener for anything in particular, all that matters is that we unlock
	@SubscribeEvent
	public static void addListener(AddReloadListenerEvent event) {
		clear();
	}
	
	@SubscribeEvent
	public static void tick(LevelTickEvent event) {
		if(!lock && event.phase == Phase.END) {
			lock = true;
			load(event.level);
		}
	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public static void tick(ClientTickEvent event) {
		if(Minecraft.getInstance().level == null)
			clear();
	}

	private static void clear() {
		if(lock) {
			lock = false;
			
			MinecraftForge.EVENT_BUS.post(new RecipeCrawlEvent.Reset());
		}
	}

	private static void load(Level level) {
		RecipeManager manager = level.getRecipeManager();
		if(!manager.getRecipes().isEmpty()) {
			MinecraftForge.EVENT_BUS.post(new RecipeCrawlEvent.CrawlStarting());
			
			recipeDigestion.clear();
			Collection<Recipe<?>> recipes = manager.getRecipes();

			for(Recipe<?> recipe : recipes) {
				if(recipe == null || recipe.getResultItem() == null || recipe.getIngredients() == null)
					continue;
				
				RecipeCrawlEvent.Visit<?> event;
				
				if(recipe instanceof ShapedRecipe sr)
					event = new Visit.Shaped(sr);
				else if(recipe instanceof ShapelessRecipe sr)
					event = new Visit.Shapeless(sr);
				else if(recipe instanceof CustomRecipe cr)
					event = new Visit.Custom(cr);
				else if(recipe instanceof AbstractCookingRecipe acr)
					event = new Visit.Cooking(acr);
				else 
					event = new Visit.Misc(recipe);
				
				digest(recipe);
				MinecraftForge.EVENT_BUS.post(event);
			}
			
			MinecraftForge.EVENT_BUS.post(new RecipeCrawlEvent.Digest(recipeDigestion));
		}
	}
	
	private static void digest(Recipe<?> recipe) {
		ItemStack out = recipe.getResultItem();
		
		NonNullList<Ingredient> ingredients = recipe.getIngredients();
		for(Ingredient ingredient : ingredients) {
			for (ItemStack inStack : ingredient.getItems())
				recipeDigestion.put(inStack.getItem(), out);
		}
	}
	
	/*
	 * Derivation list -> items to add and then derive (raw materials)
	 * Whitelist -> items to add and not derive from
	 * Blacklist -> items to ignore
	 */
	
	public static void recursivelyFindCraftedItemsFromStrings(@Nullable Collection<String> derivationList, @Nullable Collection<String> whitelist, @Nullable Collection<String> blacklist, Consumer<Item> callback) {
		List<Item> parsedDerivationList = derivationList == null ? null : MiscUtil.massRegistryGet(derivationList, ForgeRegistries.ITEMS);
		List<Item> parsedWhitelist      = whitelist == null      ? null : MiscUtil.massRegistryGet(whitelist, ForgeRegistries.ITEMS);
		List<Item> parsedBlacklist      = blacklist == null      ? null : MiscUtil.massRegistryGet(blacklist, ForgeRegistries.ITEMS);
		
		recursivelyFindCraftedItems(parsedDerivationList, parsedWhitelist, parsedBlacklist, callback);
	}
	
	public static void recursivelyFindCraftedItems(@Nullable Collection<Item> derivationList, @Nullable Collection<Item> whitelist, @Nullable Collection<Item> blacklist, Consumer<Item> callback) {
		Collection<Item> trueDerivationList = derivationList == null  ? Lists.newArrayList() : derivationList;
		Collection<Item> trueWhitelist      = whitelist == null       ? Lists.newArrayList() : whitelist;
		Collection<Item> trueBlacklist      = blacklist == null       ? Lists.newArrayList() : blacklist;
		
		Streams.concat(trueDerivationList.stream(), trueWhitelist.stream()).forEach(callback);
		
		Set<Item> scanned = Sets.newHashSet(trueDerivationList);
		List<Item> toScan = Lists.newArrayList(trueDerivationList);

		while (!toScan.isEmpty()) {
			Item scan = toScan.remove(0);

			if (recipeDigestion.containsKey(scan)) {
				for (ItemStack digestedStack : recipeDigestion.get(scan)) {
					Item candidate = digestedStack.getItem();
					
					if (!scanned.contains(candidate)) {
						scanned.add(candidate);
						toScan.add(candidate);

						if(!trueBlacklist.contains(candidate))
							callback.accept(candidate);
					}
				}
			}
		}
	}
	
}