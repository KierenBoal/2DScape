package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;
import com.kierenboal.npcsnap.SpriteShadowType;
import java.awt.Rectangle;
import net.runelite.api.Point;

public final class ShadowProjection
{
	private ShadowProjection() { }

	/** Source mapping preserves the sprite projection, including its snapped view. */
	public static BillboardDrawGeometry downward(SpriteShadowMask mask, BillboardDrawGeometry sprite,
		ShadowLighting lighting)
	{
		return downward(mask,sprite,lighting,100);
	}

	public static BillboardDrawGeometry downward(SpriteShadowMask mask, BillboardDrawGeometry sprite,
		ShadowLighting lighting, double receiverDepth)
	{
		return downward(mask,sprite,lighting,receiverDepth,null);
	}

	public static BillboardDrawGeometry downward(SpriteShadowMask mask, BillboardDrawGeometry sprite,
		ShadowLighting lighting, BillboardCanvasPoint receiver)
	{
		return downward(mask,sprite,lighting,receiver.depth,receiver);
	}

	private static BillboardDrawGeometry downward(SpriteShadowMask mask, BillboardDrawGeometry sprite,
		ShadowLighting lighting, double receiverDepth, BillboardCanvasPoint receiver)
	{
		Point oldAnchor=sprite.canvasPoint(mask.centerX/mask.sourceWidth,(mask.feet+1.0)/mask.sourceHeight);
		double dx=receiver==null ? 0 : receiver.x-oldAnchor.getX();
		double dy=receiver==null ? 0 : receiver.y-oldAnchor.getY();
		int lift = mask.type != SpriteShadowType.LEGACY && lighting == ShadowLighting.DIRECTIONAL_DOWN ? 1 : 0;
		double x=mask.offsetX, y=mask.offsetY;
		double liftPixels=0;
		if(lift!=0)
		{
			if(sprite.projectedQuad==null) liftPixels=sprite.unskewedBounds.height/(double)mask.sourceHeight;
			else
			{
				Point foot=sprite.canvasPoint(mask.centerX/mask.sourceWidth,(mask.feet+1.0)/mask.sourceHeight);
				Point above=sprite.canvasPoint(mask.centerX/mask.sourceWidth,mask.feet/(double)mask.sourceHeight);
				liftPixels=foot.getY()-above.getY();
			}
		}
		BillboardCanvasPoint[] corners=new BillboardCanvasPoint[4];
		for(int i=0;i<4;i++)
		{
			double u=(x+(i==1 || i==2 ? mask.image.getWidth() : 0))/mask.sourceWidth;
			double v=(y+(i>=2 ? mask.image.getHeight() : 0))/mask.sourceHeight;
			Point point=sprite.canvasPoint(u,v);
			double depth=receiver!=null || sprite.projectedQuad==null ? receiverDepth : sprite.projectedQuad.depthAt(u,v);
			corners[i]=new BillboardCanvasPoint(point.getX()+dx,point.getY()+dy-liftPixels,depth,0,0);
		}
		return quad(corners,mask);
	}

	public static BillboardDrawGeometry ground(SpriteShadowMask mask, BillboardDrawGeometry sprite,
		BillboardDepthCalculator calculator, double localX, double localY, double groundZ,
		double unit, double bearing, ShadowLightVector light)
	{
		return ground(mask,sprite,calculator,localX,localY,groundZ,unit,bearing,light,null);
	}

	public static BillboardDrawGeometry ground(SpriteShadowMask mask, BillboardDrawGeometry sprite,
		BillboardDepthCalculator calculator, double localX, double localY, double groundZ,
		double unit, double bearing, ShadowLightVector light, BillboardCanvasPoint receiver)
	{
		double[] basis=groundBasis(mask,bearing,light);
		BillboardCanvasPoint base=calculator.projectCanvasPoint(localX,localY,groundZ);
		if(base==null) return null;
		Point spriteAnchor=sprite.canvasPoint(mask.centerX/mask.sourceWidth,(mask.feet+1.0)/mask.sourceHeight);
		double anchorX=receiver==null ? spriteAnchor.getX() : receiver.x;
		double anchorY=receiver==null ? spriteAnchor.getY() : receiver.y;
		BillboardCanvasPoint[] corners=new BillboardCanvasPoint[4];
		for(int i=0;i<4;i++)
		{
			double x=mask.offsetX+(i==1 || i==2 ? mask.image.getWidth() : 0)-mask.centerX;
			double y=mask.offsetY+(i>=2 ? mask.image.getHeight() : 0)-(mask.feet+1);
			double wx=(x*basis[0]+y*basis[2])*unit;
			double wy=(x*basis[1]+y*basis[3])*unit;
			BillboardCanvasPoint p=calculator.projectCanvasPoint(localX+wx,localY+wy,groundZ);
			if(p==null) return null;
			corners[i]=new BillboardCanvasPoint(p.x+anchorX-base.x,p.y+anchorY-base.y,p.depth,0,0);
		}
		double minimumWidth=mask.fullResolution || Math.hypot(light.x,light.y)>1e-6
			? minimumCastWidth(mask,sprite) : 0;
		if(mask.fullResolution)
		{
			Point top=sprite.canvasPoint(mask.centerX/mask.sourceWidth,
				(mask.feet+1.0-mask.visibleHeight)/mask.sourceHeight);
			limitCastLength(corners,mask,anchorX,anchorY,Math.max(2,
				Math.hypot(spriteAnchor.getX()-top.getX(),spriteAnchor.getY()-top.getY())*0.5));
		}
		return quad(corners,mask,anchorX,anchorY,minimumWidth);
	}

	/** Shorten source height about the feet in homogeneous space. This changes
	 * cast length, not its direction, width axis, or ground-contact position. */
	static void limitCastLength(BillboardCanvasPoint[] points,SpriteShadowMask mask,
		double anchorX,double anchorY,double maximumLength)
	{
		double u=(mask.centerX-mask.offsetX)/mask.image.getWidth();
		double v=(mask.feet+1.0-mask.offsetY)/mask.image.getHeight();
		BillboardCanvasPoint tip=interpolate(points[0],points[1],u);
		double length=Math.hypot(tip.x-anchorX,tip.y-anchorY);
		if(!Double.isFinite(length) || length<=maximumLength || maximumLength<=0) return;
		BillboardCanvasPoint bottom=interpolate(points[3],points[2],u);
		double anchorDepth=tip.depth+(bottom.depth-tip.depth)*v;
		double ratio=maximumLength/length;
		double scale=ratio*anchorDepth/(tip.depth*(1-ratio)+ratio*anchorDepth);
		for(int column=0;column<2;column++)
		{
			int a=column,b=column==0 ? 3 : 2;
			BillboardCanvasPoint top=points[a],end=points[b];
			points[a]=interpolate(top,end,v*(1-scale));
			points[b]=interpolate(top,end,v+(1-v)*scale);
		}
	}

	private static BillboardCanvasPoint interpolate(BillboardCanvasPoint a,BillboardCanvasPoint b,double t)
	{
		double depth=a.depth+(b.depth-a.depth)*t;
		return new BillboardCanvasPoint((a.x*a.depth+(b.x*b.depth-a.x*a.depth)*t)/depth,
			(a.y*a.depth+(b.y*b.depth-a.y*a.depth)*t)/depth,depth,0,0);
	}

	/** Maps native silhouette pixels into the ground plane; also used by the export rasterizer. */
	public static double[] groundBasis(SpriteShadowMask mask, double bearing, ShadowLightVector light)
	{
		double rx=Math.cos(bearing), ry=Math.sin(bearing), fx=-ry, fy=rx;
		double scale=mask.visibleHeight/mask.projectionSpan();
		double length=Math.hypot(light.x,light.y);
		if(length<1e-6)
		{
			// A directly overhead point light has no azimuth; retain a compact footprint.
			double depth=mask.fullResolution ? Math.min(0.1,2.0/mask.visibleHeight)*scale : 1;
			return new double[]{rx,ry,fx*depth,fy*depth};
		}
		// Treat the capture as an upright camera-facing sheet. Its horizontal
		// axis remains camera-right; only height is displaced away from the light.
		// Rotating width around the light and then flipping/shearing it back adds
		// a second rotation to a silhouette that already includes the captured yaw.
		return new double[]{rx,ry,-scale*light.x,-scale*light.y};
	}

	static double minimumCastWidth(SpriteShadowMask mask, BillboardDrawGeometry sprite)
	{
		double y=(mask.feet+1.0)/mask.sourceHeight;
		Point left=sprite.canvasPoint(mask.offsetX/(double)mask.sourceWidth,y);
		Point right=sprite.canvasPoint((mask.offsetX+mask.image.getWidth())/(double)mask.sourceWidth,y);
		return minimumCastWidth(mask,Math.hypot(right.getX()-left.getX(),right.getY()-left.getY()));
	}

	static double minimumCastWidth(SpriteShadowMask mask,double displayedWidth)
	{
		double ratio=mask.type==SpriteShadowType.HARD || mask.type==SpriteShadowType.RADIAL ? 0.10 : 0.25;
		return Math.max(2,displayedWidth*ratio);
	}

	private static BillboardDrawGeometry quad(BillboardCanvasPoint[] points, SpriteShadowMask mask)
	{
		return quad(points,mask,0,0);
	}

	static BillboardDrawGeometry quad(BillboardCanvasPoint[] points, SpriteShadowMask mask, double anchorX, double anchorY)
	{
		return quad(points,mask,anchorX,anchorY,mask.fullResolution ? minimumCastWidth(mask,mask.image.getWidth()) : 0);
	}

	static BillboardDrawGeometry quad(BillboardCanvasPoint[] points, SpriteShadowMask mask,
		double anchorX, double anchorY, double minimumWidth)
	{
		if(minimumWidth>0) preserveThickness(points,mask,anchorX,anchorY,minimumWidth);
		Rectangle bounds=new Rectangle(0,0,mask.image.getWidth(),mask.image.getHeight());
		return BillboardDrawGeometry.projected(BillboardProjectedQuad.create(points[0],points[1],points[2],points[3],bounds,bounds));
	}

	/** Expand across the cast, never across arbitrary screen X/Y axes. The center
		* line, light direction and foot anchor therefore stay fixed while rotating. */
	private static void preserveThickness(BillboardCanvasPoint[] points, SpriteShadowMask mask,
		double anchorX, double anchorY, double minimumWidth)
	{
		double u=(mask.centerX-mask.offsetX)/mask.image.getWidth();
		double depth=points[0].depth*(1-u)+points[1].depth*u;
		if(!Double.isFinite(depth) || depth<=0) return;
		double tipX=(points[0].x*points[0].depth*(1-u)+points[1].x*points[1].depth*u)/depth;
		double tipY=(points[0].y*points[0].depth*(1-u)+points[1].y*points[1].depth*u)/depth;
		double ax=tipX-anchorX, ay=tipY-anchorY, length=Math.hypot(ax,ay);
		if(!Double.isFinite(length) || length<1e-9) return;
		ax/=length; ay/=length;
		double nx=ay, ny=-ax;
		double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
		for(BillboardCanvasPoint point:points)
		{
			double across=(point.x-anchorX)*nx+(point.y-anchorY)*ny;
			min=Math.min(min,across); max=Math.max(max,across);
		}
		double width=max-min;
		if(width>=minimumWidth && length>=1.01) return;
		double widthScale=width>1e-9 ? Math.max(1,minimumWidth/width) : 1;
		double lengthScale=Math.max(1,1.01/length);
		double v=(mask.feet+1.0-mask.offsetY)/mask.image.getHeight();
		double anchorDepth=points[0].depth+u*(points[1].depth-points[0].depth)+v*(points[3].depth-points[0].depth);
		for(int i=0;i<4;i++)
		{
			BillboardCanvasPoint point=points[i];
			double dx=point.x-anchorX,dy=point.y-anchorY;
			double along=(dx*ax+dy*ay)*lengthScale;
			double across=width>1e-9 ? (dx*nx+dy*ny)*widthScale
				: ((i==1 || i==2 ? 1 : 0)-u)*minimumWidth*anchorDepth/point.depth;
			points[i]=new BillboardCanvasPoint(anchorX+ax*along+nx*across,anchorY+ay*along+ny*across,
				point.depth,point.verticalRight,point.verticalUp);
		}
	}
}
