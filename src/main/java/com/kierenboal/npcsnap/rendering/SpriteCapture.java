package com.kierenboal.npcsnap.rendering;

import java.awt.image.BufferedImage;

/** Decorated sprite and an independent shadow sampled from pristine alpha. */
public final class SpriteCapture
{
	public final BufferedImage image;
	public final SpriteShadowMask shadow;
	public SpriteCapture(BufferedImage image, SpriteShadowMask shadow)
	{
		this.image = image;
		this.shadow = shadow;
	}
}
