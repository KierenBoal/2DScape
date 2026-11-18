package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.SpriteShadowType;
import com.kierenboal.npcsnap.rendering.SpriteShadowRenderer.SourceAlpha;
import java.awt.Color;
import java.awt.Rectangle;
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

	SpriteShadowMask(SourceAlpha source, BufferedImage image, SpriteShadowType type, boolean fullResolution,
		int originX, int originY)
	{
		this.fullResolution=fullResolution;
		int width = image.getWidth(), height = image.getHeight();
		// The renderer creates a packed ARGB image for each mask.
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		Rectangle crop = SpriteShadowRenderer.alphaBounds(pixels, width, height);
		int cropWidth = Math.max(1, crop.width), cropHeight = Math.max(1, crop.height);
		this.image = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_ARGB);
		this.image.setRGB(0, 0, cropWidth, cropHeight, pixels, crop.y * width + crop.x, width);
		offsetX = crop.x + originX;
		offsetY = crop.y + originY;
		image.flush();
		this.type = type;
		sourceWidth = source.width;
		sourceHeight = source.height;
		Rectangle occupied = source.bounds;
		feet = Math.max(0, occupied.y + occupied.height - 1);
		visibleHeight = Math.max(1, occupied.height);
		centerX = occupied.isEmpty() ? sourceWidth / 2.0 : occupied.getCenterX();
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
