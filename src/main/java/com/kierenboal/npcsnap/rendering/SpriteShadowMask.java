package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.SpriteShadowType;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Immutable shape, with a reusable tint independent of the captured sprite. */
public final class SpriteShadowMask
{
	public final BufferedImage image;
	public final SpriteShadowType type;
	public final int sourceWidth, sourceHeight, feet, visibleHeight;
	public final int offsetX, offsetY;
	public final double centerX;
	public final int rows;
	public final boolean fullResolution;
	private SpriteShadowMask silhouette;
	private BufferedImage tinted;
	private int tint;

	public SpriteShadowMask(BufferedImage source, BufferedImage image, SpriteShadowType type)
	{
		this(source,image,type,false);
	}

	SpriteShadowMask(BufferedImage source, BufferedImage image, SpriteShadowType type, boolean fullResolution)
	{
		this(source,image,type,fullResolution,-SpriteShadowRenderer.horizontalPadding(source.getWidth(),type),0);
	}

	SpriteShadowMask(BufferedImage source, BufferedImage image, SpriteShadowType type, boolean fullResolution,
		int originX, int originY)
	{
		this.fullResolution=fullResolution;
		int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
		for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
			if((image.getRGB(x,y)>>>24)!=0)
			{ minX=Math.min(minX,x); minY=Math.min(minY,y); maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); }
		if(maxX<minX) { minX=0; minY=0; maxX=0; maxY=0; }
		this.image = new BufferedImage(maxX-minX+1,maxY-minY+1,BufferedImage.TYPE_INT_ARGB);
		this.image.setRGB(0,0,this.image.getWidth(),this.image.getHeight(),
			image.getRGB(minX,minY,this.image.getWidth(),this.image.getHeight(),null,0,this.image.getWidth()),0,this.image.getWidth());
		offsetX = minX+originX; offsetY = minY+originY;
		image.flush();
		this.type = type;
		sourceWidth = source.getWidth(); sourceHeight = source.getHeight();
		int top = sourceHeight, bottom = -1, left = sourceWidth, right = -1;
		for (int y=0; y<sourceHeight; y++) for (int x=0; x<sourceWidth; x++)
			if ((source.getRGB(x,y) >>> 24) != 0)
			{
				top = Math.min(top,y); bottom = y; left = Math.min(left,x); right = Math.max(right,x);
			}
		feet = Math.max(0,bottom);
		visibleHeight = Math.max(1,bottom-top+1);
		centerX = right < left ? sourceWidth/2.0 : (left+right+1)/2.0;
		rows = type == SpriteShadowType.FLAT ? 5 : 7;
	}

	void setDirectionalCaster(SpriteShadowMask silhouette) { this.silhouette=silhouette; }
	/** Angled lighting draws one complete mask, without a second downward footprint. */
	public SpriteShadowMask directionalCaster() { return silhouette==null ? this : silhouette; }

	public double projectionSpan()
	{
		return Math.max(fullResolution ? visibleHeight : rows, Math.max(Math.abs(offsetY-(feet+1)),Math.abs(offsetY+image.getHeight()-(feet+1))));
	}

	public BufferedImage tinted(Color color)
	{
		if (tinted == null)
			tinted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		if (tint != color.getRGB() || !initialized)
		{
			int[] src = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
			int[] dst = ((DataBufferInt)tinted.getRaster().getDataBuffer()).getData();
			for (int i=0; i<src.length; i++)
				dst[i] = ((((src[i] >>> 24)*color.getAlpha()+127)/255) << 24) | (color.getRGB() & 0xffffff);
			tint = color.getRGB(); initialized = true;
		}
		return tinted;
	}
	private boolean initialized;
	public void flush()
	{
		image.flush();
		if (tinted != null) tinted.flush();
		if(silhouette!=null) silhouette.flush();
	}
}
