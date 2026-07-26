package com.kierenboal.npcsnap.export;

import java.awt.image.BufferedImage;

public final class BillboardExportFrame
{
	public final int animationFrame;
	public final int pitch;
	public final int yaw;
	public final BufferedImage image;

	public BillboardExportFrame(int animationFrame, int pitch, int yaw, BufferedImage image)
	{
		this.animationFrame = animationFrame;
		this.pitch = pitch;
		this.yaw = yaw;
		this.image = image;
	}
}
