package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;
import com.kierenboal.npcsnap.SpriteShadowType;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** CPU export rasterizer uses the requested export view, independent of the live camera. */
public final class ShadowExportImage
{
	public final BufferedImage image;
	public final Rectangle bounds;

	private ShadowExportImage(BufferedImage image, Rectangle bounds)
	{
		this.image = image;
		this.bounds = bounds;
	}

	public static ShadowExportImage render(SpriteCapture capture, ShadowLighting lighting, Color color,
		ShadowLightVector light, double pitch)
	{
		return render(capture,lighting,color,light,pitch,null);
	}

	public static ShadowExportImage render(SpriteCapture capture, ShadowLighting lighting, Color color,
		ShadowLightVector light, double pitch, BillboardCanvasPoint groundOrigin)
	{
		BufferedImage sprite = capture.image;
		Rectangle original = new Rectangle(0, 0, sprite.getWidth(), sprite.getHeight());
		SpriteShadowMask mask = capture.shadow;
		boolean oblique = mask != null && mask.type != SpriteShadowType.LEGACY
			&& lighting != ShadowLighting.DIRECTIONAL_DOWN;
		SpriteShadowMask[] layers = mask == null || color.getAlpha() == 0 ? new SpriteShadowMask[0]
			: oblique ? new SpriteShadowMask[] {mask.directionalCaster()}
			: new SpriteShadowMask[] {mask};
		BillboardDrawGeometry[] geometries = new BillboardDrawGeometry[layers.length];
		Rectangle bounds = new Rectangle(original);
		for (int i = 0; i < layers.length; i++)
		{
			SpriteShadowMask layer = layers[i];
			if (layer == null) continue;
			BillboardDrawGeometry geometry = oblique
				? obliqueGeometry(layer, light, pitch,groundOrigin)
				: groundOrigin==null ? ShadowProjection.downward(layer,BillboardDrawGeometry.rectangular(original),lighting)
					: ShadowProjection.downward(layer,BillboardDrawGeometry.rectangular(original),lighting,groundOrigin);
			geometries[i] = geometry;
			if (geometry != null) bounds = bounds.union(geometry.bounds);
		}
		BufferedImage out = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < layers.length; i++)
		{
			BillboardDrawGeometry geometry = geometries[i];
			if (geometry == null) continue;
			BufferedImage shadow = layers[i].tinted(color);
			BillboardProjectedQuad.Sample sample = new BillboardProjectedQuad.Sample();
			for (int y = geometry.bounds.y; y < geometry.bounds.getMaxY(); y++)
			{
				for (int x = geometry.bounds.x; x < geometry.bounds.getMaxX(); x++)
				{
					if (!geometry.projectedQuad.sample(x + 0.5, y + 0.5, sample)) continue;
					int pixel = shadow.getRGB(
						Math.min(shadow.getWidth() - 1, (int)(sample.u * shadow.getWidth())),
						Math.min(shadow.getHeight() - 1, (int)(sample.v * shadow.getHeight())));
					int dx = x - bounds.x, dy = y - bounds.y;
					out.setRGB(dx, dy, BillboardTriangleRasterizer.blendPixel(out.getRGB(dx, dy), pixel));
				}
			}
		}
		Graphics2D g = out.createGraphics();
		try { g.drawImage(sprite, -bounds.x, -bounds.y, null); }
		finally { g.dispose(); }
		return new ShadowExportImage(out, bounds);
	}

	private static BillboardDrawGeometry obliqueGeometry(SpriteShadowMask mask, ShadowLightVector light, double pitch, BillboardCanvasPoint groundOrigin)
	{
		// The caller supplies the light vector in the requested export view.
		double sin = Math.max(0.25, Math.abs(Math.sin(pitch)));
		double[] basis=ShadowProjection.groundBasis(mask,0,light);
		double anchorX=groundOrigin==null ? mask.centerX : groundOrigin.x;
		double anchorY=groundOrigin==null ? mask.feet+1 : groundOrigin.y;
		BillboardCanvasPoint[] corners = new BillboardCanvasPoint[4];
		for (int i = 0; i < 4; i++)
		{
			double x = mask.offsetX + (i == 1 || i == 2 ? mask.image.getWidth() : 0)-mask.centerX;
			double y = mask.offsetY + (i >= 2 ? mask.image.getHeight() : 0) - (mask.feet + 1);
			corners[i] = new BillboardCanvasPoint(anchorX+x*basis[0]+y*basis[2],anchorY-sin*(x*basis[1]+y*basis[3]),100,0,0);
		}
		double minimumWidth=mask.fullResolution || Math.hypot(light.x,light.y)>1e-6
			? ShadowProjection.minimumCastWidth(mask,mask.image.getWidth()) : 0;
		if(mask.fullResolution) ShadowProjection.limitCastLength(corners,mask,anchorX,anchorY,Math.max(2,mask.visibleHeight*0.5));
		return ShadowProjection.quad(corners,mask,anchorX,anchorY,minimumWidth);
	}
}
