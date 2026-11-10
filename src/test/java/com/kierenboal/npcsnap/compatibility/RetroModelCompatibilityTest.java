package com.kierenboal.npcsnap.compatibility;

import com.kierenboal.npcsnap.rendering.OwnedModel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.runelite.api.*;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class RetroModelCompatibilityTest
{
    private final Thread clientThread = Thread.currentThread();
    private final RetroModelCompatibility compat = new RetroModelCompatibility(
        proxy(Client.class, methodSupplier("isClientThread", () -> Thread.currentThread() == clientThread)));

    private PluginMessage state(Map<String, Object> fields)
    {
        Map<String, Object> data = new HashMap<>(fields);
        data.put("version", 1); data.put("source", "retro-npc-swapper");
        return new PluginMessage("npc-model-swap", "models", data);
    }

    @Test public void npcCaptureOwnsSharedArraysBeforeAnotherNpcIsPosed()
    {
        float[] vertices = {1, 2, 3}; int[] colors = {40}; byte[] alpha = {5};
        Model shared = proxy(Model.class, method("getVerticesX", vertices), method("getFaceColors1", colors),
            method("getFaceTransparencies", alpha));
        Model original = proxy(Model.class);
        NPC npc = proxy(NPC.class, method("getModel", original));
        compat.accept(state(Map.of("npcModel", (Function<NPC, Model>) actor -> shared)));
        Model captured = compat.model(npc);
        vertices[0] = 99; colors[0] = 80; alpha[0] = 100;
        assertEquals(1, captured.getVerticesX()[0], 0);
        assertEquals(40, captured.getFaceColors1()[0]);
        assertEquals(5, captured.getFaceTransparencies()[0]);
        assertSame(captured, captured.getModel());
        assertEquals(99, compat.model(npc).getVerticesX()[0], 0);
        compat.accept(state(Map.of()));
        assertSame(original, compat.model(npc));
    }

    @Test public void absentNullAndFailingProvidersFallBackWithoutChangingPlayers()
    {
        Model original = proxy(Model.class);
        NPC npc = proxy(NPC.class, method("getModel", original));
        Player player = proxy(Player.class, method("getModel", original));
        assertSame(original, compat.model(npc));
        compat.accept(state(Map.of("npcModel", (Function<NPC, Model>) actor -> null)));
        assertSame(original, compat.model(npc));
        compat.accept(state(Map.of("npcModel", (Function<NPC, Model>) actor -> { throw new IllegalStateException(); })));
        assertSame(original, compat.model(npc)); assertSame(original, compat.model(player));
        compat.clear(); assertSame(original, compat.model(npc));
    }

    @Test public void unrelatedOrIncompatibleMessagesCannotReplaceProvider()
    {
        Model vanilla = proxy(Model.class);
        NPC npc = proxy(NPC.class, method("getModel", vanilla));
        compat.accept(state(Map.of("npcModel", (Function<NPC, Model>) actor -> vanilla)));
        assertFalse(compat.accept(new PluginMessage("other", "models", Map.of())));
        assertTrue(compat.model(npc) instanceof OwnedModel);
        assertTrue(compat.accept(new PluginMessage("npc-model-swap", "models",
            Map.of("version", 2, "source", "retro-npc-swapper"))));
        assertSame(vanilla, compat.model(npc));
        assertEquals("2dscape", compat.request().getData().get("source"));
    }

    @Test public void sceneryKeepsOriginalIdentityAndSeparatePlacementForEachPart()
    {
        Model carrier = proxy(Model.class);
        RuneLiteObjectController first = new RuneLiteObjectController() { public Model getModel() { return carrier; } };
        first.setX(128); first.setY(256); first.setZ(33); first.setLevel(1); first.setOrientation(256);
        RuneLiteObjectController second = new RuneLiteObjectController() { public Model getModel() { return carrier; } };
        second.setX(150); second.setY(280); second.setLevel(1);
        TileObject original = proxy(TileObject.class);
        compat.accept(state(Map.of("sceneryStandIns", (Function<TileObject, List<RuneLiteObjectController>>) object -> List.of(first, second),
            "sceneryModel", (Function<RuneLiteObjectController, Model>) controller -> carrier)));
        var observed = compat.observed(original);
        assertSame(original, observed.tileObject); assertEquals(2, observed.parts.size());
        assertEquals(128, observed.parts.get(0).localPoint.getX());
        assertEquals(33, observed.parts.get(0).worldHeight);
        assertEquals(256, observed.parts.get(0).modelOrientation);
        assertEquals(1, observed.parts.get(0).plane);
        assertSame(observed.parts.get(0).renderable, compat.observed(original).parts.get(0).renderable);
        assertTrue(observed.parts.get(0).renderable.getModel() instanceof OwnedModel);
        compat.accept(state(Map.of())); assertNull(compat.observed(original));
    }
    @Test public void standInSuppressionRequiresCarrierPlacementAndReadySprite()
    {
        Model carrier = proxy(Model.class);
        RuneLiteObjectController controller = new RuneLiteObjectController() { public Model getModel() { return carrier; } };
        controller.setX(128); controller.setY(256); controller.setWorldView(0);
        TileObject original = proxy(TileObject.class);
        compat.accept(state(Map.of("sceneryStandIns", (Function<TileObject, List<RuneLiteObjectController>>) object -> List.of(controller),
            "sceneryModel", (Function<RuneLiteObjectController, Model>) item -> carrier)));
        compat.observed(original);
        Scene scene = proxy(Scene.class, method("getWorldViewId", 0));
        Renderable renderable = proxy(Renderable.class, method("getModel", carrier));
        GameObject placement = proxy(GameObject.class, method("getX", 128), method("getY", 256), method("getRenderable", renderable));
        assertFalse(compat.hidden(scene, placement, carrier));
        compat.publishSuppression(Set.of(original), part -> true);
        assertTrue(compat.hidden(scene, placement, carrier));
        GameObject other = proxy(GameObject.class, method("getX", 129), method("getY", 256), method("getRenderable", renderable));
        assertFalse(compat.hidden(scene, other, carrier));
        GameObject actor = proxy(GameObject.class, method("getX", 128), method("getY", 256), method("getRenderable", proxy(NPC.class)));
        assertFalse(compat.hidden(scene, actor, carrier));
        compat.untrack(original);
        assertFalse(compat.hidden(scene, placement, carrier));
    }

    @Test
    public void playerUsesTheVanillaPoseAndOwnsTheComposedModel()
    {
        float[] vertices = {5, 6, 7};
        Model vanilla = proxy(Model.class);
        Model composed = proxy(Model.class, method("getVerticesX", vertices));
        Player player = proxy(Player.class, method("getModel", vanilla));
        Player bare = proxy(Player.class, method("getModel", vanilla));
        compat.accept(state(Map.of("playerModel", (BiFunction<Player, Model, Model>) (actor, pose) ->
        {
            assertSame(vanilla, pose);
            return actor == player ? composed : null;
        })));
        Model captured = compat.model(player);
        vertices[0] = 100;
        assertEquals(5, captured.getVerticesX()[0], 0);
        assertSame(vanilla, compat.model(bare));

        compat.accept(state(Map.of("npcModel", (Function<NPC, Model>) npc -> composed)));
        assertSame(vanilla, compat.model(player));
    }

    @Test
    public void workerUsesPublishedSceneryWithoutCallingProviders() throws Exception
    {
        AtomicInteger calls = new AtomicInteger();
        Model carrier = proxy(Model.class);
        Model replacement = proxy(Model.class);
        TileObject owner = proxy(TileObject.class);
        RuneLiteObjectController controller = controller(carrier, 128);
        Player player = proxy(Player.class, method("getModel", carrier));
        NPC npc = proxy(NPC.class, method("getModel", carrier));
        compat.accept(state(Map.of(
            "npcModel", (Function<NPC, Model>) actor -> { calls.incrementAndGet(); return replacement; },
            "playerModel", (BiFunction<Player, Model, Model>) (actor, pose) -> { calls.incrementAndGet(); return replacement; },
            "sceneryStandIns", (Function<TileObject, List<RuneLiteObjectController>>) object ->
            { calls.incrementAndGet(); return List.of(controller); },
            "sceneryModel", (Function<RuneLiteObjectController, Model>) standIn ->
            { calls.incrementAndGet(); return replacement; })));
        compat.observed(owner);
        compat.observed(owner).parts.get(0).renderable.getModel();
        compat.publishSuppression(Set.of(owner), part -> true);
        assertEquals(2, calls.get());

        Scene scene = proxy(Scene.class);
        GameObject placement = placement(128);
        CompletableFuture.runAsync(() ->
        {
            assertNull(compat.observed(owner));
            assertSame(carrier, compat.model(player));
            assertSame(carrier, compat.model(npc));
            assertTrue(compat.hidden(scene, placement, carrier));
            assertTrue(compat.hidden(scene, placement, replacement));
            assertFalse(compat.hidden(scene, placement, proxy(Model.class)));
            assertFalse(compat.hidden(proxy(Scene.class, method("getWorldViewId", 1)), placement, carrier));
        }).get(5, TimeUnit.SECONDS);
        assertEquals(2, calls.get());

        compat.accept(state(Map.of()));
        assertFalse(compat.hidden(scene, placement, carrier));
        assertNull(compat.observed(owner));
    }

    @Test
    public void multipartSceneryRequiresEveryModelAndCachedSprite()
    {
        Model carrier = proxy(Model.class);
        TileObject owner = proxy(TileObject.class);
        RuneLiteObjectController first = controller(carrier, 128);
        RuneLiteObjectController second = controller(carrier, 256);
        AtomicBoolean available = new AtomicBoolean(true);
        compat.accept(state(Map.of(
            "sceneryStandIns", (Function<TileObject, List<RuneLiteObjectController>>) object -> List.of(first, second),
            "sceneryModel", (Function<RuneLiteObjectController, Model>) standIn ->
                standIn == second && !available.get() ? null : carrier)));

        Scene scene = proxy(Scene.class);
        compat.beginFrame(scene);
        Renderable firstPart = compat.observed(owner).parts.get(0).renderable;
        compat.publishSuppression(Set.of(owner), part -> part == firstPart);
        assertFalse(compat.hidden(scene, placement(128), carrier));
        compat.publishSuppression(Set.of(owner), part -> true);
        assertTrue(compat.hidden(scene, placement(128), carrier));
        assertTrue(compat.hidden(scene, placement(256), carrier));

        available.set(false);
        compat.beginFrame(scene);
        assertNull(compat.observed(owner));
        compat.publishSuppression(Set.of(owner), part -> true);
        assertFalse(compat.replaces(owner));
        assertFalse(compat.hidden(scene, placement(128), carrier));
    }

    @Test
    public void sceneryFailureClearsSuppressionAndRecoversOnPublication()
    {
        Model carrier = proxy(Model.class);
        TileObject owner = proxy(TileObject.class);
        TileObject failing = proxy(TileObject.class);
        NPC npc = proxy(NPC.class, method("getModel", carrier));
        RuneLiteObjectController controller = controller(carrier, 128);
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger failures = new AtomicInteger();
        PluginMessage providers = state(Map.of(
            "npcModel", (Function<NPC, Model>) actor -> carrier,
            "sceneryStandIns", (Function<TileObject, List<RuneLiteObjectController>>) object ->
            {
                if (object == failing && fail.get())
                {
                    failures.incrementAndGet();
                    throw new AssertionError("must be called on client thread");
                }
                return List.of(controller);
            },
            "sceneryModel", (Function<RuneLiteObjectController, Model>) standIn -> carrier));
        compat.accept(providers);
        Scene scene = proxy(Scene.class);
        compat.beginFrame(scene);
        compat.observed(owner);
        compat.publishSuppression(Set.of(owner), part -> true);
        assertTrue(compat.hidden(scene, placement(128), carrier));

        assertNull(compat.observed(failing));
        assertFalse(compat.hidden(scene, placement(128), carrier));
        assertNull(compat.observed(owner));
        assertNull(compat.observed(failing));
        assertEquals(1, failures.get());
        assertTrue(compat.model(npc) instanceof OwnedModel);

        fail.set(false);
        compat.accept(providers);
        assertNotNull(compat.observed(owner));
        compat.publishSuppression(Set.of(owner), part -> true);
        assertTrue(compat.hidden(scene, placement(128), carrier));
        compat.beginFrame(proxy(Scene.class));
        assertFalse(compat.hidden(scene, placement(128), carrier));
    }

    @Test
    public void playerLinkageFailureDisablesOnlyPlayerProviderUntilRepublished()
    {
        Model vanilla = proxy(Model.class);
        Player player = proxy(Player.class, method("getModel", vanilla));
        NPC npc = proxy(NPC.class, method("getModel", vanilla));
        AtomicInteger attempts = new AtomicInteger();
        Function<NPC, Model> npcModel = actor -> vanilla;
        compat.accept(state(Map.of("npcModel", npcModel,
            "playerModel", (BiFunction<Player, Model, Model>) (actor, pose) ->
            {
                attempts.incrementAndGet();
                throw new NoSuchMethodError();
            })));
        assertSame(vanilla, compat.model(player));
        assertSame(vanilla, compat.model(player));
        assertEquals(1, attempts.get());
        assertTrue(compat.model(npc) instanceof OwnedModel);

        compat.accept(state(Map.of("npcModel", npcModel,
            "playerModel", (BiFunction<Player, Model, Model>) (actor, pose) -> vanilla)));
        assertTrue(compat.model(player) instanceof OwnedModel);
    }

    private static RuneLiteObjectController controller(Model model, int x)
    {
        RuneLiteObjectController controller = new RuneLiteObjectController()
        {
            @Override public Model getModel() { return model; }
        };
        controller.setX(x);
        controller.setY(256);
        controller.setWorldView(0);
        return controller;
    }

    private static GameObject placement(int x)
    {
        return proxy(GameObject.class, method("getX", x), method("getY", 256),
            method("getRenderable", proxy(Renderable.class)));
    }
}
