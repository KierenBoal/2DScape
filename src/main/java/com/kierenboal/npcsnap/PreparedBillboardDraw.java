package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

final class PreparedBillboardDraw
{
	final BillboardRenderRequest request;
	final BillboardRenderResult result;
	final int paintOrder;
	final BufferedImage image;
	final Rectangle bounds;
	final Rectangle sourceBounds;

	PreparedBillboardDraw(BillboardRenderRequest request, BillboardRenderResult result, int paintOrder)
	{
		this.request = request;
		this.result = result;
		this.paintOrder = paintOrder;
		this.image = result != null ? result.image : null;
		this.bounds = result != null ? result.bounds : null;
		this.sourceBounds = result != null ? result.sourceBounds : null;
	}
}

