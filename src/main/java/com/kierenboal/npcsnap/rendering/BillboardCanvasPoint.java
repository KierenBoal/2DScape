package com.kierenboal.npcsnap.rendering;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class BillboardCanvasPoint
{
	public final double x;
	public final double y;
	public final double depth;
	// Screen-space world-up derivative, divided by scale / depth.
	public final double verticalRight;
	public final double verticalUp;

}
