package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public final class BillboardRenderResult
{
	public final Rectangle bounds;
	public final BufferedImage image;
	public final Rectangle sourceBounds;
	public final BillboardDrawGeometry geometry;

	public BillboardRenderResult(Rectangle bounds, BufferedImage image, Rectangle sourceBounds)
	{
		this(BillboardDrawGeometry.rectangular(bounds), image, sourceBounds);
	}

	public BillboardRenderResult(BillboardDrawGeometry geometry, BufferedImage image, Rectangle sourceBounds)
	{
		this.geometry = geometry;
		this.bounds = geometry != null ? geometry.bounds : null;
		this.image = image;
		this.sourceBounds = sourceBounds;
	}
}

