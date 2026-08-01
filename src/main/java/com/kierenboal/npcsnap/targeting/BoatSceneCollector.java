package com.kierenboal.npcsnap.targeting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

public final class BoatSceneCollector
{
	private BoatSceneCollector()
	{
	}

	public static ObservedTileObject collect(WorldEntity entity)
	{
		WorldView view = entity != null ? entity.getWorldView() : null;
		Scene scene = view != null ? view.getScene() : null;
		if (scene == null || scene.getTiles() == null)
		{
			return null;
		}

		List<ObjectRenderablePart> parts = new ArrayList<>();
		Set<TileObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		TileObject identityObject = null;
		Tile[][][] tiles = scene.getExtendedTiles() != null ? scene.getExtendedTiles() : scene.getTiles();
		for (Tile[][] plane : tiles)
		{
			if (plane == null)
			{
				continue;
			}
			for (Tile[] column : plane)
			{
				if (column == null)
				{
					continue;
				}
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					for (Tile linked = tile; linked != null; linked = linked.getBridge())
					{
						identityObject = add(entity, linked.getWallObject(), seen, parts, identityObject);
						identityObject = add(entity, linked.getDecorativeObject(), seen, parts, identityObject);
						identityObject = add(entity, linked.getGroundObject(), seen, parts, identityObject);
						GameObject[] gameObjects = linked.getGameObjects();
						if (gameObjects == null)
						{
							continue;
						}
						for (GameObject gameObject : gameObjects)
						{
							identityObject = add(entity, gameObject, seen, parts, identityObject);
						}
					}
				}
			}
		}
		return parts.isEmpty() ? null : new ObservedTileObject(identityObject, parts, ClassifiedObjectType.BOAT);
	}

	public static List<BoatTileFace> collectTileFaces(WorldEntity entity, LocalPoint origin)
	{
		WorldView view = entity != null ? entity.getWorldView() : null;
		Scene scene = view != null ? view.getScene() : null;
		if (scene == null || scene.getTiles() == null || entity.getLocalLocation() == null)
		{
			return Collections.emptyList();
		}
		List<BoatTileFace> faces = new ArrayList<>();
		Set<Tile> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Tile[][][] tiles = scene.getExtendedTiles() != null ? scene.getExtendedTiles() : scene.getTiles();
		for (Tile[][] plane : tiles)
		{
			if (plane == null)
			{
				continue;
			}
			for (Tile[] column : plane)
			{
				if (column == null)
				{
					continue;
				}
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					for (Tile linked = tile; linked != null && seen.add(linked); linked = linked.getBridge())
					{
						SceneTileModel model = linked.getSceneTileModel();
						if (model != null)
						{
							addTileModelFaces(origin, view, model, faces);
						}
						else
						{
							SceneTilePaint paint = linked.getSceneTilePaint();
							if (paint != null)
							{
								addTilePaintFaces(origin, view, linked, paint, faces);
							}
						}
					}
				}
			}
		}
		return faces;
	}

	private static void addTileModelFaces(LocalPoint origin, WorldView view, SceneTileModel model, List<BoatTileFace> output)
	{
		int[] vx = model.getVertexX();
		int[] vy = model.getVertexY();
		int[] vz = model.getVertexZ();
		int[] a = model.getFaceX();
		int[] b = model.getFaceY();
		int[] c = model.getFaceZ();
		int[] ca = model.getTriangleColorA();
		int[] cb = model.getTriangleColorB();
		int[] cc = model.getTriangleColorC();
		if (vx == null || vy == null || vz == null || a == null || b == null || c == null)
		{
			return;
		}
		for (int i = 0; i < Math.min(a.length, Math.min(b.length, c.length)); i++)
		{
			if (a[i] < 0 || b[i] < 0 || c[i] < 0 || a[i] >= vx.length || b[i] >= vx.length || c[i] >= vx.length)
			{
				continue;
			}
			addFace(origin, view,
				vx[a[i]], vy[a[i]], vz[a[i]], vx[b[i]], vy[b[i]], vz[b[i]], vx[c[i]], vy[c[i]], vz[c[i]],
				color(ca, i), color(cb, i), color(cc, i), output);
		}
	}

	private static void addTilePaintFaces(LocalPoint origin, WorldView view, Tile tile, SceneTilePaint paint,
		List<BoatTileFace> output)
	{
		LocalPoint center = tile.getLocalLocation();
		if (center == null)
		{
			return;
		}
		int west = center.getX() - 64;
		int east = center.getX() + 64;
		int south = center.getY() - 64;
		int north = center.getY() + 64;
		int plane = tile.getPlane();
		int swY = view.getTileHeight(west, south, plane);
		int seY = view.getTileHeight(east, south, plane);
		int nwY = view.getTileHeight(west, north, plane);
		int neY = view.getTileHeight(east, north, plane);
		addFace(origin, view, west, swY, south, east, seY, south, east, neY, north,
			paint.getSwColor(), paint.getSeColor(), paint.getNeColor(), output);
		addFace(origin, view, west, swY, south, east, neY, north, west, nwY, north,
			paint.getSwColor(), paint.getNeColor(), paint.getNwColor(), output);
	}

	private static void addFace(LocalPoint origin, WorldView view,
		int x0, int y0, int z0, int x1, int y1, int z1, int x2, int y2, int z2,
		int colorA, int colorB, int colorC, List<BoatTileFace> output)
	{
		if (origin == null)
		{
			return;
		}
		output.add(new BoatTileFace(
			x0 - origin.getX(), y0, z0 - origin.getY(),
			x1 - origin.getX(), y1, z1 - origin.getY(),
			x2 - origin.getX(), y2, z2 - origin.getY(), colorA, colorB, colorC));
	}

	public static LocalPoint localOrigin(ObservedTileObject boat)
	{
		if (boat == null || boat.parts == null || boat.parts.isEmpty())
		{
			return new LocalPoint(0, 0);
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int worldViewId = -1;
		for (ObjectRenderablePart part : boat.parts)
		{
			if (part == null || part.localPoint == null)
			{
				continue;
			}
			minX = Math.min(minX, part.localPoint.getX());
			minY = Math.min(minY, part.localPoint.getY());
			maxX = Math.max(maxX, part.localPoint.getX());
			maxY = Math.max(maxY, part.localPoint.getY());
			worldViewId = part.localPoint.getWorldView();
		}
		return minX == Integer.MAX_VALUE
			? new LocalPoint(0, 0)
			: new LocalPoint(minX + ((maxX - minX) / 2), minY + ((maxY - minY) / 2), worldViewId);
	}

	private static int color(int[] colors, int index)
	{
		return colors != null && index < colors.length ? colors[index] : 0;
	}

	private static TileObject add(WorldEntity entity, TileObject object, Set<TileObject> seen,
		List<ObjectRenderablePart> output, TileObject identityObject)
	{
		if (object == null || !seen.add(object))
		{
			return identityObject;
		}
		ObservedTileObject observed = ObservedTileObjectBuilder.build(object);
		if (observed == null)
		{
			return identityObject;
		}
		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.renderable == null || part.renderable.getModel() == null)
			{
				continue;
			}
			output.add(new ObjectRenderablePart(part.renderable, part.localPoint, part.plane));
		}
		return identityObject != null ? identityObject : object;
	}
}
