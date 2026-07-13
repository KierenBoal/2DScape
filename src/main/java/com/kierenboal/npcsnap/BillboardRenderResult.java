package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

final class BillboardRenderResult
{
	final Rectangle bounds;
	final BufferedImage image;
	final Rectangle sourceBounds;

	BillboardRenderResult(Rectangle bounds, BufferedImage image, Rectangle sourceBounds)
	{
		this.bounds = bounds;
		this.image = image;
		this.sourceBounds = sourceBounds;
	}
}

