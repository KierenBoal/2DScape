package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import com.kierenboal.npcsnap.rendering.AnimationFrameSnapper;
import com.kierenboal.npcsnap.targeting.BillboardWorldViewVisibility;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

/** Runs the real sprite pipeline on a synthetic boat without a game client. */
public class NpcBillboardBoatVisibilityTest
{
	@Test
	public void cachedNpcSpritesFollowParentVisibilityWithoutWaitingForAGameCycle()
	{
		checkCachedSprite(false);
	}

	@Test
	public void cachedPlayerSpritesFollowParentVisibilityWithoutWaitingForAGameCycle()
	{
		checkCachedSprite(true);
	}

	private void checkCachedSprite(boolean playerCrew)
	{
		Fixture fixture = new Fixture(playerCrew);
		fixture.overlay.setActive(true);
		fixture.overlay.beginFrame();
		assertTrue(fixture.drawnPixels() > 0);
		assertTrue(fixture.overlay.isActorWorldViewVisible(fixture.actor));
		fixture.overlap.set(true); // Change after the sprite and selection were cached.
		assertEquals(0, fixture.drawnPixels());
		assertFalse(fixture.overlay.isActorWorldViewVisible(fixture.actor));
		fixture.overlap.set(false);
		assertTrue(fixture.drawnPixels() > 0);
		fixture.hideBoat.set(true);
		fixture.overlay.beginFrame(); // Same game cycle; callback cache is per render frame.
		assertEquals(0, fixture.drawnPixels());
		fixture.hideBoat.set(false);
		fixture.overlay.beginFrame();
		assertTrue(fixture.drawnPixels() > 0);
	}

	private static final class Fixture
	{
		private final AtomicBoolean overlap = new AtomicBoolean();
		private final AtomicBoolean hideBoat = new AtomicBoolean();
		private final Actor actor;
		private final WorldView topLevel;
		private final NpcBillboardOverlay overlay;

		private Fixture(boolean playerCrew)
		{
			Model model = proxy(Model.class, method("getVerticesCount", 4), method("getModelHeight", 100),
				method("getVerticesX", new float[] {-50, 50, 50, -50}),
				method("getVerticesY", new float[] {-100, -100, 0, 0}), method("getVerticesZ", new float[4]),
				method("getFaceCount", 2), method("getFaceIndices1", new int[] {0, 0}),
				method("getFaceIndices2", new int[] {1, 2}), method("getFaceIndices3", new int[] {2, 3}),
				method("getFaceColors1", new int[] {5000, 5000}), method("getFaceColors2", new int[] {5000, 5000}),
				method("getFaceColors3", new int[] {5000, 5000}));
			// Lists are populated after constructing the view to avoid circular proxies.
			java.util.List<NPC> npcs = new java.util.ArrayList<>();
			java.util.List<Player> players = new java.util.ArrayList<>();
			WorldView boat = proxy(WorldView.class, method("getId", 7),
				method("getScene", proxy(Scene.class, method("getWorldViewId", 7))),
				method("npcs", proxy(IndexedObjectSet.class, methodSupplier("iterator", npcs::iterator))),
				method("players", proxy(IndexedObjectSet.class, methodSupplier("iterator", players::iterator))));
			TestProxies.MethodResult[] actorMethods = {
				method("getWorldView", boat), method("getLocalLocation", new LocalPoint(512, 1024)),
				method("getModel", model), method("getModelHeight", 100), method("getAnimation", -1),
				method("getPoseAnimation", -1), method("getCanvasTilePoly", new Polygon(new int[] {500, 510, 510},
					new int[] {250, 250, 260}, 3))
			};
			actor = playerCrew ? proxy(Player.class, actorMethods) : proxy(NPC.class, actorMethods);
			if (playerCrew) { players.add((Player) actor); }
			else { npcs.add((NPC) actor); }
			WorldEntity parent = proxy(WorldEntity.class, method("getWorldView", boat),
				methodSupplier("isHiddenForOverlap", overlap::get),
				method("transformToMainWorld", new LocalPoint(512, 1024)));
			topLevel = proxy(WorldView.class, method("getId", WorldView.TOPLEVEL), method("isTopLevel", true),
				method("contains", true), method("getSizeX", 16), method("getSizeY", 16),
				method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]),
				method("worldViews", proxy(IndexedObjectSet.class,
					methodSupplier("iterator", () -> Collections.singletonList(boat).iterator()))),
				method("worldEntities", proxy(IndexedObjectSet.class,
					methodSupplier("iterator", () -> Collections.singletonList(parent).iterator()))));
			Player localPlayer = proxy(Player.class, method("getWorldView", topLevel),
				method("getLocalLocation", new LocalPoint(512, 1024)));
			Client client = proxy(Client.class, method("getTopLevelWorldView", topLevel), method("getWorldView", topLevel),
				method("getLocalPlayer", localPlayer), method("getGameState", GameState.LOGGED_IN),
				method("isClientThread", true), method("getScale", 512), method("get3dZoom", 512),
				method("getViewportWidth", 800), method("getViewportHeight", 600));
			RenderCallbackManager callbacks = new RenderCallbackManager();
			callbacks.register(new RenderCallback()
			{
				@Override public boolean addEntity(Renderable renderable, boolean drawingUi) { return !hideBoat.get(); }
			});
			NpcSnapConfig config = new NpcSnapConfig()
			{
				@Override public boolean applyToProjectiles() { return false; }
				@Override public boolean applyToGraphicsObjects() { return false; }
				@Override public boolean applyToGroundItems() { return false; }
				@Override public boolean useRetroOverheads() { return false; }
				@Override public BillboardOcclusionQuality billboardOcclusionQuality() { return BillboardOcclusionQuality.OFF; }
			};
			overlay = new NpcBillboardOverlay(client, null, config, new NpcSnapDebug(client, config),
				new AnimationFrameSnapper(client), null, new BillboardWorldViewVisibility(client, callbacks));
		}

		private int drawnPixels()
		{
			overlay.prepareFrame(topLevel);
			BufferedImage canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = canvas.createGraphics();
			try { overlay.render(graphics); }
			finally { graphics.dispose(); }
			int count = 0;
			for (int pixel : canvas.getRGB(0, 0, 800, 600, null, 0, 800))
			{
				if ((pixel >>> 24) != 0) { count++; }
			}
			return count;
		}
	}
}
