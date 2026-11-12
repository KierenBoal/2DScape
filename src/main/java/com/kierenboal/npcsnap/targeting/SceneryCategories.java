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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
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
	private static final Pattern NOT_LETTERS = Pattern.compile("[^a-z]+");
	// Stands in for a transforming object that currently shows nothing.
	private static final int NO_FORM = -1;

	private final Client client;
	// Concurrent because plugin shutdown clears these from outside the client thread.
	private final Map<Integer, SceneryCategory> plain = new ConcurrentHashMap<>();
	// Farming patches and other varbit-driven objects change form without respawning.
	// Which form shows is forgotten on every varbit change; what a form is never changes.
	private final Map<Integer, Integer> currentForms = new ConcurrentHashMap<>();
	private final Map<Integer, SceneryCategory> forms = new ConcurrentHashMap<>();
	private final AtomicInteger transformRevision = new AtomicInteger();

	public SceneryCategories(Client client)
	{
		this.client = client;
	}

	/** Whether the current settings turn any object into a sprite; cheaper than building the set. */
	public static boolean anyEnabled(NpcSnapConfig config)
	{
		return config.applyToTrees() || (config.applyToObjects() && (config.worldObjectRocks()
			|| config.worldObjectPlants() || config.worldObjectFires() || config.worldObjectLogs()
			|| config.worldObjectOther()));
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
		if (known != null)
		{
			return known;
		}
		Integer form = currentForms.get(id);
		if (form != null)
		{
			known = forms.get(form);
			if (known != null)
			{
				return known;
			}
		}

		ObjectComposition definition = client.getObjectDefinition(id);
		if (definition == null || definition.getImpostorIds() == null)
		{
			known = categoryOfDefinition(definition);
			plain.put(id, known);
			return known;
		}

		ObjectComposition shown = definition.getImpostor();
		form = shown == null ? NO_FORM : shown.getId();
		currentForms.put(id, form);
		known = forms.get(form);
		if (known == null)
		{
			known = categoryOfDefinition(shown);
			forms.put(form, known);
		}
		return known;
	}

	/** Call when a varbit changes: transformed objects may now show a different form. */
	public void invalidateTransforms()
	{
		if (!currentForms.isEmpty())
		{
			currentForms.clear();
			transformRevision.incrementAndGet();
		}
	}

	/** Moves whenever an answer categoryOf has already given may no longer hold. */
	public int transformRevision()
	{
		return transformRevision.get();
	}

	public void clear()
	{
		plain.clear();
		currentForms.clear();
		forms.clear();
		transformRevision.incrementAndGet();
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
			: new HashSet<>(Arrays.asList(NOT_LETTERS.split(name.toLowerCase(Locale.ROOT))));
	}
}
