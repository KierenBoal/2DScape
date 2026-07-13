package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;

import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class BillboardOcclusionDebugSampler
{
	private static final int SAMPLE_LIMIT = 24;
	private static final double[] X_FRACTIONS = {0.25d, 0.5d, 0.75d};
	private static final double[] Y_FRACTIONS = {0.25d, 0.5d, 0.75d, 0.9d};

	private final BillboardOcclusionMask occlusionMask;
	private final Function<PreparedBillboardDraw, BillboardDepthSurface> depthSurfaceFactory;
	private final List<String> samples = new ArrayList<>();

	public BillboardOcclusionDebugSampler(
		BillboardOcclusionMask occlusionMask,
		Function<PreparedBillboardDraw, BillboardDepthSurface> depthSurfaceFactory)
	{
		this.occlusionMask = occlusionMask;
		this.depthSurfaceFactory = depthSurfaceFactory;
	}

	public void clear()
	{
		samples.clear();
	}

	public List<String> samples()
	{
		return samples;
	}

	public void collect(List<PreparedBillboardDraw> draws, boolean enabled)
	{
		if (!enabled || draws == null || draws.isEmpty())
		{
			return;
		}
		for (PreparedBillboardDraw draw : draws)
		{
			if (samples.size() >= SAMPLE_LIMIT)
			{
				return;
			}
			collect(draw);
		}
	}

	private void collect(PreparedBillboardDraw draw)
	{
		if (draw == null || draw.image == null || draw.bounds == null || draw.bounds.isEmpty()
			|| !(draw.image.getRaster().getDataBuffer() instanceof DataBufferInt))
		{
			return;
		}

		int sourceWidth = draw.image.getWidth();
		int sourceHeight = draw.image.getHeight();
		int[] sourcePixels = ((DataBufferInt) draw.image.getRaster().getDataBuffer()).getData();
		BillboardDepthSurface depthSurface = depthSurfaceFactory.apply(draw);
		for (double yFraction : Y_FRACTIONS)
		{
			for (double xFraction : X_FRACTIONS)
			{
				if (samples.size() >= SAMPLE_LIMIT)
				{
					return;
				}

				int canvasX = draw.bounds.x + clampSample(draw.bounds.width, xFraction);
				int canvasY = draw.bounds.y + clampSample(draw.bounds.height, yFraction);
				int sourceX = Math.max(0, Math.min(sourceWidth - 1, ((canvasX - draw.bounds.x) * sourceWidth) / draw.bounds.width));
				int sourceY = Math.max(0, Math.min(sourceHeight - 1, ((canvasY - draw.bounds.y) * sourceHeight) / draw.bounds.height));
				int sourceAlpha = (sourcePixels[(sourceY * sourceWidth) + sourceX] >>> 24) & 0xFF;
				if (sourceAlpha == 0)
				{
					continue;
				}

				BillboardDepthSurface.DebugPoint point = depthSurface.debugPointAt(sourceX, sourceY, sourceWidth, sourceHeight, canvasY);
				float worldDepth = occlusionMask.depthAt(canvasX, canvasY);
				float bias = occlusionMask.occlusionDepthBias();
				double margin = point.depth - (worldDepth + bias);
				samples.add(format(
					draw, canvasX, canvasY, sourceX, sourceY, sourceAlpha, point,
					worldDepth, occlusionMask.sourceAt(canvasX, canvasY), bias, margin,
					occlusionMask.isOccluded(canvasX, canvasY, point.depth)));
			}
		}
	}

	private static int clampSample(int size, double fraction)
	{
		return Math.max(0, Math.min(size - 1, (int) Math.round((size - 1) * fraction)));
	}

	private static String format(
		PreparedBillboardDraw draw,
		int canvasX,
		int canvasY,
		int sourceX,
		int sourceY,
		int sourceAlpha,
		BillboardDepthSurface.DebugPoint point,
		float worldDepth,
		String worldSource,
		float bias,
		double worldDepthMargin,
		boolean occluded)
	{
		BillboardRenderRequest request = draw.request;
		String target = request != null && request.renderable != null ? request.renderable.getClass().getSimpleName() : "-";
		return String.format(
			Locale.ROOT,
			"{target=%s canvas=(%d,%d) src=(%d,%d) alpha=%d draw=%s sourceBounds=%s modelH=%d local=(%d,%d) h=%s xOff=%s zOff=%s billboardDepth=%s worldDepth=%s worldSource=%s bias=%s worldDepthMargin=%s occluded=%s}",
			target, canvasX, canvasY, sourceX, sourceY, sourceAlpha, draw.bounds, draw.sourceBounds,
			request != null && request.renderable != null ? request.renderable.getModelHeight() : -1,
			point.localX, point.localY, decimal(point.height), decimal(point.horizontalOffset),
			decimal(point.verticalOffset), decimal(point.depth), decimal(worldDepth),
			worldSource == null ? "-" : worldSource, decimal(bias), decimal(worldDepthMargin), occluded);
	}

	private static String decimal(double value)
	{
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "NaN";
	}
}
