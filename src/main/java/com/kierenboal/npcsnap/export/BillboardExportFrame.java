package com.kierenboal.npcsnap.export;

import java.awt.image.BufferedImage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class BillboardExportFrame
{
	public final int animationFrame;
	public final int pitch;
	public final int yaw;
	public final BufferedImage image;
}
