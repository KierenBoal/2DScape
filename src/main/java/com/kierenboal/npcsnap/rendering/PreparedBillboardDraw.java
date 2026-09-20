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
		this.request = request;
		this.result = result;
		this.paintOrder = paintOrder;
		this.image = result != null ? result.image : null;
		this.bounds = result != null ? result.bounds : null;
		this.sourceBounds = result != null ? result.sourceBounds : null;
		this.geometry = result != null ? result.geometry : null;
		this.occlusionIgnoredTileObject = occlusionIgnoredTileObject;
	}
}

