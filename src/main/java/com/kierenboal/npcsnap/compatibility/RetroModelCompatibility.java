package com.kierenboal.npcsnap.compatibility;

import com.kierenboal.npcsnap.rendering.OwnedModel;
import java.util.*;
import java.util.function.Function;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.events.PluginMessage;

/**
 * Optional, classloader-independent model bridge. The providers and the maps here are client-thread
 * only; the GPU plugin's render threads reach this through drawObject and are answered from
 * {@link #published} instead.
 */
@Singleton
@Slf4j
public final class RetroModelCompatibility
{
    private Scene scene;
    private final Set<TileObject> known = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TileObject> replaced = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean dirty = true;
    private final Map<RuneLiteObjectController, StandInRenderable> renderables = new IdentityHashMap<>();
    private final Map<RuneLiteObjectController, TileObject> owners = new IdentityHashMap<>();
    public java.util.function.Predicate<TileObject> visible = object -> true;
    public java.util.function.Predicate<TileObject> hide = object -> false;
    public volatile java.util.function.BooleanSupplier clientThread = () -> true;
    /** The last client-thread scan, never mutated once assigned, for callers on other threads. */
    private volatile Map<TileObject, com.kierenboal.npcsnap.targeting.ObservedTileObject> published = Collections.emptyMap();
    private Function<NPC, Model> npcModel;
    private Function<TileObject, List<RuneLiteObjectController>> sceneryStandIns;
    private Function<RuneLiteObjectController, Model> sceneryModel;

    public PluginMessage request()
    {
        return new PluginMessage("npc-model-swap", "modelsSyncReq",
            Map.of("version", 1, "source", "2dscape"));
    }

    @SuppressWarnings("unchecked")
    public boolean accept(PluginMessage event)
    {
        Map<String, Object> data = event.getData();
        if (!"npc-model-swap".equals(event.getNamespace()) || !"models".equals(event.getName())
            || data == null || !Integer.valueOf(1).equals(data.get("version"))
            || !"retro-npc-swapper".equals(data.get("source"))) return false;
        npcModel = data.get("npcModel") instanceof Function ? (Function<NPC, Model>) data.get("npcModel") : null;
        sceneryStandIns = data.get("sceneryStandIns") instanceof Function
            ? (Function<TileObject, List<RuneLiteObjectController>>) data.get("sceneryStandIns") : null;
        sceneryModel = data.get("sceneryModel") instanceof Function
            ? (Function<RuneLiteObjectController, Model>) data.get("sceneryModel") : null;
        renderables.clear(); owners.clear(); dirty = true; published = Collections.emptyMap();
        return true;
    }

    public void clear() { scene = null; known.clear(); replaced.clear(); dirty = true; renderables.clear(); owners.clear(); published = Collections.emptyMap(); npcModel = null; sceneryStandIns = null; sceneryModel = null; }

    public void track(TileObject object) { if (object != null) { known.add(object); dirty = true; } }
    public void untrack(TileObject object)
    {
        known.remove(object); replaced.remove(object);
        owners.entrySet().removeIf(entry -> entry.getValue() == object);
        renderables.keySet().retainAll(owners.keySet());
        if (published.containsKey(object))
        {
            Map<TileObject, com.kierenboal.npcsnap.targeting.ObservedTileObject> next = new IdentityHashMap<>(published);
            next.remove(object);
            published = next;
        }
    }
    public void candidates(Scene next, Map<TileObject, com.kierenboal.npcsnap.targeting.ObservedTileObject> into)
    {
        if (scene != next)
        {
            known.clear(); replaced.clear(); owners.clear(); renderables.clear(); scene = next; dirty = true;
            if (next != null && next.getTiles() != null)
                for (Tile[][] plane : next.getTiles()) for (Tile[] row : plane) for (Tile tile : row)
                {
                    if (tile == null) continue;
                    if (tile.getGameObjects() != null) for (GameObject object : tile.getGameObjects()) if (object != null) known.add(object);
                    if (tile.getDecorativeObject() != null) known.add(tile.getDecorativeObject());
                }
        }
        if (dirty)
        {
            replaced.clear();
            for (TileObject object : known) if (!standIns(object).isEmpty()) replaced.add(object);
            dirty = false;
        }
        Map<TileObject, com.kierenboal.npcsnap.targeting.ObservedTileObject> scan = new IdentityHashMap<>();
        for (TileObject object : replaced)
        {
            com.kierenboal.npcsnap.targeting.ObservedTileObject observed = observed(object);
            if (observed != null) scan.put(object, observed);
            if (observed != null && visible.test(object)) into.put(object, observed);
            else into.remove(object);
        }
        published = scan;
    }

    /** Retains original object identity while replacing its visual parts. */
    public com.kierenboal.npcsnap.targeting.ObservedTileObject observed(TileObject object)
    {
        if (!clientThread.getAsBoolean()) return published.get(object);
        List<RuneLiteObjectController> controllers = standIns(object);
        if (controllers == null || controllers.isEmpty()) return null;
        List<com.kierenboal.npcsnap.targeting.ObjectRenderablePart> parts = new ArrayList<>();
        for (RuneLiteObjectController controller : controllers)
        {
            if (controller == null) continue;
            StandInRenderable renderable = renderables.get(controller);
            if (renderable == null)
            {
                Model captured = model(controller);
                if (captured == null) continue;
                renderable = new StandInRenderable(controller, captured);
                renderables.put(controller, renderable);
            }
            owners.put(controller, object);
            parts.add(new com.kierenboal.npcsnap.targeting.ObjectRenderablePart(renderable,
                controller.getLocation(), controller.getLevel(), controller.getZ(), controller.getOrientation()));
        }
        return new com.kierenboal.npcsnap.targeting.ObservedTileObject(object, parts,
            com.kierenboal.npcsnap.targeting.ClassifiedObjectType.OBJECT);
    }

    /** Match a carrier and its placement, never an unrelated actor on the same tile. */
    public boolean hidden(Scene scene, GameObject object, Model model)
    {
        if (object == null || object.getRenderable() instanceof Actor
            || object.getRenderable() instanceof ActorSpotAnim || object.getRenderable() instanceof Projectile
            || object.getRenderable() instanceof TileItem || !clientThread.getAsBoolean()) return false;
        for (Map.Entry<RuneLiteObjectController, TileObject> entry : owners.entrySet())
        {
            RuneLiteObjectController controller = entry.getKey();
            if (controller.getWorldView() != scene.getWorldViewId() || controller.getLevel() != object.getPlane()
                || controller.getX() != object.getX() || controller.getY() != object.getY()
                || controller.getZ() != object.getZ()) continue;
            if (!standIns(entry.getValue()).contains(controller)) continue;
            Model carrier = controller.getModel();
            if ((model == carrier || object.getRenderable().getModel() == carrier) && hide.test(entry.getValue())) return true;
        }
        return false;
    }

    private final class StandInRenderable implements Renderable
    {
        private final RuneLiteObjectController controller;
        @lombok.experimental.Delegate(types = Renderable.class, excludes = LiveModel.class)
        private final Model initial;
        StandInRenderable(RuneLiteObjectController controller, Model initial)
        { this.controller = controller; this.initial = initial; }
        public Model getModel() { return clientThread.getAsBoolean() ? model(controller) : initial; }
    }
    private interface LiveModel { Model getModel(); }

    public boolean replaces(TileObject object)
    {
        if (!clientThread.getAsBoolean()) return published.containsKey(object);
        return object != null && !standIns(object).isEmpty();
    }

    public boolean isStandIn(Renderable renderable) { return renderable instanceof StandInRenderable; }

    public Model model(Actor actor)
    {
        if (actor instanceof NPC && npcModel != null && clientThread.getAsBoolean())
        {
            try
            {
                Model model = npcModel.apply((NPC) actor);
                if (model != null) return new OwnedModel(model);
            }
            catch (RuntimeException ex) { log.debug("Retro NPC model unavailable", ex); }
        }
        return actor.getModel();
    }

    public List<RuneLiteObjectController> standIns(TileObject object)
    {
        if (!clientThread.getAsBoolean()) return Collections.emptyList();
        try { return sceneryStandIns == null ? Collections.emptyList() : sceneryStandIns.apply(object); }
        catch (RuntimeException ex) { log.debug("Retro scenery unavailable", ex); return Collections.emptyList(); }
    }

    public Model model(RuneLiteObjectController controller)
    {
        if (!clientThread.getAsBoolean()) return null;
        try
        {
            Model model = sceneryModel == null ? null : sceneryModel.apply(controller);
            return model == null ? null : new OwnedModel(model);
        }
        catch (RuntimeException ex) { log.debug("Retro scenery model unavailable", ex); return null; }
    }
}
