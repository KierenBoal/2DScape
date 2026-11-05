package com.kierenboal.npcsnap.compatibility;

import com.kierenboal.npcsnap.rendering.OwnedModel;
import java.util.*;
import java.util.function.Function;
import net.runelite.api.*;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class RetroModelCompatibilityTest
{
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
        RetroModelCompatibility compat = new RetroModelCompatibility();
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
        RetroModelCompatibility compat = new RetroModelCompatibility();
        assertSame(original, compat.model(npc));
        compat.accept(state(Map.of("npcModel", (Function<NPC, Model>) actor -> null)));
        assertSame(original, compat.model(npc));
        compat.accept(state(Map.of("npcModel", (Function<NPC, Model>) actor -> { throw new IllegalStateException(); })));
        assertSame(original, compat.model(npc)); assertSame(original, compat.model(player));
        compat.clear(); assertSame(original, compat.model(npc));
    }

    @Test public void unrelatedOrIncompatibleMessagesCannotReplaceProvider()
    {
        RetroModelCompatibility compat = new RetroModelCompatibility();
        assertFalse(compat.accept(new PluginMessage("other", "models", Map.of())));
        assertFalse(compat.accept(new PluginMessage("npc-model-swap", "models",
            Map.of("version", 2, "source", "retro-npc-swapper"))));
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
        RetroModelCompatibility compat = new RetroModelCompatibility();
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
        RetroModelCompatibility compat = new RetroModelCompatibility();
        compat.accept(state(Map.of("sceneryStandIns", (Function<TileObject, List<RuneLiteObjectController>>) object -> List.of(controller),
            "sceneryModel", (Function<RuneLiteObjectController, Model>) item -> carrier)));
        compat.observed(original);
        Scene scene = proxy(Scene.class, method("getWorldViewId", 0));
        Renderable renderable = proxy(Renderable.class, method("getModel", carrier));
        GameObject placement = proxy(GameObject.class, method("getX", 128), method("getY", 256), method("getRenderable", renderable));
        assertFalse(compat.hidden(scene, placement, carrier));
        compat.hide = object -> true;
        assertTrue(compat.hidden(scene, placement, carrier));
        GameObject other = proxy(GameObject.class, method("getX", 129), method("getY", 256), method("getRenderable", renderable));
        assertFalse(compat.hidden(scene, other, carrier));
        GameObject actor = proxy(GameObject.class, method("getX", 128), method("getY", 256), method("getRenderable", proxy(NPC.class)));
        assertFalse(compat.hidden(scene, actor, carrier));
        compat.untrack(original);
        assertFalse(compat.hidden(scene, placement, carrier));
    }

}
