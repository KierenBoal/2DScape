package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.NpcSnapConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;

/**
 * Sorts world objects into categories from their own definition (right-click options and
 * name), so new objects need no ID list. Unnamed decorative scenery cannot be told apart
 * and lands in OTHER. Look objects up on the client thread only: object definitions are
 * not thread safe.
 */
public final class SceneryCategories
{
	// Most trees / achey and blisterwood / redwood / arctic pine.
	private static final Set<String> CHOP_OPTIONS = Set.of("chop down", "chop", "cut", "cut down");
	private static final Set<String> TREE_WORDS = Set.of("tree", "trees", "stump");
	private static final Set<String> ROCK_WORDS = Set.of("rock", "rocks", "boulder", "boulders");
	private static final Set<String> PLANT_WORDS = Set.of("bush", "bushes", "plant", "plants", "fern", "ferns",
		"flower", "flowers", "shrub", "shrubs", "cactus", "cacti", "reeds", "wheat", "potato", "nettles", "onion",
		"cabbage", "sweetcorn", "flax");
	private static final Set<String> FIRE_WORDS = Set.of("fire", "fires", "campfire", "bonfire");
	private static final Set<String> LOG_WORDS = Set.of("log", "logs");

	private final Client client;
	// Concurrent because plugin shutdown clears these from outside the client thread.
	private final Map<Integer, SceneryCategory> plain = new ConcurrentHashMap<>();
	// Farming patches and other varbit-driven objects change form without respawning.
	private final Map<Integer, SceneryCategory> transformed = new ConcurrentHashMap<>();

	public SceneryCategories(Client client)
	{
		this.client = client;
	}

	/** The categories the current settings turn into sprites. */
	public static Set<SceneryCategory> enabled(NpcSnapConfig config)
	{
		Set<SceneryCategory> enabled = EnumSet.noneOf(SceneryCategory.class);
		if (config.applyToTrees()) enabled.add(SceneryCategory.TREE);
		if (config.applyToObjects())
		{
			if (config.worldObjectRocks()) enabled.add(SceneryCategory.ROCK);
			if (config.worldObjectPlants()) enabled.add(SceneryCategory.PLANT);
			if (config.worldObjectFires()) enabled.add(SceneryCategory.FIRE);
			if (config.worldObjectLogs()) enabled.add(SceneryCategory.LOG);
			if (config.worldObjectOther()) enabled.add(SceneryCategory.OTHER);
		}
		return enabled;
	}

	public SceneryCategory categoryOf(TileObject object)
	{
		if (object == null)
		{
			return SceneryCategory.OTHER;
		}

		int id = object.getId();
		SceneryCategory known = plain.get(id);
		if (known == null)
		{
			known = transformed.get(id);
		}
		if (known != null)
		{
			return known;
		}

		ObjectComposition definition = client.getObjectDefinition(id);
		boolean transforms = definition != null && definition.getImpostorIds() != null;
		SceneryCategory category = categoryOfDefinition(transforms ? definition.getImpostor() : definition);
		(transforms ? transformed : plain).put(id, category);
		return category;
	}

	/** Call when a varbit changes: transformed objects may now show a different form. */
	public void invalidateTransforms()
	{
		transformed.clear();
	}

	public void clear()
	{
		plain.clear();
		transformed.clear();
	}

	public static SceneryCategory categoryOfDefinition(ObjectComposition composition)
	{
		if (composition == null)
		{
			return SceneryCategory.OTHER;
		}

		Set<String> options = options(composition.getActions());
		Set<String> words = words(composition.getName());
		if (!Collections.disjoint(options, CHOP_OPTIONS) || !Collections.disjoint(words, TREE_WORDS))
		{
			return SceneryCategory.TREE;
		}
		if (options.contains("mine") || !Collections.disjoint(words, ROCK_WORDS))
		{
			return SceneryCategory.ROCK;
		}
		if (!Collections.disjoint(words, PLANT_WORDS))
		{
			return SceneryCategory.PLANT;
		}
		if (!Collections.disjoint(words, FIRE_WORDS))
		{
			return SceneryCategory.FIRE;
		}
		if (!Collections.disjoint(words, LOG_WORDS))
		{
			return SceneryCategory.LOG;
		}
		return SceneryCategory.OTHER;
	}

	private static Set<String> options(String[] actions)
	{
		Set<String> options = new HashSet<>();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (action != null) options.add(action.toLowerCase(Locale.ROOT).replace('-', ' ').trim());
			}
		}
		return options;
	}

	private static Set<String> words(String name)
	{
		return name == null ? Collections.emptySet()
			: new HashSet<>(Arrays.asList(name.toLowerCase(Locale.ROOT).split("[^a-z]+")));
	}
}
