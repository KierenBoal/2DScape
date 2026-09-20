package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import net.runelite.api.geometry.SimplePolygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.runelite.api.TileObject;

public final class BillboardOcclusionMask
{
	public enum CellResult
	{
		VISIBLE,
		OCCLUDED,
		REFINE
	}

	private final BillboardPerformanceMetrics performanceMetrics;

	public BillboardOcclusionMask()
	{
		this(new BillboardPerformanceMetrics());
	}

	public BillboardOcclusionMask(BillboardPerformanceMetrics performanceMetrics)
	{
		this.performanceMetrics = performanceMetrics;
	}

	private int[] heads = new int[0];
	private int[] next = new int[0];
	private int[] candidates = new int[0];
	private int[] coverageRows = new int[0];
	private int[] coverageOffsets = new int[0];
	private int[] candidateTransmittance = new int[0];
	private int[] candidateTintRgb = new int[0];
	private int coverageRowCount;
	private float[] candidateFar = new float[0];
	private float[] lower = new float[0];
	private float[] upper = new float[0];
	private boolean[] active = new boolean[0];
	private boolean[] fullRegion = new boolean[0];
	private boolean[] rowCoverage = new boolean[0];
	private List<Occluder> occluders = Collections.emptyList();
	private List<Rectangle> regions = Collections.emptyList();
	private Rectangle bounds;
	private int width;
	private int height;
	private int step;
	private boolean blocky;
	private BillboardOcclusionComposition composition = BillboardOcclusionComposition.TRANSPARENCY_AWARE;
	private int references;
	private int coveredCellCount;
	private long uniformDecisions;
	private long refinedPixels;
	private long candidateTests;
	private int[] compatibilityTransmittance = new int[0];

	public void prepare(List<Occluder> occluders, BillboardOcclusionQuality quality,
		int x, int y, int w, int h)
	{
		prepare(occluders, quality, BillboardOcclusionComposition.TRANSPARENCY_AWARE, x, y, w, h, null, null);
	}

	public void prepare(List<Occluder> occluders, BillboardOcclusionQuality quality,
		int x, int y, int w, int h, Rectangle interest)
	{
		prepare(occluders, quality, BillboardOcclusionComposition.TRANSPARENCY_AWARE, x, y, w, h, interest, null);
	}

	public void prepare(List<Occluder> occluders, BillboardOcclusionQuality quality,
		int x, int y, int w, int h, Rectangle interest, Collection<Rectangle> activeRegions)
	{
		prepare(occluders, quality, BillboardOcclusionComposition.TRANSPARENCY_AWARE, x, y, w, h, interest, activeRegions);
	}

	public void prepare(List<Occluder> occluders, BillboardOcclusionQuality quality, BillboardOcclusionComposition composition,
		int x, int y, int w, int h, Rectangle interest, Collection<Rectangle> activeRegions)
	{
		step = BillboardOcclusionQuality.normalize(quality).sampleStep();
		blocky = quality == BillboardOcclusionQuality.BLOCKY;
		this.composition = BillboardOcclusionComposition.normalize(composition);
		bounds = new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
		if (interest != null)
		{
			bounds = bounds.intersection(interest);
		}
		references = coveredCellCount = coverageRowCount = 0;
		this.occluders = occluders == null ? Collections.emptyList() : occluders;
		if (step == 0 || bounds.isEmpty())
		{
			step = width = height = 0;
			this.occluders = Collections.emptyList();
			return;
		}
		regions = new ArrayList<>();
		if (activeRegions == null || activeRegions.isEmpty())
		{
			regions.add(bounds);
		}
		else
		{
			for (Rectangle region : activeRegions)
			{
				if (region != null && region.intersects(bounds))
				{
					regions.add(bounds.intersection(region));
				}
			}
		}
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Initialize mask buffers"))
		{
			width = (bounds.width + step - 1) / step;
			height = (bounds.height + step - 1) / step;
			int size = width * height;
			if (heads.length < size)
			{
				heads = new int[size];
				lower = new float[size];
				upper = new float[size];
				active = new boolean[size];
				fullRegion = new boolean[size];
			}
			if (rowCoverage.length < height)
			{
				rowCoverage = new boolean[height];
			}
			Arrays.fill(heads, 0, size, -1);
			Arrays.fill(lower, 0, size, Float.POSITIVE_INFINITY);
			Arrays.fill(upper, 0, size, Float.POSITIVE_INFINITY);
			Arrays.fill(active, 0, size, false);
			Arrays.fill(fullRegion, 0, size, false);
			Arrays.fill(rowCoverage, 0, height, false);
		}
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Mark active mask regions"))
		{
			// Mark each rectangle's cell span directly, rather than visiting every region per cell.
			for (Rectangle region : regions)
			{
				int left = (region.x - bounds.x) / step;
				int right = (region.x + region.width - 1 - bounds.x) / step;
				int top = (region.y - bounds.y) / step;
				int bottom = (region.y + region.height - 1 - bounds.y) / step;
				for (int cy = top; cy <= bottom; cy++)
				{
					int py = bounds.y + cy * step;
					int ch = Math.min(step, bounds.y + bounds.height - py);
					Arrays.fill(active, cy * width + left, cy * width + right + 1, true);
					for (int cx = left; cx <= right; cx++)
					{
						int px = bounds.x + cx * step;
						int cw = Math.min(step, bounds.x + bounds.width - px);
						fullRegion[cy * width + cx] |= region.contains(px, py, cw, ch);
					}
				}
			}
		}
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Index occluder coverage"))
		{
			for (int id = 0; id < this.occluders.size(); id++)
			{
				Occluder o = this.occluders.get(id);
				if (o == null || o.bounds == null || !o.bounds.intersects(bounds))
				{
					continue;
				}
				int left = Math.max(0, Math.floorDiv(o.bounds.x - bounds.x, step));
				int top = Math.max(0, Math.floorDiv(o.bounds.y - bounds.y, step));
				int right = Math.min(width - 1, Math.floorDiv(o.bounds.x + o.bounds.width - 1 - bounds.x, step));
				int bottom = Math.min(height - 1, Math.floorDiv(o.bounds.y + o.bounds.height - 1 - bounds.y, step));
				for (int cy = top; cy <= bottom; cy++)
				{
					int py = bounds.y + cy * step;
					int ch = Math.min(step, bounds.y + bounds.height - py);
					float near = o.minimumDepth(py, ch);
					float far = blocky ? near : o.maximumDepth(py, ch);
					for (int cx = left; cx <= right; cx++)
					{
						int cell = cy * width + cx;
						if (!active[cell])
						{
							continue;
						}
						int px = bounds.x + cx * step;
						int cw = Math.min(step, bounds.x + bounds.width - px);
						// Blocky fills bounding-box cells without triangle or per-pixel coverage tests.
						int coverage = blocky ? 2 : o.coverage(px, py, cw, ch);
						if (coverage == 0)
						{
							continue;
						}
						if (references == next.length)
						{
							int capacity = Math.max(256, references + references / 2);
							next = Arrays.copyOf(next, capacity);
							candidates = Arrays.copyOf(candidates, capacity);
							candidateFar = Arrays.copyOf(candidateFar, capacity);
							coverageOffsets = Arrays.copyOf(coverageOffsets, capacity);
							candidateTransmittance = Arrays.copyOf(candidateTransmittance, capacity);
							candidateTintRgb = Arrays.copyOf(candidateTintRgb, capacity);
						}
						if (heads[cell] == -1)
						{
							coveredCellCount++;
							rowCoverage[cy] = true;
						}
						// Coverage bits retain crisp edges without repeating triangle membership
						// calculations for each overlapping billboard.
						if (coverage == 2)
						{
							// Negative offsets encode a constant full row, without allocating row data.
							coverageOffsets[references] = -((1 << cw) - 1);
						}
						else
						{
							int requiredRows = coverageRowCount + ch;
							if (coverageRows.length < requiredRows)
							{
								coverageRows = Arrays.copyOf(coverageRows,
									Math.max(requiredRows, Math.max(256, coverageRows.length * 2)));
							}
							coverageOffsets[references] = coverageRowCount;
							for (int row = 0; row < ch; row++)
							{
								coverageRows[coverageRowCount++] = o.coverageRow(px, py + row, cw);
							}
						}
						int reference = references++;
						candidateFar[reference] = far;
						candidates[reference] = id;
						candidateTransmittance[reference] = effectiveTransmittance(o);
						candidateTintRgb[reference] = o.tintRgb;
						next[reference] = heads[cell];
						heads[cell] = reference;
						lower[cell] = Math.min(lower[cell], near);
						if (coverage == 2 && candidateTransmittance[reference] == 0)
						{
							upper[cell] = Math.min(upper[cell], far);
						}
					}
				}
			}
		}

	}

	private int index(int x, int y)
	{
		if (step == 0 || !bounds.contains(x, y))
		{
			return -1;
		}
		int cell = ((y - bounds.y) / step) * width + (x - bounds.x) / step;
		if (!fullRegion[cell])
		{
			boolean inside = false;
			for (Rectangle region : regions)
			{
				inside |= region.contains(x, y);
			}
			if (!inside)
			{
				return -1;
			}
		}
		return cell;
	}

	public CellResult classifySample(int sampleX, int y, double depth)
	{
		return classifySample(sampleX, y, depth, null);
	}

	public CellResult classifySample(int sampleX, int y, double depth, TileObject ignoredTileObject)
	{
		if (step == 0 || !Double.isFinite(depth) || sampleX < 0 || sampleX >= width
			|| y < bounds.y || y >= bounds.y + bounds.height)
		{
			return CellResult.VISIBLE;
		}
		int cell = ((y - bounds.y) / step) * width + sampleX;
		// The aggregate cell bounds include every scene object. If this billboard is
		// replacing one of those objects, force the precise path whenever its own
		// geometry is present so the self-occluder can be skipped without affecting
		// other world objects in the same cell.
		if (ignoredTileObject != null && cellContainsOwner(cell, ignoredTileObject))
		{
			return CellResult.REFINE;
		}
		CellResult result = CellResult.REFINE;
		if (heads[cell] == -1 || lower[cell] + depthBias() >= depth)
		{
			result = CellResult.VISIBLE;
		}
		else if (fullRegion[cell] && upper[cell] + depthBias() < depth)
		{
			result = CellResult.OCCLUDED;
		}
		if (result != CellResult.REFINE)
		{
			uniformDecisions++;
		}
		return result;
	}

	private int coverageBits(int ref, int row)
	{
		int offset = coverageOffsets[ref];
		return offset < 0 ? -offset : coverageRows[offset + row];
	}

	private boolean cellContainsOwner(int cell, TileObject ignoredTileObject)
	{
		for (int ref = heads[cell]; ref != -1; ref = next[ref])
		{
			if (occluders.get(candidates[ref]).belongsTo(ignoredTileObject))
			{
				return true;
			}
		}
		return false;
	}

	private int effectiveTransmittance(Occluder occluder)
	{
		return composition == BillboardOcclusionComposition.HARD_CUTOUT ? 0 : occluder.transmittance;
	}

	/**
	 * Returns the amount of the billboard pixel which remains visible after all
	 * closer world geometry. 255 is fully visible and 0 is fully occluded.
	 */
	public int transmittanceAt(int x, int y, double depth)
	{
		return transmittanceAt(x, y, depth, null);
	}

	public int transmittanceAt(int x, int y, double depth, TileObject ignoredTileObject)
	{
		int cell = index(x, y);
		if (cell < 0 || !Double.isFinite(depth))
		{
			return 255;
		}
		if (!blocky)
		{
			refinedPixels++;
		}
		int localX = (x - bounds.x) % step;
		int localY = (y - bounds.y) % step;
		int bit = 1 << localX;
		int transmittance = 255;
		for (int ref = heads[cell]; ref != -1; ref = next[ref])
		{
			candidateTests++;
			if (occluders.get(candidates[ref]).belongsTo(ignoredTileObject))
			{
				continue;
			}
			if ((coverageBits(ref, localY) & bit) == 0)
			{
				continue;
			}
			if (candidateFar[ref] + depthBias() >= depth)
			{
				if (blocky)
				{
					continue;
				}
				float value = occluders.get(candidates[ref]).depthAt(x, y);
				if (!Float.isFinite(value) || value + depthBias() >= depth)
				{
					continue;
				}
			}
			transmittance = multiplyTransmittance(transmittance, candidateTransmittance[ref]);
			if (transmittance == 0)
			{
				return 0;
			}
		}
		return transmittance;
	}

	/**
	 * Applies closer translucent face colours over an opaque billboard pixel,
	 * preserving the billboard's original alpha rather than making it ghostly.
	 */
	public int tintPixel(int x, int y, double depth, int pixel)
	{
		return tintPixel(x, y, depth, pixel, null);
	}

	public int tintPixel(int x, int y, double depth, int pixel, TileObject ignoredTileObject)
	{
		int cell = index(x, y);
		if (cell < 0 || !Double.isFinite(depth))
		{
			return pixel;
		}
		int localX = (x - bounds.x) % step;
		int localY = (y - bounds.y) % step;
		int bit = 1 << localX;
		int tinted = pixel;
		for (int ref = heads[cell]; ref != -1; ref = next[ref])
		{
			if (occluders.get(candidates[ref]).belongsTo(ignoredTileObject))
			{
				continue;
			}
			if ((coverageBits(ref, localY) & bit) == 0 || candidateTransmittance[ref] == 0)
			{
				continue;
			}
			if (candidateFar[ref] + depthBias() >= depth)
			{
				if (blocky)
				{
					continue;
				}
				float value = occluders.get(candidates[ref]).depthAt(x, y);
				if (!Float.isFinite(value) || value + depthBias() >= depth)
				{
					continue;
				}
			}
			tinted = blendScreenTint(tinted, candidateTintRgb[ref], candidateTransmittance[ref]);
		}
		return tinted;
	}

	private static int blendScreenTint(int pixel, int tintRgb, int transmittance)
	{
		int foregroundOpacity = 255 - transmittance;
		int red = screenBlend((pixel >>> 16) & 0xFF, (tintRgb >>> 16) & 0xFF, transmittance, foregroundOpacity);
		int green = screenBlend((pixel >>> 8) & 0xFF, (tintRgb >>> 8) & 0xFF, transmittance, foregroundOpacity);
		int blue = screenBlend(pixel & 0xFF, tintRgb & 0xFF, transmittance, foregroundOpacity);
		return (pixel & 0xFF000000) | (red << 16) | (green << 8) | blue;
	}

	private static int screenBlend(int source, int tint, int transmittance, int foregroundOpacity)
	{
		int screened = 255 - (((255 - source) * (255 - tint) + 127) / 255);
		return (source * transmittance + screened * foregroundOpacity + 127) / 255;
	}

	private static int multiplyTransmittance(int left, int right)
	{
		return (left * right + 127) / 255;
	}

	public boolean isOccluded(int x, int y, double depth)
	{
		return isOccluded(x, y, depth, null);
	}

	public boolean isOccluded(int x, int y, double depth, TileObject ignoredTileObject)
	{
		return transmittanceAt(x, y, depth, ignoredTileObject) == 0;
	}

	/**
	 * Resolves a short ambiguous cell row once. Each bit is an independent pixel
	 * decision; sharing this bitset never spreads one sample across an edge.
	 */
	public int refinedSampleBits(int sampleX, int y, double depth)
	{
		return refinedSampleBits(sampleX, y, depth, null);
	}

	public int refinedSampleBits(int sampleX, int y, double depth, TileObject ignoredTileObject)
	{
		if (compatibilityTransmittance.length < Math.max(1, step))
		{
			compatibilityTransmittance = new int[Math.max(1, step)];
		}
		refinedSampleTransmittance(sampleX, y, depth, compatibilityTransmittance, ignoredTileObject);
		int bits = 0;
		for (int offset = 0; offset < step; offset++)
		{
			if (compatibilityTransmittance[offset] == 0)
			{
				bits |= 1 << offset;
			}
		}
		return bits;
	}

	/** Resolves every pixel in a coarse-cell row into reusable caller-owned storage. */
	public void refinedSampleTransmittance(int sampleX, int y, double depth, int[] output)
	{
		refinedSampleTransmittance(sampleX, y, depth, output, null);
	}

	public void refinedSampleTransmittance(int sampleX, int y, double depth, int[] output, TileObject ignoredTileObject)
	{
		if (output == null || output.length < step)
		{
			throw new IllegalArgumentException("Output must fit one occlusion sample row");
		}
		Arrays.fill(output, 0, step, 255);
		if (step == 0 || sampleX < 0 || sampleX >= width || !Double.isFinite(depth)
			|| y < bounds.y || y >= bounds.y + bounds.height)
		{
			return;
		}
		if (blocky)
		{
			int cell = ((y - bounds.y) / step) * width + sampleX;
			int value = 255;
			for (int reference = heads[cell]; reference != -1; reference = next[reference])
			{
				candidateTests++;
				if (occluders.get(candidates[reference]).belongsTo(ignoredTileObject))
				{
					continue;
				}
				if (candidateFar[reference] + depthBias() < depth)
				{
					value = multiplyTransmittance(value, candidateTransmittance[reference]);
				}
			}
			Arrays.fill(output, 0, step, value);
			// Keep clipping to actual sprite regions even though geometry edges are coarse.
			int startX = bounds.x + sampleX * step;
			if (!fullRegion[cell])
			{
				for (int offset = 0; offset < step; offset++)
				{
					if (index(startX + offset, y) < 0)
					{
						output[offset] = 255;
					}
				}
			}
			return;
		}
		int cell = ((y - bounds.y) / step) * width + sampleX;
		int localY = (y - bounds.y) % step;
		int x = bounds.x + sampleX * step;
		for (int ref = heads[cell]; ref != -1; ref = next[ref])
		{
			candidateTests++;
			if (occluders.get(candidates[ref]).belongsTo(ignoredTileObject))
			{
				continue;
			}
			int covered = coverageBits(ref, localY);
			Occluder o = occluders.get(candidates[ref]);
			while (covered != 0)
			{
				int offset = Integer.numberOfTrailingZeros(covered);
				if (!blocky)
		{
			refinedPixels++;
		}
				if (candidateFar[ref] + depthBias() < depth)
				{
					output[offset] = multiplyTransmittance(output[offset], candidateTransmittance[ref]);
				}
				else
				{
					float value = o.depthAt(x + offset, y);
					if (Float.isFinite(value) && value + depthBias() < depth)
					{
						output[offset] = multiplyTransmittance(output[offset], candidateTransmittance[ref]);
					}
				}
				covered &= covered - 1;
			}
		}
		if (!fullRegion[cell])
		{
			for (int offset = 0; offset < step; offset++)
			{
				if (index(x + offset, y) < 0)
				{
					output[offset] = 255;
				}
			}
		}
	}

	public int sampleOffset(int x)
	{
		return (x - bounds.x) % step;
	}
	public boolean isOccludedSample(int sampleX, int y, double depth)
	{
		return step > 0 && sampleX >= 0 && sampleX < width
			&& isOccluded(bounds.x + sampleX * step + step / 2, y, depth);
	}

	public float depthAt(int x, int y)
	{
		return depthAt(x, y, null);
	}

	public float depthAt(int x, int y, TileObject ignoredTileObject)
	{
		int cell = index(x, y);
		if (cell < 0)
		{
			return Float.NaN;
		}
		float value = Float.POSITIVE_INFINITY;
		for (int ref = heads[cell]; ref != -1; ref = next[ref])
		{
			Occluder occluder = occluders.get(candidates[ref]);
			if (occluder.belongsTo(ignoredTileObject))
			{
				continue;
			}
			float d = occluder.depthAt(x, y);
			if (Float.isFinite(d))
			{
				value = Math.min(value, d);
			}
		}
		return value;
	}

	public String sourceAt(int x, int y)
	{
		return sourceAt(x, y, null);
	}

	public String sourceAt(int x, int y, TileObject ignoredTileObject)
	{
		int cell = index(x, y);
		if (cell < 0)
		{
			return null;
		}
		float nearest = Float.POSITIVE_INFINITY;
		String source = null;
		int winner = Integer.MAX_VALUE;
		for (int ref = heads[cell]; ref != -1; ref = next[ref])
		{
			int id = candidates[ref];
			Occluder o = occluders.get(id);
			if (o.belongsTo(ignoredTileObject))
			{
				continue;
			}
			float d = o.depthAt(x, y);
			if (Float.isFinite(d) && (d < nearest || (d == nearest && id < winner)))
			{
				nearest = d;
				winner = id;
				source = o.source;
			}
		}
		return source;
	}

	public boolean hasCoverageAt(int y)
	{
		return step > 0 && y >= bounds.y && y < bounds.y + bounds.height
			&& rowCoverage[(y - bounds.y) / step];
	}

	public int sampleX(int x)
	{
		return step > 0 && x >= bounds.x && x < bounds.x + bounds.width ? (x - bounds.x) / step : -1;
	}

	public int sampleStep()
	{
		return step;
	}
	public int coveredCellCount()
	{
		return coveredCellCount;
	}
	private float depthBias()
	{
		return Math.max(8f, step * 2f);
	}
	public float occlusionDepthBias()
	{
		return depthBias();
	}

	// Read by the collector's existing throttled debug logger, after preceding frames blit.
	public String drainDebugStats()
	{
		String result = "candidates=" + references + " uniform=" + uniformDecisions
			+ " refinedPixels=" + refinedPixels + " candidateTests=" + candidateTests;
		uniformDecisions = refinedPixels = candidateTests = 0;
		return result;
	}

	static Shape filledFallbackShape(Shape shape)
	{
		// SimplePolygon's rectangle intersection tests boundary crossings only.
		// Use Java2D's filled-area semantics for both contained and crossing cells.
		return shape instanceof SimplePolygon ? new Path2D.Double(shape) : shape;
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
		private final double ax;
		private final double ay;
		private final double bx;
		private final double by;
		private final Rectangle bounds;
		private final String source;
		private final int transmittance;
		private final int tintRgb;
		private final TileObject owner;

		Occluder(Shape shape, float depth)
		{
			this(shape, depth, null);
		}

		Occluder(Shape shape, float depth, String source)
		{
			this(shape, depth, source, 0);
		}

		Occluder(Shape shape, float depth, String source, int transmittance)
		{
			this(shape, depth, source, transmittance, 0xFFFFFF);
		}

		Occluder(Shape shape, float depth, String source, int transmittance, int tintRgb)
		{
			this(shape, depth, source, transmittance, tintRgb, null);
		}

		Occluder(Shape shape, float depth, String source, int transmittance, int tintRgb, TileObject owner)
		{
			this.shape = filledFallbackShape(shape);
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
			ax = ay = bx = by = 0d;
			bounds = shape != null ? shape.getBounds() : null;
			this.source = source;
			this.transmittance = clampTransmittance(transmittance);
			this.tintRgb = tintRgb & 0xFFFFFF;
			this.owner = owner;
		}

		static Occluder triangle(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2)
		{
			return triangle(x0, y0, depth0, x1, y1, depth1, x2, y2, depth2, null);
		}

		static Occluder triangle(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2, String source)
		{
			return triangle(x0, y0, depth0, x1, y1, depth1, x2, y2, depth2, source, 0);
		}

		static Occluder triangle(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2, String source, int transmittance)
		{
			return triangle(x0, y0, depth0, x1, y1, depth1, x2, y2, depth2, source, transmittance, 0xFFFFFF);
		}

		static Occluder triangle(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2, String source, int transmittance, int tintRgb)
		{
			return triangle(x0, y0, depth0, x1, y1, depth1, x2, y2, depth2, source, transmittance, tintRgb, null);
		}

		static Occluder triangle(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2,
			String source, int transmittance, int tintRgb, TileObject owner)
		{
			if (!Float.isFinite(depth0) || !Float.isFinite(depth1) || !Float.isFinite(depth2))
			{
				return null;
			}

			return new Occluder(x0, y0, depth0, x1, y1, depth1, x2, y2, depth2, source, transmittance, tintRgb, owner);
		}

		private Occluder(int x0, int y0, float depth0, int x1, int y1, float depth1, int x2, int y2, float depth2,
			String source, int transmittance, int tintRgb, TileObject owner)
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
			ax = (y1 - y2) / barycentricDenominator;
			ay = (x2 - x1) / barycentricDenominator;
			bx = (y2 - y0) / barycentricDenominator;
			by = (x0 - x2) / barycentricDenominator;
			int minX = Math.min(x0, Math.min(x1, x2));
			int minY = Math.min(y0, Math.min(y1, y2));
			int maxX = Math.max(x0, Math.max(x1, x2));
			int maxY = Math.max(y0, Math.max(y1, y2));
			bounds = new Rectangle(minX, minY, Math.max(1, (maxX - minX) + 1), Math.max(1, (maxY - minY) + 1));
			this.source = source;
			this.transmittance = clampTransmittance(transmittance);
			this.tintRgb = tintRgb & 0xFFFFFF;
			this.owner = owner;
		}

		static Occluder vertical(Shape shape, int baseY, float baseDepth, int topY, float topDepth, String source)
		{
			return vertical(shape, baseY, baseDepth, topY, topDepth, source, null);
		}

		static Occluder vertical(Shape shape, int baseY, float baseDepth, int topY, float topDepth, String source, TileObject owner)
		{
			if (shape == null || shape.getBounds().isEmpty() || baseY == topY
				|| !Float.isFinite(baseDepth) || !Float.isFinite(topDepth) || baseDepth <= 0.0f || topDepth <= 0.0f)
			{
				return null;
			}

			return new Occluder(shape, baseY, baseDepth, topY, topDepth, source, owner);
		}

		private Occluder(Shape shape, int baseY, float baseDepth, int topY, float topDepth, String source, TileObject owner)
		{
			this.shape = filledFallbackShape(shape);
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
			ax = ay = bx = by = 0d;
			bounds = shape.getBounds();
			this.source = source;
			this.transmittance = 0;
			this.tintRgb = 0xFFFFFF;
			this.owner = owner;
		}

		private boolean belongsTo(TileObject tileObject)
		{
			return tileObject != null && owner == tileObject;
		}

		private static int clampTransmittance(int value)
		{
			return Math.max(0, Math.min(255, value));
		}

		// 0: outside, 1: uncertain/partial, 2: every integer pixel is covered.
		private int coverage(int x, int y, int w, int h)
		{
			if (!triangle)
			{
				if (shape == null || !shape.intersects(x, y, w, h))
				{
					return 0;
				}
				return shape.contains(x, y, w, h) ? 2 : 1;
			}
			if (Math.abs(barycentricDenominator) <= 1.0e-6d)
			{
				return 0;
			}
			double a = ax * (x - x2) + ay * (y - y2);
			double b = bx * (x - x2) + by * (y - y2);
			int ea = edgeCoverage(a, ax * (w - 1), ay * (h - 1));
			int eb = edgeCoverage(b, bx * (w - 1), by * (h - 1));
			int ec = edgeCoverage(1d - a - b, (-ax - bx) * (w - 1), (-ay - by) * (h - 1));
			return ea == 0 || eb == 0 || ec == 0 ? 0 : Math.min(ea, Math.min(eb, ec));
		}

		private int coverageRow(int x, int y, int w)
		{
			int bits = 0;
			for (int dx = 0; dx < w; dx++)
			{
				if (triangle)
				{
					double a = ax * (x + dx - x2) + ay * (y - y2);
					double b = bx * (x + dx - x2) + by * (y - y2);
					if (a >= -1.0e-6d && b >= -1.0e-6d && 1d - a - b >= -1.0e-6d)
					{
						bits |= 1 << dx;
					}
				}
				else if (shape.contains(x + dx, y))
				{
					bits |= 1 << dx;
				}
			}
			return bits;
		}
		private static int edgeCoverage(double origin, double dx, double dy)
		{
			// Leave a numerical guard around the exact point-in-triangle tolerance.
			double min = origin + Math.min(0d, dx) + Math.min(0d, dy);
			double max = origin + Math.max(0d, dx) + Math.max(0d, dy);
			return max < -1.0001e-6d ? 0 : min >= -0.9999e-6d ? 2 : 1;
		}

		private float minimumDepth(int y, int h)
		{
			if (vertical)
			{
				float a = verticalDepth(y);
				float b = verticalDepth(y + h - 1);
				return Float.isFinite(a) && Float.isFinite(b) ? Math.min(a, b) : Float.NEGATIVE_INFINITY;
			}
			if (!triangle)
			{
				return Float.isFinite(depth) ? depth : Float.NEGATIVE_INFINITY;
			}
			float min = Math.min(depth0, Math.min(depth1, depth2));
			float max = Math.max(depth0, Math.max(depth1, depth2));
			return Math.nextDown((float) (min - 3.0e-6d * ((double) max - min)));
		}

		private float maximumDepth(int y, int h)
		{
			if (vertical)
			{
				float a = verticalDepth(y);
				float b = verticalDepth(y + h - 1);
				return Float.isFinite(a) && Float.isFinite(b) ? Math.max(a, b) : Float.POSITIVE_INFINITY;
			}
			if (!triangle)
			{
				return Float.isFinite(depth) ? depth : Float.POSITIVE_INFINITY;
			}
			float min = Math.min(depth0, Math.min(depth1, depth2));
			float max = Math.max(depth0, Math.max(depth1, depth2));
			return Math.nextUp((float) (max + 3.0e-6d * ((double) max - min)));
		}

		private float verticalDepth(int y)
		{
			// Reciprocal camera depth is linear along the projected vertical anchor.
			double t = (y - y0) / (double) (y1 - y0);
			double reciprocalDepth = ((1.0d - t) / depth0) + (t / depth1);
			if (!Double.isFinite(reciprocalDepth) || reciprocalDepth <= 0d)
			{
				return Float.NaN;
			}
			return (float) (1d / reciprocalDepth);
		}
		private float depthAt(int canvasX, int canvasY)
		{
			if (vertical)
			{
				return shape.contains(canvasX, canvasY) ? verticalDepth(canvasY) : Float.NaN;
			}
			if (!triangle)
			{
				return shape != null && shape.contains(canvasX, canvasY) ? depth : Float.NaN;
			}

			if (Math.abs(barycentricDenominator) <= 1.0e-6d)
			{
				return Float.NaN;
			}

			double a = ax * (canvasX - x2) + ay * (canvasY - y2);
			double b = bx * (canvasX - x2) + by * (canvasY - y2);
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
