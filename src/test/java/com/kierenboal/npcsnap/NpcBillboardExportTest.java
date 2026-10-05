package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.export.BillboardExportBatch;
import com.kierenboal.npcsnap.export.BillboardObjectExportTarget;
import com.kierenboal.npcsnap.rendering.AnimationFrameSnapper;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class NpcBillboardExportTest
{
	@Test
	public void exportsFreshMultiTileModelsEvenWhenWorldObjectBillboardsAreDisabled()
	{
		AtomicInteger faceCount = new AtomicInteger(1);
		Model model = proxy(Model.class, method("getVerticesCount", 3), method("getModelHeight", 100),
			method("getVerticesX", new float[] { -50, 50, 0 }), method("getVerticesY", new float[] { 0, 0, -100 }),
			method("getVerticesZ", new float[3]), methodSupplier("getFaceCount", faceCount::get),
			method("getFaceIndices1", new int[] { 0 }), method("getFaceIndices2", new int[] { 1 }),
			method("getFaceIndices3", new int[] { 2 }), method("getFaceColors1", new int[] { 5000 }),
			method("getFaceColors2", new int[] { 5000 }), method("getFaceColors3", new int[] { 5000 }));
		Tile[][][] tiles = new Tile[4][10][10];
		Scene scene = proxy(Scene.class, method("getTiles", tiles));
		WorldView view = proxy(WorldView.class, method("getId", WorldView.TOPLEVEL), method("getScene", scene),
			method("getSizeX", 10), method("getSizeY", 10), method("getTileSettings", new byte[4][10][10]),
			method("getTileHeights", new int[4][11][11]));
		GameObject object = proxy(GameObject.class, method("getId", 1234), method("getWorldView", view),
			method("getRenderable", proxy(Renderable.class, method("getModel", model))),
			method("getLocalLocation", LocalPoint.fromScene(4, 4, view)),
			method("getSceneMinLocation", new Point(2, 3)), method("getSceneMaxLocation", new Point(5, 6)));
		tiles[0][2][3] = proxy(Tile.class, method("getGameObjects", new GameObject[] { object }));
		Client client = proxy(Client.class, method("getWorldView", view),
			method("getObjectDefinition", proxy(ObjectComposition.class, method("getName", "Test tree"))));
		NpcSnapConfig config = new NpcSnapConfig() { };
		assertFalse(config.applyToObjects());
		NpcBillboardOverlay overlay = new NpcBillboardOverlay(client, null, config,
			new NpcSnapDebug(client, config), new AnimationFrameSnapper(client), null);
		BillboardObjectExportTarget target = new BillboardObjectExportTarget(1234, 2, 3, WorldView.TOPLEVEL, 0);
		// No beginFrame, scene observation, classification or billboard cache was populated.
		BillboardExportBatch batch = overlay.captureExport(target);
		assertNotNull(batch);
		assertEquals("Test_tree", batch.name);
		assertFalse(batch.frames.isEmpty());
		assertTrue(batch.frames.get(0).image.getWidth() > 0);
		faceCount.set(0);
		assertNull("An unavailable raster must not reuse the prior export", overlay.captureExport(target));
		tiles[0][2][3] = null;
		assertNull(overlay.captureExport(target));
	}
}
