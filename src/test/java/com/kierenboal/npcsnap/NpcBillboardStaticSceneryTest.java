package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.hooks.DrawCallbacks;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

/** Drives the GPU static scenery swap end to end on one synthetic static object. */
public class NpcBillboardStaticSceneryTest
{
	private static final int SCENE_X = 4;
	private static final int SCENE_Y = 8;
	private static final ObjectComposition OAK = proxy(ObjectComposition.class, method("getName", "Oak tree"),
		method("getActions", new String[] {"Chop down"}));
	private static final ObjectComposition ROCKS = proxy(ObjectComposition.class, method("getName", "Rocks"),
		method("getActions", new String[] {"Mine"}));
	private static final ObjectComposition CRATE = proxy(ObjectComposition.class, method("getName", "Crate"),
		method("getActions", new String[] {"Search"}));

	@Test
	public void staticModelIsSwappedForItsSpriteAfterTheZoneReuploads()
	{
		Fixture fixture = new Fixture(CRATE, false, true, true);

		// A new scene uploads before any sprite exists, so the model must draw.
		fixture.overlay.prepareStaticScenery(true, fixture.view);
		assertEquals(Boolean.TRUE, fixture.overlay.drawStaticScenery(fixture.scene, fixture.object));

		boolean drewBeforeSwap = false;
		for (int cycle = 0; cycle < 4 && fixture.rebuilds.isEmpty(); cycle++)
		{
			drewBeforeSwap |= fixture.frame(cycle);
		}
		int offset = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
		assertEquals("The object's zone must be asked to re-upload once its sprite is ready",
			List.of(new Point((SCENE_X + offset) / 8, (SCENE_Y + offset) / 8)), fixture.rebuilds);
		assertFalse("The sprite must not draw over the model that is still uploaded", drewBeforeSwap);

		// The GPU plugin now re-uploads the zone and asks whether to keep the model.
		assertEquals(Boolean.FALSE, fixture.overlay.drawStaticScenery(fixture.scene, fixture.object));
		assertTrue("The sprite must draw once the model is gone", fixture.frame(10));
		assertTrue("The sprite must keep drawing on later frames", fixture.frame(11));
		assertEquals("A settled swap must not keep re-uploading the zone", 1, fixture.rebuilds.size());
	}

	@Test
	public void turningTheSettingOffPutsTheModelBack()
	{
		Fixture fixture = new Fixture(OAK, true, false, false);
		assertTrue(fixture.swaps());
		fixture.rebuilds.clear();

		fixture.trees = false;
		assertFalse("No sprite may draw once trees are switched off", fixture.frame(20));
		// The request repeats each frame until the renderer re-uploads; it is idempotent there.
		int offset = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
		assertFalse("The zone must re-upload so the model comes back", fixture.rebuilds.isEmpty());
		assertEquals(Set.of(new Point((SCENE_X + offset) / 8, (SCENE_Y + offset) / 8)), new HashSet<>(fixture.rebuilds));
		assertEquals(Boolean.TRUE, fixture.overlay.drawStaticScenery(fixture.scene, fixture.object));
		fixture.rebuilds.clear();
		assertFalse(fixture.frame(21));
		assertTrue("A restored model must not keep re-uploading", fixture.rebuilds.isEmpty());
	}

	@Test
	public void applyToTreesConvertsTreesWithoutWorldObjects()
	{
		assertTrue(new Fixture(OAK, true, false, false).swaps());
		assertFalse("A crate is not a tree", new Fixture(CRATE, true, false, false).swaps());
	}

	@Test
	public void worldObjectsConvertOnlyTheirTickedCategories()
	{
		assertTrue("Rocks convert once their box is ticked", new Fixture(ROCKS, false, true, false).swaps());
		assertFalse("A crate is not a rock", new Fixture(CRATE, false, true, false).swaps());
		assertTrue(new Fixture(CRATE, false, true, true).swaps());
		assertFalse("Trees have their own setting", new Fixture(OAK, false, true, true).swaps());
		assertFalse("Nothing converts with both settings off", new Fixture(ROCKS, false, false, true).swaps());
	}

	private static final class Fixture
	{
		private final AtomicInteger cycle = new AtomicInteger();
		private final List<Point> rebuilds = new ArrayList<>();
		private final WorldView view;
		private final Scene scene;
		private final GameObject object;
		private final NpcBillboardOverlay overlay;
		private boolean trees;

		private Fixture(ObjectComposition definition, boolean trees, boolean objects, boolean everythingElse)
		{
			this.trees = trees;
			LocalPoint location = new LocalPoint(SCENE_X * 128 + 64, SCENE_Y * 128 + 64);
			// Static scenery is already a Model. In the live client getModel() on it gives
			// nothing back, so the proxy's default null is the faithful stand-in.
			Model model = proxy(Model.class,
				method("getVerticesCount", 3), method("getModelHeight", 100),
				method("getVerticesX", new float[] {-50, 50, 0}),
				method("getVerticesY", new float[] {0, 0, -100}), method("getVerticesZ", new float[3]),
				method("getFaceCount", 1), method("getFaceIndices1", new int[] {0}),
				method("getFaceIndices2", new int[] {1}), method("getFaceIndices3", new int[] {2}),
				method("getFaceColors1", new int[] {5000}), method("getFaceColors2", new int[] {5000}),
				method("getFaceColors3", new int[] {5000}));

			WorldView[] viewHolder = new WorldView[1];
			object = proxy(GameObject.class, methodSupplier("getWorldView", () -> viewHolder[0]),
				method("getId", 1), method("getLocalLocation", location),
				method("getSceneMinLocation", new Point(SCENE_X, SCENE_Y)),
				method("getSceneMaxLocation", new Point(SCENE_X, SCENE_Y)),
				method("getRenderable", model));
			Tile[][][] tiles = new Tile[4][16][16];
			tiles[0][SCENE_X][SCENE_Y] = proxy(Tile.class, method("getSceneLocation", new Point(SCENE_X, SCENE_Y)),
				method("getGameObjects", new GameObject[] {object}));
			scene = proxy(Scene.class, method("getTiles", tiles), method("getWorldViewId", WorldView.TOPLEVEL));
			view = proxy(WorldView.class, method("getId", WorldView.TOPLEVEL), method("isTopLevel", true),
				method("contains", true), method("getSizeX", 16), method("getSizeY", 16),
				method("getScene", scene),
				method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]));
			viewHolder[0] = view;

			DrawCallbacks renderer = new DrawCallbacks()
			{
				@Override public void draw(int overlayColor) {}
				@Override public void swapScene(Scene swapped) {}
				@Override public void invalidateZone(Scene owner, int x, int z)
				{
					assertSame(scene, owner);
					rebuilds.add(new Point(x, z));
				}
			};
			Player localPlayer = proxy(Player.class, method("getWorldView", view), method("getLocalLocation", location));
			Client client = proxy(Client.class, method("getTopLevelWorldView", view), method("getWorldView", view),
				method("getLocalPlayer", localPlayer), method("getGameState", GameState.LOGGED_IN),
				method("isClientThread", true), method("getScale", 512), method("get3dZoom", 512),
				method("getViewportWidth", 800), method("getViewportHeight", 600),
				method("getDrawCallbacks", renderer), method("getObjectDefinition", definition),
				methodSupplier("getGameCycle", cycle::get));
			NpcSnapConfig config = new NpcSnapConfig()
			{
				@Override public boolean applyToTrees() { return Fixture.this.trees; }
				@Override public boolean applyToObjects() { return objects; }
				@Override public boolean worldObjectRocks() { return true; }
				@Override public boolean worldObjectOther() { return everythingElse; }
				@Override public boolean applyToNpcs() { return false; }
				@Override public boolean applyToPlayers() { return false; }
				@Override public boolean applyToGroundItems() { return false; }
				@Override public boolean applyToProjectiles() { return false; }
				@Override public boolean applyToGraphicsObjects() { return false; }
				@Override public boolean useRetroOverheads() { return false; }
				@Override public boolean enableRotationSnapping() { return false; }
				@Override public BillboardOcclusionQuality billboardOcclusionQuality() { return BillboardOcclusionQuality.OFF; }
			};
			overlay = new NpcBillboardOverlay(client, null, config, new NpcSnapDebug(client, config), null);
			overlay.setActive(true);
		}

		/** Runs frames until the sprite replaces the model, or gives up. */
		private boolean swaps()
		{
			overlay.prepareStaticScenery(true, view);
			for (int gameCycle = 0; gameCycle < 4 && rebuilds.isEmpty(); gameCycle++)
			{
				frame(gameCycle);
			}
			return !rebuilds.isEmpty() && Boolean.FALSE.equals(overlay.drawStaticScenery(scene, object)) && frame(10);
		}

		/** Runs one frame the way the plugin does and reports whether anything was painted. */
		private boolean frame(int gameCycle)
		{
			cycle.set(gameCycle);
			overlay.beginFrame();
			overlay.prepareStaticScenery(true, view);
			overlay.prepareFrame(view);
			BufferedImage canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = canvas.createGraphics();
			try
			{
				overlay.render(graphics);
			}
			finally
			{
				graphics.dispose();
			}
			return Arrays.stream(canvas.getRGB(0, 0, 800, 600, null, 0, 800)).anyMatch(pixel -> pixel != 0);
		}
	}
}
