package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BillboardNpcSurfaceOcclusionTest
{
	private final Tile[][][] tiles = new Tile[4][16][16];
	private final int[][][] heights = new int[4][17][17];
	private final Scene scene = proxy(Scene.class, method("getTiles", tiles));
	private final WorldView view = proxy(WorldView.class, method("isTopLevel", true), method("contains", true),
		method("getId", WorldView.TOPLEVEL), method("getScene", scene), method("getSizeX", 16), method("getSizeY", 16),
		method("getTileSettings", new byte[4][16][16]), method("getTileHeights", heights));
	private final Client client = client(0);
	private final LocalPoint location = new LocalPoint(512, 1024, view);

	@Test
	public void waterlineUsesTileHeightInsteadOfNpcHeightOrHalfTheSprite()
	{
		addSurface();
		assertEquals(300, cutoff(client, request(80, -100)));
		assertEquals(300, cutoff(client, request(20, -10)));
		assertEquals(300, cutoff(client, request(80, 40)));
		for (int[] row : heights[0])
		{
			Arrays.fill(row, 100);
		}
		assertEquals(350, cutoff(client, request(80, -100)));
	}

	@Test
	public void groundedAndRaisedNpcsAreNotBlindlyCropped()
	{
		addSurface();
		assertEquals(Integer.MAX_VALUE, cutoff(client, request(0, 0)));
		assertEquals(Integer.MAX_VALUE, cutoff(client, request(20, 30)));
		assertEquals(Integer.MAX_VALUE, cutoff(client, request(20, 20)));
	}

	@Test
	public void waterlineReprojectsWithCameraPitchWithoutChangingActorHeight()
	{
		addSurface();
		assertTrue(cutoff(client(1024), request(20, -10)) < cutoff(client, request(20, -10)));
		assertEquals(cutoff(client(1024), request(20, -10)), cutoff(client(1024), request(80, -100)));
	}

	@Test
	public void absentSurfaceSceneOrNpcIsNotGuessed()
	{
		assertEquals(Integer.MAX_VALUE, cutoff(client, request(20, -10)));
		assertEquals(Integer.MAX_VALUE, cutoff(client, null));
		addSurface();
		BillboardRenderRequest player = new BillboardRenderRequest(proxy(Player.class), proxy(Model.class), location,
			0, -10, 0, 0, -1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
		assertEquals(Integer.MAX_VALUE, cutoff(client, player));
		tiles[0][4][8] = proxy(Tile.class); // Empty scene tile is not an occluding surface.
		assertEquals(Integer.MAX_VALUE, cutoff(client, request(20, -10)));
	}

	@Test
	public void nestedBoatDeckActorsAreNotClippedAgainstTheMainWorldSea()
	{
		addSurface();
		WorldView nested = proxy(WorldView.class, method("getId", 1), method("contains", true), method("getScene", scene));
		NPC npc = proxy(NPC.class, method("getWorldView", nested));
		BillboardRenderRequest request = new BillboardRenderRequest(npc, proxy(Model.class, method("getBottomY", 20)), location,
			0, -10, 0, 0, -1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
		assertEquals(Integer.MAX_VALUE, cutoff(client, request));
	}

	private int cutoff(Client client, BillboardRenderRequest request)
	{
		return BillboardNpcSurfaceOcclusion.firstOccludedRow(client, new BillboardDepthCalculator(client), request);
	}

	private BillboardRenderRequest request(int bottomY, int verticalOffset)
	{
		NPC npc = proxy(NPC.class, method("getWorldView", view));
		Model model = proxy(Model.class, method("getBottomY", bottomY));
		return new BillboardRenderRequest(npc, model, location,
			0, verticalOffset, 0, 0, -1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
	}

	private Client client(int pitch)
	{
		return proxy(Client.class, method("getTopLevelWorldView", view), method("getWorldView", view),
			method("getCameraPitch", pitch), method("getScale", 512),
			method("getViewportWidth", 800), method("getViewportHeight", 600));
	}

	private void addSurface()
	{
		tiles[0][4][8] = proxy(Tile.class, method("getSceneTilePaint", proxy(SceneTilePaint.class)));
	}
}
