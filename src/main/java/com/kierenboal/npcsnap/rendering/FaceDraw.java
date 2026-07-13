package com.kierenboal.npcsnap.rendering;

import java.awt.Color;

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

	public FaceDraw(
		int x0,
		int y0,
		int x1,
		int y1,
		int x2,
		int y2,
		Color color,
		double depth,
		TextureSample textureSample,
		TextureUvs textureUvs)
	{
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.color = color;
		this.depth = depth;
		this.textureSample = textureSample;
		this.textureUvs = textureUvs;
	}

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

