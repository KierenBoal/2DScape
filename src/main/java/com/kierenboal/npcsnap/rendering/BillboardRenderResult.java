package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public final class BillboardRenderResult
{
	public final Rectangle bounds;
	public final BufferedImage image;
	public final Rectangle sourceBounds;

	public BillboardRenderResult(Rectangle bounds, BufferedImage image, Rectangle sourceBounds)
	{
		this.bounds = bounds;
		this.image = image;
		this.sourceBounds = sourceBounds;
	}
}

