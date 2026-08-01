package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.Model;
import net.runelite.api.coords.LocalPoint;

public final class BillboardDepthSurface
{
	private static final BillboardDepthSurface INVALID = new BillboardDepthSurface(null, 0, 0, Double.NaN, 0, false, null, null);

	private final BillboardDepthCalculator depthCalculator;
	private final int baseLocalX;
	private final int baseLocalY;
	private final double baseHeight;
	private final int modelHeight;
	private final boolean supportsWorldOcclusion;
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
			modelHeight > 0,
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
		boolean supportsWorldOcclusion,
		Rectangle sourceBounds,
		Rectangle drawBounds)
	{
		this.depthCalculator = depthCalculator;
		this.baseLocalX = baseLocalX;
		this.baseLocalY = baseLocalY;
		this.baseHeight = baseHeight;
		this.modelHeight = modelHeight;
		this.supportsWorldOcclusion = supportsWorldOcclusion;
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
			supportsVerticalPlaneOcclusion(request.model),
			sourceBounds,
			drawBounds
		);
	}

	public boolean supportsWorldOcclusion()
	{
		return supportsWorldOcclusion;
	}

	static boolean supportsVerticalPlaneOcclusion(Model model)
	{
		if (model == null || model.getVerticesCount() <= 0)
		{
			return false;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		if (verticesX == null || verticesY == null || verticesZ == null)
		{
			return false;
		}

		int count = Math.min(model.getVerticesCount(), Math.min(verticesX.length, Math.min(verticesY.length, verticesZ.length)));
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < count; i++)
		{
			if (!Float.isFinite(verticesX[i]) || !Float.isFinite(verticesY[i]) || !Float.isFinite(verticesZ[i]))
			{
				continue;
			}
			minX = Math.min(minX, verticesX[i]);
			minY = Math.min(minY, verticesY[i]);
			minZ = Math.min(minZ, verticesZ[i]);
			maxX = Math.max(maxX, verticesX[i]);
			maxY = Math.max(maxY, verticesY[i]);
			maxZ = Math.max(maxZ, verticesZ[i]);
		}

		if (!Float.isFinite(minX))
		{
			return false;
		}

		double verticalSpan = maxY - minY;
		double horizontalSpan = Math.max(maxX - minX, maxZ - minZ);
		return verticalSpan >= Math.max(2.0d, horizontalSpan * 0.1d);
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

		return depthCalculator.cameraForwardDepthOnVerticalPlane(baseLocalX, baseLocalY, canvasY);
	}

	public DebugPoint debugPointAt(int sourceX, int sourceY, int sourceWidth, int sourceHeight, int canvasY)
	{
		if (depthCalculator == null || sourceBounds == null || drawBounds == null || sourceWidth <= 0 || sourceHeight <= 0 || modelHeight <= 0 || !Double.isFinite(baseHeight))
		{
			return DebugPoint.invalid();
		}

		double spriteX = sourceBounds.x + (((sourceX + 0.5d) * sourceBounds.width) / sourceWidth);
		double horizontalOffset = spriteX;
		if (!Double.isFinite(horizontalOffset))
		{
			return DebugPoint.invalid();
		}

		int localX = baseLocalX;
		int localY = baseLocalY;
		double depth = depthCalculator.cameraForwardDepthOnVerticalPlane(localX, localY, canvasY);
		return new DebugPoint(localX, localY, baseHeight, horizontalOffset, 0.0d, depth);
	}

	public double depthAt(int sourceX, int sourceY, int sourceWidth, int sourceHeight)
	{
		int canvasY = drawBounds != null
			? drawBounds.y + (int) Math.round(((sourceY + 0.5d) * drawBounds.height) / Math.max(1.0d, sourceHeight))
			: 0;
		return depthAt(sourceX, sourceY, sourceWidth, sourceHeight, canvasY);
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
