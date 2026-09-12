package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionMask;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

public final class BillboardFrameBuffer
{
	public interface DepthSurfaceFactory
	{
		BillboardDepthSurface create(PreparedBillboardDraw draw);
	}

	private static final int DEBUG_OCCLUDED_PIXEL = new Color(255, 32, 32, 150).getRGB();

	private final BillboardOcclusionMask occlusionMask;
	private final BillboardPerformanceMetrics performanceMetrics;
	private final DepthSurfaceFactory depthSurfaceFactory;
	private boolean[] rowHasDepth = new boolean[0];
	private double[] rowDepth = new double[0];
	private BufferedImage image;
	private int[] pixels = new int[0];
	private char[] paintOrder = new char[0];
	private int width;
	private int height;

	public BillboardFrameBuffer(
		BillboardOcclusionMask occlusionMask,
		BillboardPerformanceMetrics performanceMetrics,
		DepthSurfaceFactory depthSurfaceFactory)
	{
		this.occlusionMask = occlusionMask;
		this.performanceMetrics = performanceMetrics;
		this.depthSurfaceFactory = depthSurfaceFactory;
	}

	public void begin(int width, int height)
	{
		ensureCapacity(width, height);
		Arrays.fill(pixels, 0, width * height, 0);
		Arrays.fill(paintOrder, 0, width * height, (char) 0);
	}

	public BufferedImage image()
	{
		return image;
	}

	public void blit(
		PreparedBillboardDraw draw,
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight,
		boolean drawOccludedPixels)
	{
		if (draw == null || draw.image == null || draw.bounds == null || draw.bounds.isEmpty()
			|| !(draw.image.getRaster().getDataBuffer() instanceof DataBufferInt))
		{
			return;
		}

		int clipLeft = Math.max(draw.bounds.x, viewportX);
		int clipTop = Math.max(draw.bounds.y, viewportY);
		int clipRight = Math.min(draw.bounds.x + draw.bounds.width, viewportX + viewportWidth);
		int clipBottom = Math.min(draw.bounds.y + draw.bounds.height, viewportY + viewportHeight);
		if (clipLeft >= clipRight || clipTop >= clipBottom)
		{
			return;
		}

		int[] sourcePixels = ((DataBufferInt) draw.image.getRaster().getDataBuffer()).getData();
		int sourceWidth = draw.image.getWidth();
		int sourceHeight = draw.image.getHeight();
		BillboardDrawGeometry geometry = draw.geometry != null
			? draw.geometry
			: BillboardDrawGeometry.rectangular(draw.bounds);
		int drawWidth = geometry.unskewedBounds.width;
		int drawPaintOrder = draw.paintOrder;
		BillboardDepthSurface billboardDepth = occlusionMask.coveredCellCount() > 0 ? depthSurfaceFactory.create(draw) : null;
		boolean hasActiveOcclusion = billboardDepth != null && billboardDepth.supportsWorldOcclusion();
		int clippedHeight = clipBottom - clipTop;
		if (hasActiveOcclusion)
		{
			ensureRowCapacity(clippedHeight);
			Arrays.fill(rowHasDepth, 0, clippedHeight, false);
		}

		long occlusionElapsedNanos = 0L;
		boolean measureOcclusion = performanceMetrics.isEnabled() && hasActiveOcclusion;
		int occlusionSampleStep = hasActiveOcclusion ? occlusionMask.sampleStep() : 0;
		for (int y = clipTop; y < clipBottom; y++)
		{
			int sourceY = geometry.sourceYAt(y, sourceHeight);
			if (sourceY < 0 || sourceY >= sourceHeight)
			{
				continue;
			}
			int destinationRow = (y - viewportY) * viewportWidth;
			int sourceRow = sourceY * sourceWidth;
			// Translate whole pixel rows, preserving the same nearest-neighbour
			// sampling at every slope, including zero. Compute the shear once per row.
			int rowLeft = geometry.rowLeftAt(y);
			int clippedRow = y - clipTop;
			boolean rowHasOcclusion = hasActiveOcclusion && occlusionMask.hasCoverageAt(y);
			long rowOcclusionStart = measureOcclusion && rowHasOcclusion ? System.nanoTime() : 0L;
			BillboardOcclusionMask.CellResult cachedSampleResult = BillboardOcclusionMask.CellResult.VISIBLE;
			int cachedSampleX = Integer.MIN_VALUE;
			int cachedSampleBits = 0;
			int cachedSampleStartX = 0;
			for (int x = clipLeft; x < clipRight; x++)
			{
				int destinationIndex = destinationRow + (x - viewportX);
				if (paintOrder[destinationIndex] > drawPaintOrder)
				{
					continue;
				}

				int rowX = x - rowLeft;
				if (rowX < 0 || rowX >= drawWidth)
				{
					continue;
				}
				int sourceX = (int) (((long) rowX * sourceWidth) / drawWidth);
				int sourcePixel = sourcePixels[sourceRow + sourceX];
				int sourceAlpha = (sourcePixel >>> 24) & 0xFF;
				if (sourceAlpha == 0)
				{
					continue;
				}

				if (rowHasOcclusion)
				{
					if (!rowHasDepth[clippedRow])
					{
						rowDepth[clippedRow] = billboardDepth.depthAtRow(sourceY, sourceHeight, y);
						rowHasDepth[clippedRow] = true;
					}
					double pixelDepth = rowDepth[clippedRow];
					boolean occluded;
					if (occlusionSampleStep > 1)
					{
						int sampleX = occlusionMask.sampleX(x);
						if (sampleX != cachedSampleX)
						{
							cachedSampleX = sampleX;
							cachedSampleResult = occlusionMask.classifySample(sampleX, y, pixelDepth);
							if (cachedSampleResult == BillboardOcclusionMask.CellResult.REFINE)
							{
								cachedSampleBits = occlusionMask.refinedSampleBits(sampleX, y, pixelDepth);
								cachedSampleStartX = x - occlusionMask.sampleOffset(x);
							}
						}
						occluded = cachedSampleResult == BillboardOcclusionMask.CellResult.OCCLUDED
							|| (cachedSampleResult == BillboardOcclusionMask.CellResult.REFINE
								&& (cachedSampleBits & (1 << (x - cachedSampleStartX))) != 0);
					}
					else
					{
						occluded = occlusionMask.isOccluded(x, y, pixelDepth);
					}
					if (occluded)
					{
						if (drawOccludedPixels)
						{
							pixels[destinationIndex] = BillboardTriangleRasterizer.blendPixel(pixels[destinationIndex], DEBUG_OCCLUDED_PIXEL);
						}
						continue;
					}
				}

				// Draws arrive nearest-to-farthest. The accumulated destination is therefore
				// in front of this source pixel and must be composited over it. Reversing the
				// blend arguments preserves transparent foreground layers instead of allowing
				// later, farther pixels to draw on top of or replace them.
				pixels[destinationIndex] = BillboardTriangleRasterizer.blendPixel(sourcePixel, pixels[destinationIndex]);
				if (((pixels[destinationIndex] >>> 24) & 0xFF) == 0xFF)
				{
					paintOrder[destinationIndex] = (char) drawPaintOrder;
				}
			}
			if (rowOcclusionStart > 0L)
			{
				occlusionElapsedNanos += System.nanoTime() - rowOcclusionStart;
			}
		}
		if (occlusionElapsedNanos > 0L)
		{
			performanceMetrics.addElapsed("Per-pixel occlusion checks", occlusionElapsedNanos);
		}
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

	private void ensureRowCapacity(int requiredHeight)
	{
		if (rowHasDepth.length < requiredHeight)
		{
			rowHasDepth = new boolean[requiredHeight];
			rowDepth = new double[requiredHeight];
		}
	}
}
