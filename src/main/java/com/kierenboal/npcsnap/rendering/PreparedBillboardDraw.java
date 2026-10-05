package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.TileObject;

public final class PreparedBillboardDraw
{
	public final BillboardRenderRequest request;
	public final BillboardRenderResult result;
	public final int paintOrder;
	public final BufferedImage image;
	public final Rectangle bounds;
	public final Rectangle sourceBounds;
	public final BillboardDrawGeometry geometry;
	public final TileObject occlusionIgnoredTileObject;
	public final int surfaceOcclusionRow;
	public final int modelHeight;
	public final boolean supportsWorldOcclusion;

	public PreparedBillboardDraw(BillboardRenderRequest request, BillboardRenderResult result, int paintOrder)
	{
		this(request, result, paintOrder, null);
	}

	public PreparedBillboardDraw(
		BillboardRenderRequest request,
		BillboardRenderResult result,
		int paintOrder,
		TileObject occlusionIgnoredTileObject)
	{
		this(request, result, paintOrder, occlusionIgnoredTileObject, Integer.MAX_VALUE);
	}

	public PreparedBillboardDraw(
		BillboardRenderRequest request,
		BillboardRenderResult result,
		int paintOrder,
		TileObject occlusionIgnoredTileObject,
		int surfaceOcclusionRow)
	{
		this.request = request;
		this.result = result;
		this.paintOrder = paintOrder;
		this.image = result != null ? result.image : null;
		this.bounds = result != null ? result.bounds : null;
		this.sourceBounds = result != null ? result.sourceBounds : null;
		this.geometry = result != null ? result.geometry : null;
		this.occlusionIgnoredTileObject = occlusionIgnoredTileObject;
		this.surfaceOcclusionRow = surfaceOcclusionRow;
		// Animated models can reuse their model and vertex buffers on the next
		// getModel() call. Capture these scalars before preparing other entities
		// or gathering scenery; compositing must not reclassify overwritten data.
		this.modelHeight = BillboardDepthSurface.modelHeight(request);
		this.supportsWorldOcclusion = modelHeight > 0 && BillboardDepthSurface.supportsWorldOcclusion(request);
	}
}

