package com.kierenboal.npcsnap.rendering;

public final class BillboardCanvasPoint
{
	public final double x;
	public final double y;
	public final double depth;
	// Screen-space world-up derivative, divided by scale / depth.
	public final double verticalRight;
	public final double verticalUp;

	public BillboardCanvasPoint(double x, double y, double depth, double verticalRight, double verticalUp)
	{
		this.x = x;
		this.y = y;
		this.depth = depth;
		this.verticalRight = verticalRight;
		this.verticalUp = verticalUp;
	}
}
