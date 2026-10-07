package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;

public final class BillboardProjectileGeometry
{
	private BillboardProjectileGeometry()
	{
	}

	public static LocalPoint localPoint(Projectile projectile)
	{
		return new LocalPoint((int) projectile.getX(), (int) projectile.getY());
	}

	public static int verticalOffset(Client client, Projectile projectile)
	{
		LocalPoint localPoint = localPoint(projectile);
		int tileHeight = Perspective.getTileHeight(client, localPoint, projectile.getFloor());
		// getZ() is the current world coordinate, including the client's arc and
		// launch/target heights. Convert it only for APIs expecting a tile offset.
		return (int) Math.round(BillboardDepthCalculator.worldHeight(tileHeight, projectile.getZ()));
	}

	public static Rectangle drawBounds(
		BillboardDepthCalculator depthCalculator, Projectile projectile, Rectangle imageBounds, double canvasScale)
	{
		// The cached sprite's bounds are relative to model-local zero. Adding half
		// the live model height moves that origin as the projectile animates/pitches.
		// Project the native flight position directly, retaining fractional XYZ and
		// avoiding terrain-relative rounding or any model-height-based adjustment.
		BillboardCanvasPoint origin = depthCalculator.projectCanvasPoint(
			projectile.getX(), projectile.getY(), projectile.getZ());
		return BillboardGeometryUtils.projectedDrawBounds(imageBounds, origin, canvasScale);
	}
}
