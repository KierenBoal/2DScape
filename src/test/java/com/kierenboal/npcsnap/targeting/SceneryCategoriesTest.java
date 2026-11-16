package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.NpcSnapConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class SceneryCategoriesTest
{
	@Test
	public void everyChopStyleOptionMarksATree()
	{
		assertEquals(SceneryCategory.TREE, category("Oak tree", "Chop down"));
		assertEquals(SceneryCategory.TREE, category("Achey Tree", "Chop"));
		assertEquals(SceneryCategory.TREE, category("Redwood", "Cut"));
		assertEquals(SceneryCategory.TREE, category("Arctic pine", "Cut down"));
		assertEquals(SceneryCategory.TREE, category("Yew", null, "Chop down", null));
		assertEquals(SceneryCategory.TREE, category("Dramen", "Chop-down"));
		assertEquals(SceneryCategory.TREE, category("Teak", "CHOP DOWN"));
	}

	@Test
	public void stumpsAndUnchoppableTreesCountAsTreesByName()
	{
		assertEquals(SceneryCategory.TREE, category("Tree stump"));
		assertEquals(SceneryCategory.TREE, category("Magic tree Stump"));
		assertEquals(SceneryCategory.TREE, category("Dead tree"));
		assertEquals(SceneryCategory.TREE, category("Trees", "Examine"));
	}

	@Test
	public void rocksAreMineableOrNamedAsRock()
	{
		assertEquals(SceneryCategory.ROCK, category("Copper rocks", "Mine"));
		assertEquals(SceneryCategory.ROCK, category("Gem vein", "Mine"));
		assertEquals(SceneryCategory.ROCK, category("Rocks"));
		assertEquals(SceneryCategory.ROCK, category("Boulder", "Push"));
	}

	@Test
	public void plantsAreNamedAsPlants()
	{
		assertEquals(SceneryCategory.PLANT, category("Bush"));
		assertEquals(SceneryCategory.PLANT, category("Fern"));
		assertEquals(SceneryCategory.PLANT, category("Flowers", "Pick"));
		assertEquals(SceneryCategory.PLANT, category("Cactus"));
		assertEquals(SceneryCategory.PLANT, category("Wheat", "Pick"));
		assertEquals(SceneryCategory.PLANT, category("Potato", "Pick"));
		assertEquals(SceneryCategory.PLANT, category("Nettles", "Pick"));
		assertEquals(SceneryCategory.PLANT, category("Onion", "Pick"));
		assertEquals(SceneryCategory.PLANT, category("Cabbage", "Pick"));
		assertEquals(SceneryCategory.PLANT, category("Sweetcorn"));
		assertEquals(SceneryCategory.PLANT, category("Flax", "Pick"));
	}

	@Test
	public void firesAreNamedAsFires()
	{
		assertEquals(SceneryCategory.FIRE, category("Fire"));
		assertEquals(SceneryCategory.FIRE, category("Campfire", "Tend-to"));
		assertEquals(SceneryCategory.FIRE, category("Forester's Campfire"));
		assertEquals(SceneryCategory.FIRE, category("Fire pit", "Light"));
	}

	@Test
	public void logsAreNamedAsLogs()
	{
		assertEquals(SceneryCategory.LOG, category("Hollow log"));
		assertEquals(SceneryCategory.LOG, category("Log pile", "Take"));
		assertEquals(SceneryCategory.LOG, category("Logs"));
		assertEquals("A hollow tree is still a tree", SceneryCategory.TREE, category("Hollow tree", "Chop down"));
	}

	@Test
	public void everythingElseIsOther()
	{
		assertEquals(SceneryCategory.OTHER, category("Fireplace"));
		assertEquals(SceneryCategory.OTHER, category("Logbook", "Read"));
		assertEquals(SceneryCategory.OTHER, SceneryCategories.categoryOfDefinition(null));
		assertEquals(SceneryCategory.OTHER, category("Crate", "Search"));
		assertEquals(SceneryCategory.OTHER, category("Fence"));
		assertEquals(SceneryCategory.OTHER, category("Rocking chair", "Sit"));
		assertEquals(SceneryCategory.OTHER, category("Treehouse"));
		assertEquals(SceneryCategory.OTHER, category(null, "Chop wood"));
		assertEquals(SceneryCategory.OTHER, category("null"));
	}

	@Test
	public void settingsChooseWhichCategoriesConvert()
	{
		assertEquals(EnumSet.noneOf(SceneryCategory.class), SceneryCategories.enabled(config(false, false, false)));
		assertEquals(EnumSet.of(SceneryCategory.TREE), SceneryCategories.enabled(config(true, false, false)));
		assertEquals("Every world object box starts unticked",
			EnumSet.noneOf(SceneryCategory.class), SceneryCategories.enabled(config(false, true, false)));
		assertEquals(EnumSet.of(SceneryCategory.TREE, SceneryCategory.OTHER), SceneryCategories.enabled(config(true, true, true)));
		assertEquals("The category boxes do nothing while world objects are off",
			EnumSet.noneOf(SceneryCategory.class), SceneryCategories.enabled(config(false, false, true)));
		NpcSnapConfig firesOnly = new NpcSnapConfig()
		{
			@Override public boolean applyToObjects() { return true; }
			@Override public boolean worldObjectFires() { return true; }
		};
		assertEquals("Each box only switches its own category",
			EnumSet.of(SceneryCategory.FIRE), SceneryCategories.enabled(firesOnly));
		NpcSnapConfig everything = new NpcSnapConfig()
		{
			@Override public boolean applyToTrees() { return true; }
			@Override public boolean applyToObjects() { return true; }
			@Override public boolean worldObjectRocks() { return true; }
			@Override public boolean worldObjectPlants() { return true; }
			@Override public boolean worldObjectFires() { return true; }
			@Override public boolean worldObjectLogs() { return true; }
			@Override public boolean worldObjectOther() { return true; }
		};
		assertEquals(EnumSet.allOf(SceneryCategory.class), SceneryCategories.enabled(everything));
	}

	@Test
	public void objectsAreOnlyHandledWhileSomeCategoryConverts()
	{
		List<NpcSnapConfig> settings = new ArrayList<>(List.of(config(false, false, false), config(true, false, false),
			config(false, true, false), config(true, true, true), config(false, false, true), config(false, true, true)));
		for (SceneryCategory category : SceneryCategory.values())
		{
			NpcSnapConfig only = only(category);
			assertEquals(EnumSet.of(category), SceneryCategories.enabled(only));
			settings.add(only);
		}
		for (NpcSnapConfig config : settings)
		{
			Set<SceneryCategory> enabled = SceneryCategories.enabled(config);
			assertEquals(enabled.toString(), !enabled.isEmpty(), SceneryCategories.anyEnabled(config));
			assertEquals(enabled.toString(), !enabled.isEmpty(), ObjectClassifier.isEnabled(ClassifiedObjectType.OBJECT, config));
		}
		assertFalse("World objects with no box ticked convert nothing",
			ObjectClassifier.isEnabled(ClassifiedObjectType.OBJECT, config(false, true, false)));
	}

	@Test
	public void transformRevisionMovesOnlyWhenAKnownFormMayHaveChanged()
	{
		Map<Integer, ObjectComposition> definitions = patch(new ObjectComposition[] {form(10, "Weeds", new AtomicInteger())});
		definitions.put(ObjectID.TREE, composition("Tree", "Chop down"));
		SceneryCategories categories = new SceneryCategories(client(definitions, new AtomicInteger()));
		int start = categories.transformRevision();

		categories.categoryOf(object(ObjectID.TREE));
		categories.invalidateTransforms();
		assertEquals("Plain objects never change form", start, categories.transformRevision());

		categories.categoryOf(object(1));
		assertEquals(start, categories.transformRevision());
		categories.invalidateTransforms();
		assertNotEquals(start, categories.transformRevision());
	}

	@Test
	public void plainObjectsAreLookedUpOncePerId()
	{
		AtomicInteger lookups = new AtomicInteger();
		Map<Integer, ObjectComposition> definitions = new HashMap<>();
		definitions.put(ObjectID.TREE, composition("Tree", "Chop down"));
		definitions.put(2, composition("Crate", "Search"));
		SceneryCategories categories = new SceneryCategories(client(definitions, lookups));

		assertEquals(SceneryCategory.TREE, categories.categoryOf(object(ObjectID.TREE)));
		assertEquals(SceneryCategory.TREE, categories.categoryOf(object(ObjectID.TREE)));
		assertEquals(SceneryCategory.OTHER, categories.categoryOf(object(2)));
		assertEquals(SceneryCategory.OTHER, categories.categoryOf(object(2)));
		assertEquals(SceneryCategory.OTHER, categories.categoryOf(object(3)));
		assertEquals(SceneryCategory.OTHER, categories.categoryOf(null));
		assertEquals(3, lookups.get());
	}

	@Test
	public void transformedObjectsFollowTheirCurrentForm()
	{
		ObjectComposition[] current = {form(10, "Weeds", new AtomicInteger(), "Rake")};
		SceneryCategories categories = new SceneryCategories(client(patch(current), new AtomicInteger()));

		assertEquals(SceneryCategory.OTHER, categories.categoryOf(object(1)));
		current[0] = form(11, "Oak", new AtomicInteger(), "Chop down");
		assertEquals("A transform is only re-read after it is invalidated",
			SceneryCategory.OTHER, categories.categoryOf(object(1)));
		categories.invalidateTransforms();
		assertEquals(SceneryCategory.TREE, categories.categoryOf(object(1)));
		current[0] = null;
		categories.invalidateTransforms();
		assertEquals(SceneryCategory.OTHER, categories.categoryOf(object(1)));
	}

	@Test
	public void eachFormIsClassifiedOnlyOnceHoweverOftenItReturns()
	{
		AtomicInteger nameReads = new AtomicInteger();
		ObjectComposition weeds = form(10, "Weeds", nameReads, "Rake");
		ObjectComposition oak = form(11, "Oak", nameReads, "Chop down");
		ObjectComposition[] current = {weeds};
		SceneryCategories categories = new SceneryCategories(client(patch(current), new AtomicInteger()));

		for (int growth = 0; growth < 3; growth++)
		{
			current[0] = weeds;
			categories.invalidateTransforms();
			assertEquals(SceneryCategory.OTHER, categories.categoryOf(object(1)));
			current[0] = oak;
			categories.invalidateTransforms();
			assertEquals(SceneryCategory.TREE, categories.categoryOf(object(1)));
		}
		assertEquals("A varbit change must not re-read a form already classified", 2, nameReads.get());
	}

	/** Object 1 is a patch showing whichever form the holder currently names. */
	private static Map<Integer, ObjectComposition> patch(ObjectComposition[] current)
	{
		Map<Integer, ObjectComposition> definitions = new HashMap<>();
		definitions.put(1, proxy(ObjectComposition.class, method("getName", "null"),
			method("getImpostorIds", new int[] {10, 11}), methodSupplier("getImpostor", () -> current[0])));
		return definitions;
	}

	private static ObjectComposition form(int id, String name, AtomicInteger nameReads, String... actions)
	{
		return proxy(ObjectComposition.class, method("getId", id), method("getActions", actions),
			methodSupplier("getName", () ->
			{
				nameReads.incrementAndGet();
				return name;
			}));
	}

	private static NpcSnapConfig config(boolean trees, boolean objects, boolean other)
	{
		return new NpcSnapConfig()
		{
			@Override public boolean applyToTrees() { return trees; }
			@Override public boolean applyToObjects() { return objects; }
			@Override public boolean worldObjectOther() { return other; }
		};
	}

	private static NpcSnapConfig only(SceneryCategory category)
	{
		return new NpcSnapConfig()
		{
			@Override public boolean applyToTrees() { return category == SceneryCategory.TREE; }
			@Override public boolean applyToObjects() { return category != SceneryCategory.TREE; }
			@Override public boolean worldObjectRocks() { return category == SceneryCategory.ROCK; }
			@Override public boolean worldObjectPlants() { return category == SceneryCategory.PLANT; }
			@Override public boolean worldObjectFires() { return category == SceneryCategory.FIRE; }
			@Override public boolean worldObjectLogs() { return category == SceneryCategory.LOG; }
			@Override public boolean worldObjectOther() { return category == SceneryCategory.OTHER; }
		};
	}

	private static SceneryCategory category(String name, String... actions)
	{
		return SceneryCategories.categoryOfDefinition(composition(name, actions));
	}

	private static TileObject object(int id)
	{
		return proxy(GameObject.class, method("getId", id));
	}

	private static ObjectComposition composition(String name, String... actions)
	{
		return proxy(ObjectComposition.class, method("getName", name), method("getActions", actions));
	}

	/** getObjectDefinition takes an argument, which the shared proxy helper cannot see. */
	private static Client client(Map<Integer, ObjectComposition> definitions, AtomicInteger lookups)
	{
		return (Client) java.lang.reflect.Proxy.newProxyInstance(Client.class.getClassLoader(),
			new Class<?>[] {Client.class}, (instance, invoked, args) ->
			{
				if ("getObjectDefinition".equals(invoked.getName()))
				{
					lookups.incrementAndGet();
					return definitions.get((Integer) args[0]);
				}
				return null;
			});
	}
}
