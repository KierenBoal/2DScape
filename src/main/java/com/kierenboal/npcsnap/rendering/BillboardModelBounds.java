package com.kierenboal.npcsnap.rendering;

import net.runelite.api.AABB;
import net.runelite.api.Model;

/** Model-unit dimensions: camera movement and perspective cannot trigger a resize. */
public final class BillboardModelBounds
{
	public final double width;
	public final double height;
	private final double hullWidth;
	private final double hullHeight;
	private final double hullDepth;

	public BillboardModelBounds(double width, double height)
	{
		this(width, height, width, height, 0);
	}

	private BillboardModelBounds(double width, double height, double hullWidth, double hullHeight, double hullDepth)
	{
		this.width = width;
		this.height = height;
		this.hullWidth = hullWidth;
		this.hullHeight = hullHeight;
		this.hullDepth = hullDepth;
	}

	public boolean exceeds(BillboardModelBounds previous, int percent)
	{
		return previous != null && percent > 0
			&& (changed(width, previous.width, percent) || changed(height, previous.height, percent)
				|| changed(hullWidth, previous.hullWidth, percent) || changed(hullHeight, previous.hullHeight, percent)
				|| changed(hullDepth, previous.hullDepth, percent));
	}

	private static boolean changed(double current, double previous, int percent)
	{
		return previous > 0 && Math.abs(current - previous) / previous > Math.min(100, percent) / 100d;
	}

	public static BillboardModelBounds sample(Model model, boolean rotationInvariant)
	{
		if (model == null) return null;
		AABB box = rotationInvariant ? null : model.getAABB(0);
		float[] x = model.getVerticesX(), y = model.getVerticesY(), z = model.getVerticesZ();
		int count = x == null || y == null || z == null ? 0
			: Math.min(model.getVerticesCount(), Math.min(x.length, Math.min(y.length, z.length)));
		double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
		double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX, radiusSquared = 0;
		for (int i = 0; i < count; i++)
		{
			minX = Math.min(minX, x[i]); maxX = Math.max(maxX, x[i]);
			minY = Math.min(minY, y[i]); maxY = Math.max(maxY, y[i]);
			minZ = Math.min(minZ, z[i]); maxZ = Math.max(maxZ, z[i]);
			radiusSquared = Math.max(radiusSquared, (double) x[i] * x[i] + (double) y[i] * y[i] + (double) z[i] * z[i]);
		}
		// Projectile models may already contain their changing flight pitch.
		if (rotationInvariant && count > 0)
		{
			double diameter = 2 * Math.sqrt(radiusSquared);
			return new BillboardModelBounds(diameter, diameter);
		}
		double width = count > 0 ? Math.max(maxX - minX, maxZ - minZ) : 0;
		double height = count > 0 ? maxY - minY : 0;
		double hullWidth = width, hullHeight = height, hullDepth = count > 0 ? Math.min(maxX - minX, maxZ - minZ) : 0;
		if (box != null)
		{
			width = Math.max(width, 2d * Math.max(box.getExtremeX(), box.getExtremeZ()));
			height = Math.max(height, 2d * box.getExtremeY());
		}
		return width > 0 || height > 0 ? new BillboardModelBounds(width, height, hullWidth, hullHeight, hullDepth) : null;
	}
}
