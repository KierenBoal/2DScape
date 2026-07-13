package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public final class PreparedBillboardDraw
{
	public final BillboardRenderRequest request;
	public final BillboardRenderResult result;
	public final int paintOrder;
	public final BufferedImage image;
	public final Rectangle bounds;
	public final Rectangle sourceBounds;

	public PreparedBillboardDraw(BillboardRenderRequest request, BillboardRenderResult result, int paintOrder)
	{
		this.request = request;
		this.result = result;
		this.paintOrder = paintOrder;
		this.image = result != null ? result.image : null;
		this.bounds = result != null ? result.bounds : null;
		this.sourceBounds = result != null ? result.sourceBounds : null;
	}
}

