package com.kierenboal.npcsnap.targeting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class BillboardWorldViewVisibilityTest
{
	private final List<WorldEntity> boats = new ArrayList<>();
	private final WorldView topLevel = proxy(WorldView.class, method("isTopLevel", true),
		method("worldEntities", proxy(IndexedObjectSet.class, methodSupplier("iterator", boats::iterator))));
	private final RenderCallbackManager callbacks = new RenderCallbackManager();
	private final BillboardWorldViewVisibility visibility = new BillboardWorldViewVisibility(
		proxy(Client.class, method("getTopLevelWorldView", topLevel)), callbacks);

	@Test
	public void overlapChangesHideBothKindsOfCrewImmediately()
	{
		AtomicBoolean overlap = new AtomicBoolean();
		WorldView boat = boat(7, overlap, WorldEntity.OWNER_TYPE_OTHER_PLAYER);
		NPC npc = proxy(NPC.class, method("getWorldView", boat));
		Player player = proxy(Player.class, method("getWorldView", boat));
		visibility.beginFrame();
		assertTrue(visibility.isVisible(npc));
		assertTrue(visibility.isVisible(player));
		overlap.set(true); // Client can set this after BeforeRender.
		assertFalse(visibility.isVisible(npc));
		assertFalse(visibility.isVisible(player));
		overlap.set(false);
		assertTrue(visibility.isVisible(npc));
	}

	@Test
	public void boatCallbacksPreserveTheHidersOwnerExceptionsAndCacheOncePerFrame()
	{
		WorldView other = boat(1, new AtomicBoolean(), WorldEntity.OWNER_TYPE_OTHER_PLAYER);
		WorldView own = boat(2, new AtomicBoolean(), WorldEntity.OWNER_TYPE_SELF_PLAYER);
		WorldView npcBoat = boat(3, new AtomicBoolean(), WorldEntity.OWNER_TYPE_NOT_PLAYER);
		AtomicBoolean hideBoats = new AtomicBoolean(true);
		AtomicInteger checks = new AtomicInteger();
		callbacks.register(new RenderCallback()
		{
			@Override public boolean addEntity(Renderable renderable, boolean drawingUi)
			{
				assertFalse(drawingUi);
				checks.incrementAndGet();
				// Equivalent to Entity Hider's Scene check, with a synthetic registry.
				int id = ((Scene) renderable).getWorldViewId();
				return !hideBoats.get() || boats.get(id - 1).getOwnerType() != WorldEntity.OWNER_TYPE_OTHER_PLAYER;
			}
		});
		visibility.beginFrame();
		assertFalse(visibility.isVisible(proxy(NPC.class, method("getWorldView", other))));
		assertFalse(visibility.isVisible(proxy(Player.class, method("getWorldView", other))));
		assertEquals(1, checks.get());
		assertTrue(visibility.isVisible(own));
		assertTrue(visibility.isVisible(npcBoat));
		hideBoats.set(false);
		visibility.beginFrame();
		assertTrue(visibility.isVisible(other));
		assertEquals(4, checks.get());
	}

	@Test
	public void anyCallbackCanHideTheParent()
	{
		WorldView boat = boat(1, new AtomicBoolean(), WorldEntity.OWNER_TYPE_OTHER_PLAYER);
		callbacks.register(new RenderCallback() {});
		callbacks.register(new RenderCallback()
		{
			@Override public boolean addEntity(Renderable renderable, boolean drawingUi) { return false; }
		});
		visibility.beginFrame();
		assertFalse(visibility.isVisible(boat));
	}

	@Test
	public void despawnedOrReusedViewsCannotBorrowAnotherBoatsVisibility()
	{
		WorldView old = boat(7, new AtomicBoolean(), WorldEntity.OWNER_TYPE_OTHER_PLAYER);
		visibility.beginFrame();
		assertTrue(visibility.isVisible(old));
		boats.clear();
		WorldView replacement = boat(7, new AtomicBoolean(), WorldEntity.OWNER_TYPE_OTHER_PLAYER);
		visibility.beginFrame();
		assertFalse(visibility.isVisible(old));
		assertTrue(visibility.isVisible(replacement));
		visibility.clear();
		assertFalse(visibility.isVisible(replacement));
	}

	@Test
	public void topLevelActorsRemainVisibleWithoutBoatCallbacks()
	{
		callbacks.register(new RenderCallback()
		{
			@Override public boolean addEntity(Renderable renderable, boolean drawingUi)
			{
				fail("Top-level actors must not query a boat scene");
				return false;
			}
		});
		visibility.beginFrame();
		assertTrue(visibility.isVisible(proxy(NPC.class, method("getWorldView", topLevel))));
		assertTrue(visibility.isVisible(proxy(Player.class, method("getWorldView", topLevel))));
		assertFalse(visibility.isVisible((WorldView) null));
		assertFalse(visibility.isVisible(proxy(WorldView.class, method("getId", 99))));
	}

	@Test
	public void missingScenesAndOverlapHiddenBoatsDoNotQueryCallbacks()
	{
		WorldView missing = proxy(WorldView.class);
		boats.add(proxy(WorldEntity.class, method("getWorldView", missing)));
		WorldView overlap = boat(7, new AtomicBoolean(true), WorldEntity.OWNER_TYPE_OTHER_PLAYER);
		callbacks.register(new RenderCallback()
		{
			@Override public boolean addEntity(Renderable renderable, boolean drawingUi)
			{
				fail("Unavailable parents must not query callbacks");
				return true;
			}
		});
		visibility.beginFrame();
		assertFalse(visibility.isVisible(missing));
		assertFalse(visibility.isVisible(overlap));
	}

	private WorldView boat(int id, AtomicBoolean overlap, int ownerType)
	{
		Scene scene = proxy(Scene.class, method("getWorldViewId", id));
		WorldView view = proxy(WorldView.class, method("getId", id), method("getScene", scene));
		boats.add(proxy(WorldEntity.class, method("getWorldView", view), method("getOwnerType", ownerType),
			methodSupplier("isHiddenForOverlap", overlap::get)));
		return view;
	}
}
