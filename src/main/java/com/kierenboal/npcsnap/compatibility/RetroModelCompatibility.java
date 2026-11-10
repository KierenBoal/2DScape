package com.kierenboal.npcsnap.compatibility;

import com.kierenboal.npcsnap.rendering.OwnedModel;
import com.kierenboal.npcsnap.state.BillboardUpdateScheduler;
import com.kierenboal.npcsnap.targeting.ClassifiedObjectType;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.ObservedTileObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.events.PluginMessage;

/** Optional model bridge. Provider and capture state belongs to the client thread. */
@Singleton
@Slf4j
public final class RetroModelCompatibility
{
	private static final String NAMESPACE = "npc-model-swap";
	private static final String SOURCE = "retro-npc-swapper";

	private final Client client;
	private Scene scene;
	private boolean seeded;
	private final Set<TileObject> known = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<TileObject> pending = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<TileObject> replaced = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<TileObject, Map<RuneLiteObjectController, StandInRenderable>> renderables = new IdentityHashMap<>();
	private final Map<TileObject, SceneryCapture> captures = new IdentityHashMap<>();
	private volatile Suppression suppressed = Suppression.EMPTY;
	private Function<NPC, Model> npcModel;
	private BiFunction<Player, Model, Model> playerModel;
	private Function<TileObject, List<?>> sceneryStandIns;
	private Function<RuneLiteObjectController, Model> sceneryModel;

	@Inject
	public RetroModelCompatibility(Client client)
	{
		this.client = client;
	}

	public PluginMessage request()
	{
		return new PluginMessage(NAMESPACE, "modelsSyncReq", Map.of("version", 1, "source", "2dscape"));
	}

	@SuppressWarnings("unchecked")
	public boolean accept(PluginMessage event)
	{
		Map<String, Object> data = event.getData();
		if (!client.isClientThread() || !NAMESPACE.equals(event.getNamespace()) || !"models".equals(event.getName())
			|| data == null || !SOURCE.equals(data.get("source")))
		{
			return false;
		}

		boolean supported = Integer.valueOf(1).equals(data.get("version"));
		boolean hadProviders = npcModel != null || playerModel != null || hasScenery();
		npcModel = supported && data.get("npcModel") instanceof Function
			? (Function<NPC, Model>) data.get("npcModel") : null;
		playerModel = supported && data.get("playerModel") instanceof BiFunction
			? (BiFunction<Player, Model, Model>) data.get("playerModel") : null;
		boolean scenery = supported && data.get("sceneryStandIns") instanceof Function
			&& data.get("sceneryModel") instanceof Function;
		sceneryStandIns = scenery ? (Function<TileObject, List<?>>) data.get("sceneryStandIns") : null;
		sceneryModel = scenery ? (Function<RuneLiteObjectController, Model>) data.get("sceneryModel") : null;
		clearCaptures();
		replaced.clear();
		pending.addAll(known);
		return supported || hadProviders;
	}

	public void clear()
	{
		npcModel = null;
		playerModel = null;
		sceneryStandIns = null;
		sceneryModel = null;
		resetScene(null);
	}

	public void beginFrame(Scene next)
	{
		if (scene != next)
		{
			resetScene(next);
		}
		captures.clear();
		suppressed = Suppression.EMPTY;
	}

	public void resetScene(Scene next)
	{
		scene = next;
		seeded = false;
		known.clear();
		pending.clear();
		replaced.clear();
		clearCaptures();
	}

	private void clearCaptures()
	{
		suppressed = Suppression.EMPTY;
		captures.clear();
		renderables.clear();
	}

	public void track(TileObject object)
	{
		if (object != null && known.add(object))
		{
			pending.add(object);
		}
	}

	public void untrack(TileObject object)
	{
		known.remove(object);
		pending.remove(object);
		replaced.remove(object);
		captures.remove(object);
		renderables.remove(object);
		Map<TileObject, List<Placement>> snapshot = suppressed.byOwner;
		if (snapshot.containsKey(object))
		{
			Map<TileObject, List<Placement>> next = new IdentityHashMap<>(snapshot);
			next.remove(object);
			suppressed = new Suppression(next);
		}
	}

	public void addCandidates(Map<TileObject, ObservedTileObject> into, Predicate<TileObject> visible)
	{
		if (!client.isClientThread() || !hasScenery())
		{
			return;
		}
		if (!seeded)
		{
			seedScene();
			seeded = true;
		}
		for (TileObject object : pending)
		{
			if (!standIns(object).isEmpty())
			{
				replaced.add(object);
			}
			if (!hasScenery())
			{
				return;
			}
		}
		pending.clear();

		// Stage results so a provider failure cannot leave partial Retro candidates in the frame.
		Map<TileObject, ObservedTileObject> additions = new IdentityHashMap<>();
		for (TileObject object : replaced)
		{
			if (!visible.test(object))
			{
				continue;
			}
			ObservedTileObject observed = observed(object);
			if (!hasScenery())
			{
				return;
			}
			if (observed != null)
			{
				additions.put(object, observed);
			}
		}
		into.putAll(additions);
	}

	private void seedScene()
	{
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		if (tiles == null)
		{
			return;
		}
		for (Tile[][] plane : tiles)
		{
			for (Tile[] row : plane)
			{
				for (Tile tile : row)
				{
					if (tile != null)
					{
						GameObject[] objects = tile.getGameObjects();
						if (objects != null)
						{
							for (GameObject object : objects)
							{
								track(object);
							}
						}
						track(tile.getDecorativeObject());
					}
				}
			}
		}
	}

	public ObservedTileObject observed(TileObject object)
	{
		if (object == null || !client.isClientThread() || !hasScenery()
			|| (known.contains(object) && !replaced.contains(object) && !pending.contains(object)))
		{
			return null;
		}
		SceneryCapture capture = captures.get(object);
		if (capture == null)
		{
			capture = captureScenery(object);
			if (hasScenery())
			{
				captures.put(object, capture);
			}
		}
		return capture.observed;
	}

	private SceneryCapture captureScenery(TileObject object)
	{
		List<?> controllers = standIns(object);
		if (controllers.isEmpty())
		{
			renderables.remove(object);
			return SceneryCapture.EMPTY;
		}

		Map<RuneLiteObjectController, StandInRenderable> previous = renderables.getOrDefault(object, Collections.emptyMap());
		Map<RuneLiteObjectController, StandInRenderable> current = new IdentityHashMap<>();
		List<ObjectRenderablePart> parts = new ArrayList<>(controllers.size());
		List<Placement> placements = new ArrayList<>(controllers.size());
		try
		{
			for (Object entry : controllers)
			{
				if (!(entry instanceof RuneLiteObjectController))
				{
					throw new IllegalArgumentException("Invalid Retro scenery controller");
				}
				RuneLiteObjectController controller = (RuneLiteObjectController) entry;
				Model model = sceneryModel.apply(controller);
				if (model == null)
				{
					renderables.remove(object);
					return SceneryCapture.EMPTY;
				}
				OwnedModel owned = new OwnedModel(model);
				StandInRenderable renderable = previous.get(controller);
				if (renderable == null)
				{
					renderable = new StandInRenderable();
				}
				renderable.model = owned;
				current.put(controller, renderable);
				LocalPoint location = controller.getLocation();
				int level = controller.getLevel();
				int z = controller.getZ();
				parts.add(new ObjectRenderablePart(renderable, location, level, z, controller.getOrientation()));
				placements.add(new Placement(controller.getModel(), model, location, level, z));
			}
		}
		catch (RuntimeException | AssertionError | LinkageError ex)
		{
			disableScenery(ex);
			return SceneryCapture.EMPTY;
		}
		renderables.put(object, current);
		return new SceneryCapture(new ObservedTileObject(object, List.copyOf(parts), ClassifiedObjectType.OBJECT),
			List.copyOf(placements));
	}

	private List<?> standIns(TileObject object)
	{
		try
		{
			List<?> controllers = sceneryStandIns.apply(object);
			return controllers == null ? Collections.emptyList() : new ArrayList<>(controllers);
		}
		catch (RuntimeException | AssertionError | LinkageError ex)
		{
			disableScenery(ex);
			return Collections.emptyList();
		}
	}

	private boolean hasScenery()
	{
		return sceneryModel != null;
	}

	private void disableScenery(Throwable ex)
	{
		sceneryStandIns = null;
		sceneryModel = null;
		clearCaptures();
		log.debug("Retro scenery provider disabled", ex);
	}

	public boolean replaces(TileObject object)
	{
		SceneryCapture capture = captures.get(object);
		return capture != null && capture.observed != null;
	}

	public void publishSuppression(Set<TileObject> selected, Predicate<Renderable> spriteCached)
	{
		if (captures.isEmpty())
		{
			suppressed = Suppression.EMPTY;
			return;
		}
		Map<TileObject, List<Placement>> snapshot = new IdentityHashMap<>();
		for (TileObject object : selected)
		{
			SceneryCapture capture = captures.get(object);
			if (capture != null && capture.observed != null
				&& BillboardUpdateScheduler.allTileObjectPartsCached(capture.observed.parts, spriteCached))
			{
				snapshot.put(object, capture.placements);
			}
		}
		suppressed = snapshot.isEmpty() ? Suppression.EMPTY : new Suppression(snapshot);
	}

	public boolean suppressesStandIns(TileObject object)
	{
		return suppressed.byOwner.containsKey(object);
	}

	/** Render workers only compare draw arguments against the published placement snapshot. */
	public boolean hidden(Scene scene, GameObject object, Model model)
	{
		List<Placement> placements = suppressed.byModel.get(model);
		if (placements == null || scene == null || object == null)
		{
			return false;
		}
		Renderable renderable = object.getRenderable();
		if (renderable instanceof Actor || renderable instanceof ActorSpotAnim || renderable instanceof GraphicsObject
			|| renderable instanceof Projectile || renderable instanceof TileItem)
		{
			return false;
		}
		int worldView = scene.getWorldViewId();
		int level = object.getPlane();
		int x = object.getX();
		int y = object.getY();
		int z = object.getZ();
		for (Placement placement : placements)
		{
			if (worldView == placement.location.getWorldView() && level == placement.level
				&& x == placement.location.getX() && y == placement.location.getY() && z == placement.z)
			{
				return true;
			}
		}
		return false;
	}

	public boolean isStandIn(Renderable renderable)
	{
		return renderable instanceof StandInRenderable;
	}

	public Model model(Actor actor)
	{
		if (client.isClientThread())
		{
			if (actor instanceof NPC && npcModel != null)
			{
				try
				{
					Model model = npcModel.apply((NPC) actor);
					if (model != null)
					{
						return new OwnedModel(model);
					}
				}
				catch (RuntimeException | AssertionError | LinkageError ex)
				{
					npcModel = null;
					log.debug("Retro NPC model provider disabled", ex);
				}
			}
			else if (actor instanceof Player && playerModel != null)
			{
				Model vanilla = actor.getModel();
				if (vanilla != null)
				{
					try
					{
						Model model = playerModel.apply((Player) actor, vanilla);
						return model == null ? vanilla : new OwnedModel(model);
					}
					catch (RuntimeException | AssertionError | LinkageError ex)
					{
						playerModel = null;
						log.debug("Retro player model provider disabled", ex);
					}
				}
				return vanilla;
			}
		}
		return actor.getModel();
	}

	private static final class SceneryCapture
	{
		private static final SceneryCapture EMPTY = new SceneryCapture(null, Collections.emptyList());
		private final ObservedTileObject observed;
		private final List<Placement> placements;

		private SceneryCapture(ObservedTileObject observed, List<Placement> placements)
		{
			this.observed = observed;
			this.placements = placements;
		}
	}

	private static final class Placement
	{
		private final Model carrier;
		private final Model drawnModel;
		private final LocalPoint location;
		private final int level;
		private final int z;

		private Placement(Model carrier, Model drawnModel, LocalPoint location, int level, int z)
		{
			this.carrier = carrier;
			this.drawnModel = drawnModel;
			this.location = location;
			this.level = level;
			this.z = z;
		}
	}

	private static final class Suppression
	{
		private static final Suppression EMPTY = new Suppression(Collections.emptyMap());
		private final Map<TileObject, List<Placement>> byOwner;
		private final Map<Model, List<Placement>> byModel;

		private Suppression(Map<TileObject, List<Placement>> placementsByOwner)
		{
			byOwner = Collections.unmodifiableMap(placementsByOwner);
			Map<Model, List<Placement>> index = new IdentityHashMap<>();
			for (List<Placement> placements : byOwner.values())
			{
				for (Placement placement : placements)
				{
					index.computeIfAbsent(placement.drawnModel, model -> new ArrayList<>()).add(placement);
					if (placement.carrier != null && placement.carrier != placement.drawnModel)
					{
						index.computeIfAbsent(placement.carrier, model -> new ArrayList<>()).add(placement);
					}
				}
			}
			index.replaceAll((model, placements) -> List.copyOf(placements));
			byModel = Collections.unmodifiableMap(index);
		}
	}

	private static final class StandInRenderable implements Renderable
	{
		@Delegate(types = Renderable.class, excludes = CapturedModel.class)
		private OwnedModel model;

		@Override
		public Model getModel()
		{
			return model;
		}
	}

	private interface CapturedModel
	{
		Model getModel();
	}
}
