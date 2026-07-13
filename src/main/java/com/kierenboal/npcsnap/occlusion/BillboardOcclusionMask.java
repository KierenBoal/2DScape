package com.kierenboal.npcsnap.occlusion;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class BillboardOcclusionMask
{
	private static final Color DEBUG_OCCLUSION_CELL = new Color(0, 220, 255, 70);
	private static final Color DEBUG_RASTER_BOUNDS = new Color(0, 220, 255, 220);

	private float[] depthBuffer = new float[0];
	private Occluder[] occluderBuffer = new Occluder[0];
	private String[] sourceBuffer = new String[0];
	private boolean[] rowCoverage = new boolean[0];
	private int width;
	private int height;
	private int step;
	private int viewportX;
	private int viewportY;
	private int coveredCellCount;
	private float nearestDepth = Float.POSITIVE_INFINITY;
	private float furthestDepth = Float.NEGATIVE_INFINITY;

	public void prepare(
		List<Occluder> occluders,
		BillboardOcclusionQuality quality,
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight)
	{
		prepare(occluders, quality, viewportX, viewportY, viewportWidth, viewportHeight, null);
	}

	public void prepare(
		List<Occluder> occluders,
		BillboardOcclusionQuality quality,
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight,
		Rectangle interestBounds)
	{
		prepare(occluders, quality, viewportX, viewportY, viewportWidth, viewportHeight, interestBounds, null);
	}

	public void prepare(
		List<Occluder> occluders,
		BillboardOcclusionQuality quality,
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight,
		Rectangle interestBounds,
		Collection<Rectangle> activeRegions)
	{
		BillboardOcclusionQuality normalizedQuality = BillboardOcclusionQuality.normalize(quality);
		int qualityStep = normalizedQuality.sampleStep();
		Rectangle rasterBounds = effectiveRasterBounds(viewportX, viewportY, viewportWidth, viewportHeight, interestBounds);
		List<Rectangle> clippedActiveRegions = activeRegions(rasterBounds, activeRegions);
		if (qualityStep <= 0 || rasterBounds == null)
		{
			step = 0;
			width = 0;
			height = 0;
			coveredCellCount = 0;
			nearestDepth = Float.POSITIVE_INFINITY;
			furthestDepth = Float.NEGATIVE_INFINITY;
			return;
		}

		this.viewportX = rasterBounds.x;
		this.viewportY = rasterBounds.y;
		this.step = qualityStep;
		width = Math.max(1, (rasterBounds.width + qualityStep - 1) / qualityStep);
		height = Math.max(1, (rasterBounds.height + qualityStep - 1) / qualityStep);
		int size = width * height;
		if (depthBuffer.length < size)
		{
			depthBuffer = new float[size];
		}
		if (sourceBuffer.length < size)
		{
			sourceBuffer = new String[size];
		}
		if (occluderBuffer.length < size)
		{
			occluderBuffer = new Occluder[size];
		}
		if (rowCoverage.length < height)
		{
			rowCoverage = new boolean[height];
		}
		Arrays.fill(depthBuffer, 0, size, Float.POSITIVE_INFINITY);
		Arrays.fill(occluderBuffer, 0, size, null);
		Arrays.fill(sourceBuffer, 0, size, null);
		Arrays.fill(rowCoverage, 0, height, false);
		coveredCellCount = 0;
		nearestDepth = Float.POSITIVE_INFINITY;
		furthestDepth = Float.NEGATIVE_INFINITY;

		if (occluders == null || occluders.isEmpty())
		{
			return;
		}

		for (Occluder occluder : occluders)
		{
			rasterize(occluder, rasterBounds.x, rasterBounds.y, rasterBounds.width, rasterBounds.height, qualityStep, clippedActiveRegions);
		}

		updateDepthRange(size);
	}

	public boolean isOccluded(int canvasX, int canvasY, double billboardDepth)
	{
		if (step <= 0 || !Double.isFinite(billboardDepth))
		{
			return false;
		}

		int sampleX = (canvasX - viewportX) / step;
		int sampleY = (canvasY - viewportY) / step;
		if (sampleX < 0 || sampleY < 0 || sampleX >= width || sampleY >= height)
		{
			return false;
		}

		int index = (sampleY * width) + sampleX;
		float occluderDepth = depthAt(index, canvasX, canvasY);
		return Float.isFinite(occluderDepth) && occluderDepth + depthBias() < billboardDepth;
	}

	public boolean isOccludedSample(int sampleX, int canvasY, double billboardDepth)
	{
		if (step <= 0 || !Double.isFinite(billboardDepth))
		{
			return false;
		}

		int sampleY = sampleY(canvasY);
		if (sampleX < 0 || sampleY < 0 || sampleX >= width || sampleY >= height)
		{
			return false;
		}

		int index = (sampleY * width) + sampleX;
		int canvasX = viewportX + (sampleX * step) + (step / 2);
		float occluderDepth = depthAt(index, canvasX, canvasY);
		return Float.isFinite(occluderDepth) && occluderDepth + depthBias() < billboardDepth;
	}

	public boolean hasCoverageAt(int canvasY)
	{
		if (step <= 0)
		{
			return false;
		}

		int sampleY = sampleY(canvasY);
		return sampleY >= 0 && sampleY < height && rowCoverage[sampleY];
	}

	public int sampleX(int canvasX)
	{
		return step > 0 ? (canvasX - viewportX) / step : -1;
	}

	public int sampleStep()
	{
		return step;
	}

	public float depthAt(int canvasX, int canvasY)
	{
		if (step <= 0)
		{
			return Float.NaN;
		}

		int sampleX = (canvasX - viewportX) / step;
		int sampleY = (canvasY - viewportY) / step;
		if (sampleX < 0 || sampleY < 0 || sampleX >= width || sampleY >= height)
		{
			return Float.NaN;
		}

		int index = (sampleY * width) + sampleX;
		return depthAt(index, canvasX, canvasY);
	}

	public String sourceAt(int canvasX, int canvasY)
	{
		if (step <= 0)
		{
			return null;
		}

		int sampleX = (canvasX - viewportX) / step;
		int sampleY = (canvasY - viewportY) / step;
		if (sampleX < 0 || sampleY < 0 || sampleX >= width || sampleY >= height)
		{
			return null;
		}

		return sourceBuffer[(sampleY * width) + sampleX];
	}

	private float depthAt(int index, int canvasX, int canvasY)
	{
		float sampledDepth = depthBuffer[index];
		Occluder occluder = occluderBuffer[index];
		if (occluder == null || !occluder.vertical)
		{
			return sampledDepth;
		}

		float rowDepth = occluder.depthAt(canvasX, canvasY);
		return Float.isFinite(rowDepth) ? rowDepth : sampledDepth;
	}

	public float occlusionDepthBias()
	{
		return depthBias();
	}

	public void drawDebug(Graphics2D graphics)
	{
		if (graphics == null || step <= 0)
		{
			return;
		}

		Color oldColor = graphics.getColor();
		for (int y = 0; y < height; y++)
		{
			int row = y * width;
			int canvasY = viewportY + (y * step);
			for (int x = 0; x < width; x++)
			{
				float depth = depthBuffer[row + x];
				if (!Float.isFinite(depth))
				{
					continue;
				}

				graphics.setColor(debugDepthColor(depth));
				graphics.fillRect(viewportX + (x * step), canvasY, step, step);
			}
		}

		graphics.setColor(DEBUG_RASTER_BOUNDS);
		graphics.drawRect(viewportX, viewportY, Math.max(0, (width * step) - 1), Math.max(0, (height * step) - 1));
		graphics.setColor(oldColor);
	}

	public int coveredCellCount()
	{
		return coveredCellCount;
	}

	public float nearestDepth()
	{
		return nearestDepth;
	}

	public float furthestDepth()
	{
		return furthestDepth;
	}

	public Rectangle rasterBounds()
	{
		return step > 0 ? new Rectangle(viewportX, viewportY, width * step, height * step) : null;
	}

	private void rasterize(
		Occluder occluder,
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight,
		int step,
		List<Rectangle> activeRegions)
	{
		if (occluder == null)
		{
			return;
		}

		Rectangle bounds = occluder.bounds();
		if (bounds == null || bounds.isEmpty())
		{
			return;
		}

		int left = Math.max(0, (bounds.x - viewportX) / step);
		int top = Math.max(0, (bounds.y - viewportY) / step);
		int right = Math.min(width - 1, ((bounds.x + bounds.width) - viewportX + step - 1) / step);
		int bottom = Math.min(height - 1, ((bounds.y + bounds.height) - viewportY + step - 1) / step);
		if (left > right || top > bottom)
		{
			return;
		}

		for (int sampleY = top; sampleY <= bottom; sampleY++)
		{
			int canvasY = viewportY + (sampleY * step) + (step / 2);
			if (canvasY < viewportY || canvasY >= viewportY + viewportHeight || !rowIntersectsActiveRegion(canvasY, activeRegions))
			{
				continue;
			}

			int row = sampleY * width;
			for (int sampleX = left; sampleX <= right; sampleX++)
			{
				int canvasX = viewportX + (sampleX * step) + (step / 2);
				if (canvasX < viewportX
					|| canvasX >= viewportX + viewportWidth
					|| !isInActiveRegion(canvasX, canvasY, activeRegions))
				{
					continue;
				}

				float depth = occluder.depthAt(canvasX, canvasY);
				if (!Float.isFinite(depth))
				{
					continue;
				}

				int index = row + sampleX;
				if (depth < depthBuffer[index])
				{
					if (!Float.isFinite(depthBuffer[index]))
					{
						coveredCellCount++;
						rowCoverage[sampleY] = true;
					}
					depthBuffer[index] = depth;
					occluderBuffer[index] = occluder;
					sourceBuffer[index] = occluder.source();
				}
			}
		}
	}

	private float depthBias()
	{
		return Math.max(8f, step * 2f);
	}

	private int sampleY(int canvasY)
	{
		return (canvasY - viewportY) / step;
	}

	private void updateDepthRange(int size)
	{
		for (int i = 0; i < size; i++)
		{
			float depth = depthBuffer[i];
			if (!Float.isFinite(depth))
			{
				continue;
			}

			nearestDepth = Math.min(nearestDepth, depth);
			furthestDepth = Math.max(furthestDepth, depth);
		}
	}

	private Color debugDepthColor(float depth)
	{
		float range = furthestDepth - nearestDepth;
		float nearAmount = Float.isFinite(range) && range > 0.0f
			? 1.0f - Math.max(0.0f, Math.min(1.0f, (depth - nearestDepth) / range))
			: 1.0f;
		int red = Math.round(DEBUG_OCCLUSION_CELL.getRed() * nearAmount);
		int green = Math.round(DEBUG_OCCLUSION_CELL.getGreen() * nearAmount);
		int blue = Math.round(DEBUG_OCCLUSION_CELL.getBlue() * nearAmount);
		return new Color(red, green, blue, DEBUG_OCCLUSION_CELL.getAlpha());
	}

	private static Rectangle effectiveRasterBounds(
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight,
		Rectangle interestBounds)
	{
		if (viewportWidth <= 0 || viewportHeight <= 0)
		{
			return null;
		}

		Rectangle viewportBounds = new Rectangle(viewportX, viewportY, viewportWidth, viewportHeight);
		if (interestBounds == null)
		{
			return viewportBounds;
		}

		Rectangle clipped = viewportBounds.intersection(interestBounds);
		return clipped.isEmpty() ? null : clipped;
	}

	private static List<Rectangle> activeRegions(Rectangle rasterBounds, Collection<Rectangle> activeRegions)
	{
		if (rasterBounds == null || activeRegions == null || activeRegions.isEmpty())
		{
			return Collections.emptyList();
		}

		List<Rectangle> clipped = new ArrayList<>(activeRegions.size());
		for (Rectangle activeRegion : activeRegions)
		{
			if (activeRegion == null || activeRegion.isEmpty())
			{
				continue;
			}

			Rectangle region = rasterBounds.intersection(activeRegion);
			if (!region.isEmpty())
			{
				clipped.add(region);
			}
		}

		return clipped;
	}

	private static boolean rowIntersectsActiveRegion(int canvasY, List<Rectangle> activeRegions)
	{
		if (activeRegions == null || activeRegions.isEmpty())
		{
			return true;
		}

		for (Rectangle activeRegion : activeRegions)
		{
			if (canvasY >= activeRegion.y && canvasY < activeRegion.y + activeRegion.height)
			{
				return true;
			}
		}

		return false;
	}

	private static boolean isInActiveRegion(int canvasX, int canvasY, List<Rectangle> activeRegions)
	{
		if (activeRegions == null || activeRegions.isEmpty())
		{
			return true;
		}

		for (Rectangle activeRegion : activeRegions)
		{
			if (activeRegion.contains(canvasX, canvasY))
			{
				return true;
			}
		}

		return false;
	}

	public static final class Occluder
	{
		private final Shape shape;
		private final float depth;
		private final boolean triangle;
		private final boolean vertical;
		private final int x0;
		private final int y0;
		private final float depth0;
		private final int x1;
		private final int y1;
		private final float depth1;
		private final int x2;
		private final int y2;
		private final float depth2;
		private final double barycentricDenominator;
		private final Rectangle bounds;
		private final String source;

		Occluder(Shape shape, float depth)
		{
			this(shape, depth, null);
		}

		Occluder(Shape shape, float depth, String source)
		{
			this.shape = shape;
			this.depth = depth;
			triangle = false;
			vertical = false;
			x0 = 0;
			y0 = 0;
			depth0 = Float.NaN;
			x1 = 0;
			y1 = 0;
			depth1 = Float.NaN;
			x2 = 0;
			y2 = 0;
			depth2 = Float.NaN;
			barycentricDenominator = Double.NaN;
			bounds = shape != null ? shape.getBounds() : null;
			this.source = source;
		}

		static Occluder triangle(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2)
		{
			return triangle(x0, y0, depth0, x1, y1, depth1, x2, y2, depth2, null);
		}

		static Occluder triangle(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2, String source)
		{
			if (!Float.isFinite(depth0) || !Float.isFinite(depth1) || !Float.isFinite(depth2))
			{
				return null;
			}

			return new Occluder(x0, y0, depth0, x1, y1, depth1, x2, y2, depth2, source);
		}

		private Occluder(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2, String source)
		{
			shape = null;
			depth = Float.NaN;
			triangle = true;
			vertical = false;
			this.x0 = x0;
			this.y0 = y0;
			this.depth0 = depth0;
			this.x1 = x1;
			this.y1 = y1;
			this.depth1 = depth1;
			this.x2 = x2;
			this.y2 = y2;
			this.depth2 = depth2;
			barycentricDenominator = ((y1 - y2) * (double) (x0 - x2))
				+ ((x2 - x1) * (double) (y0 - y2));
			int minX = Math.min(x0, Math.min(x1, x2));
			int minY = Math.min(y0, Math.min(y1, y2));
			int maxX = Math.max(x0, Math.max(x1, x2));
			int maxY = Math.max(y0, Math.max(y1, y2));
			bounds = new Rectangle(minX, minY, Math.max(1, (maxX - minX) + 1), Math.max(1, (maxY - minY) + 1));
			this.source = source;
		}

		static Occluder vertical(Shape shape, int baseY, float baseDepth, int topY, float topDepth, String source)
		{
			if (shape == null || shape.getBounds().isEmpty() || baseY == topY
				|| !Float.isFinite(baseDepth) || !Float.isFinite(topDepth) || baseDepth <= 0.0f || topDepth <= 0.0f)
			{
				return null;
			}

			return new Occluder(shape, baseY, baseDepth, topY, topDepth, source);
		}

		private Occluder(Shape shape, int baseY, float baseDepth, int topY, float topDepth, String source)
		{
			this.shape = shape;
			depth = Float.NaN;
			triangle = false;
			vertical = true;
			x0 = 0;
			y0 = baseY;
			depth0 = baseDepth;
			x1 = 0;
			y1 = topY;
			depth1 = topDepth;
			x2 = 0;
			y2 = 0;
			depth2 = Float.NaN;
			barycentricDenominator = Double.NaN;
			bounds = shape.getBounds();
			this.source = source;
		}

		private Rectangle bounds()
		{
			return bounds;
		}

		private String source()
		{
			return source;
		}

		private float depthAt(int canvasX, int canvasY)
		{
			if (vertical)
			{
				// Perspective projection makes reciprocal camera depth linear in screen Y
				// along a fixed vertical world line. This is exact for the sampled anchor
				// and avoids treating an entire tall hull as if it were at one depth.
				double t = (canvasY - y0) / (double) (y1 - y0);
				double reciprocalDepth = ((1.0d - t) / depth0) + (t / depth1);
				if (!Double.isFinite(reciprocalDepth) || reciprocalDepth <= 0.0d)
				{
					return Float.NaN;
				}

				double interpolated = 1.0d / reciprocalDepth;
				return Double.isFinite(interpolated) ? (float) interpolated : Float.NaN;
			}

			if (!triangle)
			{
				return shape != null && shape.contains(canvasX, canvasY) ? depth : Float.NaN;
			}

			if (Math.abs(barycentricDenominator) <= 1.0e-6d)
			{
				return Float.NaN;
			}

			double a = (((y1 - y2) * (double) (canvasX - x2)) + ((x2 - x1) * (double) (canvasY - y2))) / barycentricDenominator;
			double b = (((y2 - y0) * (double) (canvasX - x2)) + ((x0 - x2) * (double) (canvasY - y2))) / barycentricDenominator;
			double c = 1.0d - a - b;
			if (a < -1.0e-6d || b < -1.0e-6d || c < -1.0e-6d)
			{
				return Float.NaN;
			}

			double interpolated = (a * depth0) + (b * depth1) + (c * depth2);
			return Double.isFinite(interpolated) ? (float) interpolated : Float.NaN;
		}
	}

}
