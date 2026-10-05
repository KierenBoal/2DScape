package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardCanvasPoint;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Clips submerged NPC pixels against their tile surface without changing sprite placement. */
public final class BillboardNpcSurfaceOcclusion
{
	private BillboardNpcSurfaceOcclusion()
	{
	}

	public static int firstOccludedRow(Client client, BillboardDepthCalculator depthCalculator, BillboardRenderRequest request)
	{
		if (request == null || !(request.renderable instanceof NPC) || request.model == null || request.localPoint == null)
		{
			return Integer.MAX_VALUE;
		}
		NPC npc = (NPC) request.renderable;
		WorldView view = npc.getWorldView();
		LocalPoint location = request.localPoint;
		// Nested actors stand on their own deck, not the top-level sea surface.
		if (view == null || !view.isTopLevel() || view != client.getTopLevelWorldView()
			|| view.getScene() == null || !view.contains(location))
		{
			return Integer.MAX_VALUE;
		}
		Model model = request.model;
		// Actor models normally already have cylinder bounds. This is cached by
		// the model and avoids another per-frame scan of its vertices.
		model.calculateBoundsCylinder();
		// Model Y and world height increase downwards, while verticalOffset is
		// positive upwards. Ordinary NPCs with feet at Y=0 do not need clipping.
		if (model.getBottomY() <= request.verticalOffset)
		{
			return Integer.MAX_VALUE;
		}

		Tile[][][] tiles = view.getScene().getTiles();
		int plane = request.plane;
		int x = location.getSceneX();
		int y = location.getSceneY();
		if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null
			|| x < 0 || x >= tiles[plane].length || tiles[plane][x] == null
			|| y < 0 || y >= tiles[plane][x].length)
		{
			return Integer.MAX_VALUE;
		}
		Tile tile = tiles[plane][x][y];
		if (tile == null || (tile.getSceneTilePaint() == null && tile.getSceneTileModel() == null))
		{
			return Integer.MAX_VALUE;
		}
		// The surface stays at the tile height, independently of animation depth
		// and of the cached sprite's height, pitch, padding or position.
		BillboardCanvasPoint surface = depthCalculator.projectCanvasPoint(location, plane, 0);
		if (surface == null || !Double.isFinite(surface.y))
		{
			return Integer.MAX_VALUE;
		}
		return (int) Math.floor(surface.y + 0.5d);
	}
}
