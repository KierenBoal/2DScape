package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionMask;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import net.runelite.api.TileObject;

public final class BillboardFrameBuffer
{
	public interface DepthSurfaceFactory
	{
		BillboardDepthSurface create(PreparedBillboardDraw draw);
	}

	private static final int DEBUG_OCCLUDED_PIXEL = new Color(255, 32, 32, 150).getRGB();

	private final BillboardOcclusionMask occlusionMask;
	private final DepthSurfaceFactory depthSurfaceFactory;
	private final Rectangle clipped = new Rectangle();
	private long skippedPixels;

	public long skippedPixels()
	{
		return skippedPixels;
	}
	private int[] sampleTransmittance = new int[0];
	private final BillboardProjectedQuad.Sample projectedSample = new BillboardProjectedQuad.Sample();
	private BufferedImage image;
	private int[] pixels = new int[0];
	private char[] paintOrder = new char[0];
	private BufferedImage maskSource;
	private BillboardDrawGeometry maskGeometry;
	private Rectangle maskRegion;
	private int maskCutoff;
	private int[] nearestX = new int[0];
	private int width;
	private int height;

	public BillboardFrameBuffer(
		BillboardOcclusionMask occlusionMask,
		DepthSurfaceFactory depthSurfaceFactory)
	{
		this.occlusionMask = occlusionMask;
		this.depthSurfaceFactory = depthSurfaceFactory;
	}

	public void begin(int width, int height)
	{
		skippedPixels = 0;
		ensureCapacity(width, height);
		Arrays.fill(pixels, 0, width * height, 0);
		Arrays.fill(paintOrder, 0, width * height, (char) 0);
	}

	public BufferedImage image()
	{
		return image;
	}

	private boolean isFullyOccluded(PreparedBillboardDraw draw, BillboardDepthSurface surface)
	{
		if (surface == null || !surface.supportsWorldOcclusion() || !occlusionMask.mightFullyCover(clipped))
			return false;
		BillboardProjectedQuad quad = draw.geometry == null ? null : draw.geometry.projectedQuad;
		double nearest;
		if (quad == null)
		{
			int height = draw.image.getHeight();
			nearest = Math.min(surface.depthAtRow(height, clipped.y), surface.depthAtRow(height, clipped.y + clipped.height - 1));
		}
		else
		{
			nearest = quad.nearestDepth();
		}
		return occlusionMask.fullyCovers(clipped, nearest, draw.occlusionIgnoredTileObject, draw.supportingGround);
	}

	public void blit(
		PreparedBillboardDraw draw,
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight,
		boolean drawOccludedPixels)
	{
		blit(draw, viewportX, viewportY, viewportWidth, viewportHeight, drawOccludedPixels, false, false);
	}

	public boolean blit(PreparedBillboardDraw draw, int viewportX, int viewportY, int viewportWidth,
		int viewportHeight, boolean debug, boolean skipBody, boolean proveOcclusion)
	{
		if (draw == null || draw.image == null || draw.bounds == null || draw.bounds.isEmpty()
			|| !(draw.image.getRaster().getDataBuffer() instanceof DataBufferInt))
			return false;

		boolean hidden = false;
		for (PreparedBillboardDraw layer = draw; layer != null; layer = layer.shadowDraw())
		{
			int left = Math.max(layer.bounds.x, viewportX);
			int top = Math.max(layer.bounds.y, viewportY);
			int right = Math.min(layer.bounds.x + layer.bounds.width, viewportX + viewportWidth);
			int bottom = Math.min(layer.bounds.y + layer.bounds.height, viewportY + viewportHeight);
			if (!debug) bottom = Math.min(bottom, layer.surfaceOcclusionRow);
			clipped.setBounds(left, top, right - left, bottom - top);
			boolean highlight = layer.highlightWidth > 0 && !(layer == draw && skipBody);
			if (clipped.isEmpty() && !highlight) continue;
			BillboardDepthSurface depth = occlusionMask.coveredCellCount() == 0 ? null : depthSurfaceFactory.create(layer);
			if (proveOcclusion && layer.highlightWidth <= 0 && isFullyOccluded(layer, depth))
			{
				skippedPixels += (long) clipped.width * clipped.height;
				if (layer == draw) hidden = true;
				continue;
			}
			if (layer == draw && skipBody) continue;
			if (!clipped.isEmpty()) blitLayer(layer, viewportX, viewportY, viewportWidth, debug, depth);
			if (highlight) blitHighlight(layer, viewportX, viewportY, depth);
		}
		return hidden;
	}

	private void blitLayer(PreparedBillboardDraw draw, int viewportX, int viewportY,
		int viewportWidth, boolean drawOccludedPixels, BillboardDepthSurface billboardDepth)
	{
		int clipLeft = clipped.x, clipRight = clipLeft + clipped.width;
		int clipTop = clipped.y, clipBottom = clipTop + clipped.height;
		int[] sourcePixels = ((DataBufferInt) draw.image.getRaster().getDataBuffer()).getData();
		int sourceWidth = draw.image.getWidth();
		int sourceHeight = draw.image.getHeight();
		BillboardDrawGeometry geometry = draw.geometry != null
			? draw.geometry
			: BillboardDrawGeometry.rectangular(draw.bounds);
		int drawWidth = geometry.unskewedBounds.width;
		BillboardProjectedQuad quad = geometry.projectedQuad;
		double nearestQuadDepth = quad == null ? Double.NaN : quad.nearestDepth();
		int drawPaintOrder = draw.paintOrder;
		TileObject ignoredTileObject = draw.occlusionIgnoredTileObject;
		boolean hasActiveOcclusion = billboardDepth != null && billboardDepth.supportsWorldOcclusion();
		int occlusionSampleStep = hasActiveOcclusion ? occlusionMask.sampleStep() : 0;
		if (occlusionSampleStep > 1 && sampleTransmittance.length < occlusionSampleStep)
		{
			sampleTransmittance = new int[occlusionSampleStep];
		}
		for (int y = clipTop; y < clipBottom; y++)
		{
			int sourceY = quad == null ? geometry.sourceYAt(y, sourceHeight) : 0;
			if (sourceY < 0 || sourceY >= sourceHeight)
			{
				continue;
			}
			int destinationRow = (y - viewportY) * viewportWidth;
			int sourceRow = sourceY * sourceWidth;
			// Translate whole pixel rows, preserving the same nearest-neighbour
			// sampling at every slope, including zero. Compute the shear once per row.
			int rowLeft = geometry.rowLeftAt(y);
			boolean depthReady = false;
			double rowDepth = Double.NaN;
			boolean belowSurface = y >= draw.surfaceOcclusionRow;
			boolean rowHasOcclusion = hasActiveOcclusion && occlusionMask.hasCoverageAt(y);
			BillboardOcclusionMask.CellResult cachedSampleResult = BillboardOcclusionMask.CellResult.VISIBLE;
			int cachedSampleX = Integer.MIN_VALUE;
			int cachedSampleStartX = 0;
			int endX = quad == null ? Math.min(clipRight, rowLeft + drawWidth) : clipRight;
			for (int x = quad == null ? Math.max(clipLeft, rowLeft) : clipLeft; x < endX; x++)
			{
				int destinationIndex = destinationRow + (x - viewportX);
				if (paintOrder[destinationIndex] > drawPaintOrder)
				{
					continue;
				}

				int sourceX;
				if (quad != null)
				{
					if (rowHasOcclusion && !drawOccludedPixels)
					{
						int sample = occlusionMask.sampleX(x);
						if (sample != cachedSampleX)
						{
							cachedSampleX = sample;
							if (occlusionMask.opaqueCell(sample, y, nearestQuadDepth, ignoredTileObject, draw.supportingGround))
							{
								int skip = Math.min(endX - x, occlusionSampleStep - occlusionMask.sampleOffset(x));
								skippedPixels += skip;
								x += skip - 1;
								continue;
							}
						}
					}
					if (!quad.sample(x + 0.5d, y + 0.5d, projectedSample))
					{
						continue;
					}
					sourceX = Math.min(sourceWidth - 1, (int) (projectedSample.u * sourceWidth));
					sourceY = Math.min(sourceHeight - 1, (int) (projectedSample.v * sourceHeight));
					sourceRow = sourceY * sourceWidth;
				}
				else
				{
					int rowX = x - rowLeft;
					sourceX = (int) (((long) rowX * sourceWidth) / drawWidth);
				}
				int sourcePixel = sourcePixels[sourceRow + sourceX];
				int sourceAlpha = (sourcePixel >>> 24) & 0xFF;
				if (sourceAlpha == 0)
				{
					continue;
				}
				if (belowSurface)
				{
					pixels[destinationIndex] = BillboardTriangleRasterizer.blendPixel(pixels[destinationIndex], DEBUG_OCCLUDED_PIXEL);
					continue;
				}

				if (rowHasOcclusion)
				{
					if (quad == null && !depthReady)
					{
						rowDepth = billboardDepth.depthAtRow(sourceHeight, y);
						depthReady = true;
					}
					double pixelDepth = quad != null ? projectedSample.depth : rowDepth;
					int transmittance;
					// A rotated plane varies in depth across the row. Do not reuse a
					// cell classification made at a different point on that plane.
					if (occlusionSampleStep > 1 && quad == null)
					{
						int sampleX = occlusionMask.sampleX(x);
						if (sampleX != cachedSampleX)
						{
							cachedSampleX = sampleX;
							cachedSampleResult = occlusionMask.classifySample(sampleX, y, pixelDepth, ignoredTileObject, draw.supportingGround);
							if (cachedSampleResult == BillboardOcclusionMask.CellResult.REFINE)
							{
								occlusionMask.refinedSampleTransmittance(sampleX, y, pixelDepth, sampleTransmittance, ignoredTileObject, draw.supportingGround);
								cachedSampleStartX = x - occlusionMask.sampleOffset(x);
							}
						}
						if (!drawOccludedPixels && cachedSampleResult == BillboardOcclusionMask.CellResult.OCCLUDED)
						{
							int skip = Math.min(endX - x, occlusionSampleStep - occlusionMask.sampleOffset(x));
							skippedPixels += skip;
							x += skip - 1;
							continue;
						}
						transmittance = cachedSampleResult == BillboardOcclusionMask.CellResult.OCCLUDED ? 0
							: cachedSampleResult == BillboardOcclusionMask.CellResult.REFINE
								? sampleTransmittance[x - cachedSampleStartX] : 255;
					}
					else
					{
						transmittance = occlusionMask.transmittanceAt(x, y, pixelDepth, ignoredTileObject, draw.supportingGround);
					}
					if (transmittance == 0)
					{
						if (drawOccludedPixels)
						{
							pixels[destinationIndex] = BillboardTriangleRasterizer.blendPixel(pixels[destinationIndex], DEBUG_OCCLUDED_PIXEL);
						}
						continue;
					}
					if (transmittance < 255)
					{
						if (drawOccludedPixels)
						{
							pixels[destinationIndex] = BillboardTriangleRasterizer.blendPixel(
								pixels[destinationIndex], debugOcclusionPixel(transmittance));
						}
						sourcePixel = occlusionMask.tintPixel(x, y, pixelDepth, sourcePixel, ignoredTileObject, draw.supportingGround);
					}
				}

				// Draws arrive nearest-to-farthest. The accumulated destination is therefore
				// in front of this source pixel and must be composited over it. Reversing the
				// blend arguments preserves transparent foreground layers instead of allowing
				// later, farther pixels to draw on top of or replace them.
				pixels[destinationIndex] = BillboardTriangleRasterizer.blendPixel(sourcePixel, pixels[destinationIndex]);
				if (sourceAlpha == 0xFF)
				{
					paintOrder[destinationIndex] = (char) drawPaintOrder;
				}
			}
		}
	}

	/** For each row, remember the nearest opaque sprite pixel to each column.
	 * Searching the neighbouring rows then gives the exact screen-space distance
	 * and the surface depth of the closest silhouette point. */
	private void blitHighlight(PreparedBillboardDraw draw, int viewportX, int viewportY, BillboardDepthSurface depth)
	{
		int radius = draw.highlightWidth;
		Rectangle viewport = new Rectangle(viewportX, viewportY, width, height);
		Rectangle region = new Rectangle(draw.bounds);
		region.grow(radius, radius);
		Rectangle output = viewport.intersection(region);
		output.height = Math.min(output.height, (int) Math.min(Integer.MAX_VALUE, (long) draw.surfaceOcclusionRow - output.y));
		if (output.isEmpty()) return;
		viewport.grow(radius, radius);
		region = region.intersection(viewport);
		int stride = region.width;
		BillboardDrawGeometry geometry = draw.geometry != null ? draw.geometry : BillboardDrawGeometry.rectangular(draw.bounds);
		if (draw.image != maskSource || draw.surfaceOcclusionRow != maskCutoff
			|| !region.equals(maskRegion) || !geometry.sameSampling(maskGeometry))
		{
			prepareHighlightMask(draw, geometry, region);
			maskSource = draw.image;
			maskCutoff = draw.surfaceOcclusionRow;
			maskRegion = region;
			maskGeometry = geometry;
		}
		int sourceHeight = draw.image.getHeight();
		double fade = draw.highlightFeather * 0.25 * (radius - 0.5);
		for (int y = output.y; y < output.y + output.height; y++)
		{
			int row = y - region.y;
			for (int x = output.x; x < output.x + output.width; x++)
			{
				int col = x - region.x, index = (y - viewportY) * width + x - viewportX;
				if (nearestX[row * stride + col] == col || paintOrder[index] > draw.paintOrder) continue;
				int best = radius * radius + 1, seedX = 0, seedY = 0;
				for (int ry = Math.max(0, row - radius); ry <= Math.min(region.height - 1, row + radius); ry++)
				{
					int nx = nearestX[ry * stride + col], dx = nx - col, dy = ry - row;
					if (Math.abs(dx) > radius) continue;
					int distance = dx * dx + dy * dy;
					if (distance < best)
					{
						best = distance;
						seedX = region.x + nx;
						seedY = region.y + ry;
					}
				}
				if (best > radius * radius) continue;
				int alpha = draw.highlight.getAlpha();
				if (fade > 0) alpha = (int) Math.round(alpha * Math.min(1, (radius + 0.5 - Math.sqrt(best)) / fade));
				int pixel = (draw.highlight.getRGB() & 0xffffff) | alpha << 24;
				if (depth != null && depth.supportsWorldOcclusion() && occlusionMask.hasCoverageAt(y))
				{
					double z = geometry.projectedQuad != null && geometry.projectedQuad.sample(seedX + 0.5, seedY + 0.5, projectedSample)
						? projectedSample.depth : depth.depthAtRow(sourceHeight, seedY);
					int transmission = occlusionMask.transmittanceAt(x, y, z, draw.occlusionIgnoredTileObject, draw.supportingGround);
					if (transmission == 0) continue;
					if (transmission < 255) pixel = occlusionMask.tintPixel(x, y, z, pixel, draw.occlusionIgnoredTileObject, draw.supportingGround);
				}
				pixels[index] = BillboardTriangleRasterizer.blendPixel(pixel, pixels[index]);
				if (pixel >>> 24 == 255) paintOrder[index] = (char) draw.paintOrder;
			}
		}
	}

	private void prepareHighlightMask(PreparedBillboardDraw draw, BillboardDrawGeometry geometry, Rectangle region)
	{
		int stride = region.width;
		int size = stride * region.height;
		if (nearestX.length < size) nearestX = new int[size];
		int sourceWidth = draw.image.getWidth(), sourceHeight = draw.image.getHeight();
		int[] source = ((DataBufferInt) draw.image.getRaster().getDataBuffer()).getData();
		for (int row = 0; row < region.height; row++)
		{
			int y = region.y + row, offset = row * stride, nearest = Integer.MIN_VALUE / 2;
			for (int x = 0; x < stride; x++)
			{
				int canvasX = region.x + x;
				int sx, sy;
				if (geometry.projectedQuad != null && geometry.projectedQuad.sample(canvasX + 0.5, y + 0.5, projectedSample))
				{
					sx = Math.min(sourceWidth - 1, (int) (projectedSample.u * sourceWidth));
					sy = Math.min(sourceHeight - 1, (int) (projectedSample.v * sourceHeight));
				}
				else if (geometry.projectedQuad == null)
				{
					sx = geometry.sourceXAt(canvasX, y, sourceWidth);
					sy = geometry.sourceYAt(y, sourceHeight);
				}
				else sx = sy = -1;
				if (sx >= 0 && sy >= 0 && y < draw.surfaceOcclusionRow && source[sy * sourceWidth + sx] >>> 24 != 0) nearest = x;
				nearestX[offset + x] = nearest;
			}
			nearest = Integer.MAX_VALUE / 2;
			for (int x = stride - 1; x >= 0; x--)
			{
				int left = nearestX[offset + x];
				if (left == x) nearest = x;
				else if (nearest - x < x - left) nearestX[offset + x] = nearest;
			}
		}
	}

	public void clearHighlight()
	{
		maskSource = null;
		maskGeometry = null;
		maskRegion = null;
		nearestX = new int[0];
	}

	private static int debugOcclusionPixel(int transmittance)
	{
		int alpha = ((DEBUG_OCCLUDED_PIXEL >>> 24) & 0xFF) * (255 - transmittance) / 255;
		return (DEBUG_OCCLUDED_PIXEL & 0x00FFFFFF) | (alpha << 24);
	}

	private void ensureCapacity(int width, int height)
	{
		if (image != null && this.width == width && this.height == height)
		{
			return;
		}
		image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		paintOrder = new char[width * height];
		this.width = width;
		this.height = height;
	}

}
