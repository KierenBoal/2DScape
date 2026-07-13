package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.coords.LocalPoint;

public final class BillboardDepthSurface
{
	private static final BillboardDepthSurface INVALID = new BillboardDepthSurface(null, 0, 0, Double.NaN, 0, null, null);

	private final BillboardDepthCalculator depthCalculator;
	private final int baseLocalX;
	private final int baseLocalY;
	private final double baseHeight;
	private final int modelHeight;
	private final Rectangle sourceBounds;
	private final Rectangle drawBounds;

	public BillboardDepthSurface(
		BillboardDepthCalculator depthCalculator,
		int baseLocalX,
		int baseLocalY,
		double baseHeight,
		int modelHeight,
		Rectangle sourceBounds,
		Rectangle drawBounds,
		int relativeYaw,
		int relativePitch)
	{
		this(
			depthCalculator,
			baseLocalX,
			baseLocalY,
			baseHeight,
			Math.max(0, modelHeight),
			sourceBounds != null ? new Rectangle(sourceBounds) : null,
			drawBounds != null ? new Rectangle(drawBounds) : null
		);
	}

	private BillboardDepthSurface(
		BillboardDepthCalculator depthCalculator,
		int baseLocalX,
		int baseLocalY,
		double baseHeight,
		int modelHeight,
		Rectangle sourceBounds,
		Rectangle drawBounds)
	{
		this.depthCalculator = depthCalculator;
		this.baseLocalX = baseLocalX;
		this.baseLocalY = baseLocalY;
		this.baseHeight = baseHeight;
		this.modelHeight = modelHeight;
		this.sourceBounds = sourceBounds;
		this.drawBounds = drawBounds;
	}

	public static BillboardDepthSurface invalid()
	{
		return INVALID;
	}

	public static BillboardDepthSurface from(BillboardDepthCalculator depthCalculator, BillboardRenderRequest request, Rectangle sourceBounds, Rectangle drawBounds, double baseHeight)
	{
		if (depthCalculator == null || request == null || request.localPoint == null || request.renderable == null || sourceBounds == null
			|| sourceBounds.width <= 0 || sourceBounds.height <= 0 || drawBounds == null || drawBounds.width <= 0 || drawBounds.height <= 0 || !Double.isFinite(baseHeight))
		{
			return invalid();
		}

		LocalPoint localPoint = request.localPoint;
		return new BillboardDepthSurface(
			depthCalculator,
			localPoint.getX(),
			localPoint.getY(),
			baseHeight + request.verticalOffset,
			request.renderable.getModelHeight(),
			sourceBounds,
			drawBounds,
			request.relativeYaw,
			request.relativePitch
		);
	}

	public double depthAt(int sourceX, int sourceY, int sourceWidth, int sourceHeight, int canvasY)
	{
		DebugPoint point = debugPointAt(sourceX, sourceY, sourceWidth, sourceHeight, canvasY);
		return point.depth;
	}

	public double depthAtRow(int sourceY, int sourceHeight, int canvasY)
	{
		if (depthCalculator == null || sourceBounds == null || drawBounds == null || sourceHeight <= 0 || modelHeight <= 0 || !Double.isFinite(baseHeight))
		{
			return Double.NaN;
		}

		double verticalOffset = verticalOffset(sourceY, sourceHeight);
		if (!Double.isFinite(verticalOffset))
		{
			return Double.NaN;
		}

		return depthCalculator.cameraForwardDepth(baseLocalX, baseLocalY, baseHeight + verticalOffset);
	}

	public DebugPoint debugPointAt(int sourceX, int sourceY, int sourceWidth, int sourceHeight, int canvasY)
	{
		if (depthCalculator == null || sourceBounds == null || drawBounds == null || sourceWidth <= 0 || sourceHeight <= 0 || modelHeight <= 0 || !Double.isFinite(baseHeight))
		{
			return DebugPoint.invalid();
		}

		double spriteX = sourceBounds.x + (((sourceX + 0.5d) * sourceBounds.width) / sourceWidth);
		double horizontalOffset = spriteX;
		double verticalOffset = verticalOffset(sourceY, sourceHeight);
		if (!Double.isFinite(horizontalOffset) || !Double.isFinite(verticalOffset))
		{
			return DebugPoint.invalid();
		}

		int localX = baseLocalX;
		int localY = baseLocalY;
		double height = baseHeight + verticalOffset;
		double depth = depthCalculator.cameraForwardDepth(localX, localY, height);
		return new DebugPoint(localX, localY, height, horizontalOffset, verticalOffset, depth);
	}

	public double depthAt(int sourceX, int sourceY, int sourceWidth, int sourceHeight)
	{
		int canvasY = drawBounds != null
			? drawBounds.y + (int) Math.round(((sourceY + 0.5d) * drawBounds.height) / Math.max(1.0d, sourceHeight))
			: 0;
		return depthAt(sourceX, sourceY, sourceWidth, sourceHeight, canvasY);
	}

	private double verticalOffset(int sourceY, int sourceHeight)
	{
		double t = Math.max(0.0d, Math.min(1.0d, (sourceY + 0.5d) / Math.max(1.0d, sourceHeight)));
		return modelHeight * (1.0d - t);
	}

	public static final class DebugPoint
	{
		public final int localX;
		public final int localY;
		public final double height;
		public final double horizontalOffset;
		public final double verticalOffset;
		public final double depth;

		private DebugPoint(int localX, int localY, double height, double horizontalOffset, double verticalOffset, double depth)
		{
			this.localX = localX;
			this.localY = localY;
			this.height = height;
			this.horizontalOffset = horizontalOffset;
			this.verticalOffset = verticalOffset;
			this.depth = depth;
		}

		private static DebugPoint invalid()
		{
			return new DebugPoint(0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		}
	}
}
