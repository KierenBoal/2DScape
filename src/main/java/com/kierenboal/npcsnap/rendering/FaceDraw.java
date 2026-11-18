package com.kierenboal.npcsnap.rendering;

import java.awt.Color;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class FaceDraw
{
	public final int x0;
	public final int y0;
	public final int x1;
	public final int y1;
	public final int x2;
	public final int y2;
	private final Color color;
	private final double depth;
	private final TextureSample textureSample;
	private final TextureUvs textureUvs;

	public Color getColor()
	{
		return color;
	}

	public double getDepth()
	{
		return depth;
	}

	public boolean isTextured()
	{
		return textureSample != null && textureUvs != null;
	}

	public TextureSample getTextureSample()
	{
		return textureSample;
	}

	public TextureUvs getTextureUvs()
	{
		return textureUvs;
	}
}

