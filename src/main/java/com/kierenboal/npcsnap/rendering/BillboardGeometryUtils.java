package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import java.util.List;
import net.runelite.api.Point;

public final class BillboardGeometryUtils
{
	private static final int MAX_SOURCE_BILLBOARD_SIZE = 4096;
	private static final int MAX_SOURCE_BILLBOARD_COORDINATE = 32768;
	private static final int MAX_DRAW_BILLBOARD_SIZE = 8192;
	private static final int MAX_CANVAS_COORDINATE = 1_000_000;
	private static final double MAX_PROJECTION_SHEAR_SLOPE = 0.20d;
	private static final double MIN_VERTICAL_PROJECTION = 0.15d;
	private static final double FULL_VERTICAL_PROJECTION = 0.50d;

	private BillboardGeometryUtils()
	{
	}

	public static Rectangle expandedBounds(Rectangle bounds, int padding)
	{
		if (bounds == null || padding <= 0)
		{
			return bounds;
		}

		return new Rectangle(bounds.x - padding, bounds.y - padding, bounds.width + (padding * 2), bounds.height + (padding * 2));
	}

	public static boolean isUsableSourceBounds(Rectangle bounds)
	{
		return bounds != null
			&& bounds.width > 0
			&& bounds.height > 0
			&& bounds.width <= MAX_SOURCE_BILLBOARD_SIZE
			&& bounds.height <= MAX_SOURCE_BILLBOARD_SIZE
			&& Math.abs(bounds.x) <= MAX_SOURCE_BILLBOARD_COORDINATE
			&& Math.abs(bounds.y) <= MAX_SOURCE_BILLBOARD_COORDINATE;
	}

	public static boolean isUsableDistance(double distance)
	{
		return Double.isFinite(distance) && distance >= 1.0d;
	}

	public static int scaledSize(int sourceSize, double scale)
	{
		if (!Double.isFinite(scale) || scale <= 0.0d)
		{
			return -1;
		}

		double scaled = sourceSize * scale;
		if (!Double.isFinite(scaled) || scaled <= 0.0d || scaled > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return Math.max(1, (int) Math.round(scaled));
	}

	public static int projectedHeight(Point basePoint, Point topPoint)
	{
		if (basePoint == null || topPoint == null)
		{
			return 0;
		}

		long height = Math.abs((long) basePoint.getY() - topPoint.getY());
		if (height > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return (int) height;
	}

	public static int aspectWidth(Rectangle bounds, int targetHeight)
	{
		if (targetHeight <= 0 || bounds.height <= 0)
		{
			return -1;
		}

		double width = bounds.width * (targetHeight / (double) bounds.height);
		if (!Double.isFinite(width) || width <= 0.0d || width > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return Math.max(1, (int) Math.round(width));
	}

	/**
	 * Place an orthographic sprite at its model origin using RuneLite's canvas
	 * scale and depth at that same origin. Using an animated model's height to
	 * choose the scale makes even a cached frame breathe as the animation runs.
	 */
	public static Rectangle projectedDrawBounds(Rectangle imageBounds, BillboardCanvasPoint origin, double canvasScale)
	{
		if (imageBounds == null || imageBounds.isEmpty() || origin == null
			|| !isUsableDistance(origin.depth) || !Double.isFinite(origin.x) || !Double.isFinite(origin.y))
		{
			return null;
		}
		double scale = canvasScale / origin.depth;
		int width = scaledSize(imageBounds.width, scale);
		int height = scaledSize(imageBounds.height, scale);
		if (!isUsableDrawSize(width, height))
		{
			return null;
		}
		int x = (int) Math.round(origin.x + imageBounds.x * (width / (double) imageBounds.width));
		int y = (int) Math.round(origin.y + imageBounds.y * (height / (double) imageBounds.height));
		return isUsableCanvasCoordinate(x) && isUsableCanvasCoordinate(y)
			? new Rectangle(x, y, width, height)
			: null;
	}

	/**
	 * Follow the projected world-up direction without fitting the animated model.
	 * Inputs are the screen derivative divided by scale / depth, so zoom, model
	 * height and sprite padding cannot change the lean. Near an overhead view the
	 * projected up direction degenerates; smoothly fade it out before dividing.
	 */
	public static double projectionShearSlope(double verticalRight, double verticalUp)
	{
		if (!Double.isFinite(verticalRight) || !Double.isFinite(verticalUp)
			|| verticalUp <= MIN_VERTICAL_PROJECTION)
		{
			return 0.0d;
		}

		double fraction = Math.min(1.0d,
			(verticalUp - MIN_VERTICAL_PROJECTION) / (FULL_VERTICAL_PROJECTION - MIN_VERTICAL_PROJECTION));
		double fade = fraction * fraction * (3.0d - 2.0d * fraction);
		double slope = verticalRight / verticalUp;
		// A soft limit avoids a sudden change in speed as the camera pans.
		return fade * MAX_PROJECTION_SHEAR_SLOPE * Math.tanh(slope / MAX_PROJECTION_SHEAR_SLOPE);
	}

	public static boolean isUsableDrawSize(int width, int height)
	{
		return isUsableDrawDimension(width) && isUsableDrawDimension(height);
	}

	public static boolean isUsableCanvasCoordinate(int coordinate)
	{
		return Math.abs(coordinate) <= MAX_CANVAS_COORDINATE;
	}

	public static boolean isBackFace(float[] spriteX, float[] spriteY, float[] spriteDepth, int a, int b, int c)
	{
		float abx = spriteX[b] - spriteX[a];
		float aby = spriteY[b] - spriteY[a];
		float acx = spriteX[c] - spriteX[a];
		float acy = spriteY[c] - spriteY[a];
		float normalZ = (abx * acy) - (aby * acx);
		if (Math.abs(normalZ) <= 1.0e-4f)
		{
			return true;
		}

		float abz = spriteDepth[b] - spriteDepth[a];
		float acz = spriteDepth[c] - spriteDepth[a];
		float normalDepth = (aby * acz) - (abz * acy);
		return normalZ >= 0.0f || !Float.isFinite(normalDepth);
	}

	public static Rectangle computeBounds(List<FaceDraw> faces)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;

		for (FaceDraw face : faces)
		{
			minX = Math.min(minX, Math.min(face.x0, Math.min(face.x1, face.x2)));
			minY = Math.min(minY, Math.min(face.y0, Math.min(face.y1, face.y2)));
			maxX = Math.max(maxX, Math.max(face.x0, Math.max(face.x1, face.x2)));
			maxY = Math.max(maxY, Math.max(face.y0, Math.max(face.y1, face.y2)));
		}

		if (minX == Integer.MAX_VALUE)
		{
			return new Rectangle();
		}

		return new Rectangle(minX, minY, Math.max(1, (maxX - minX) + 1), Math.max(1, (maxY - minY) + 1));
	}

	public static double wrapUnit(double coordinate)
	{
		double wrapped = coordinate - Math.floor(coordinate);
		return wrapped < 0.0d ? wrapped + 1.0d : wrapped;
	}

	private static boolean isUsableDrawDimension(int dimension)
	{
		return dimension > 0 && dimension <= MAX_DRAW_BILLBOARD_SIZE;
	}

}
