package com.kierenboal.npcsnap;

import java.awt.Color;

final class FaceDraw
{
	final int x0;
	final int y0;
	final int x1;
	final int y1;
	final int x2;
	final int y2;
	private final Color color;
	private final double depth;
	private final TextureSample textureSample;
	private final TextureUvs textureUvs;

	FaceDraw(
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

	Color getColor()
	{
		return color;
	}

	double getDepth()
	{
		return depth;
	}

	boolean isTextured()
	{
		return textureSample != null && textureUvs != null;
	}

	TextureSample getTextureSample()
	{
		return textureSample;
	}

	TextureUvs getTextureUvs()
	{
		return textureUvs;
	}
}

