package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;

/** Immutable per-draw terrain exclusion. It never removes geometry from the shared mask. */
public final class SupportingFootprint
{
	public final int worldView, plane, minX, minY, maxX, maxY;
	public SupportingFootprint(int worldView, int plane, int minX, int minY, int maxX, int maxY)
	{
		this.worldView=worldView; this.plane=plane; this.minX=minX; this.minY=minY; this.maxX=maxX; this.maxY=maxY;
	}
	public boolean contains(TerrainSurface surface)
	{
		return surface!=null && !surface.upperSurface && worldView==surface.worldView && plane==surface.plane
			&& surface.tileX>=minX && surface.tileX<=maxX && surface.tileY>=minY && surface.tileY<=maxY;
	}
	public static SupportingFootprint capture(BillboardRenderRequest request, TileObject object)
	{
		if(request==null || request.localPoint==null) return null;
		LocalPoint point=request.sceneLocation==null ? request.localPoint : request.sceneLocation.sourcePoint;
		int plane=request.sceneLocation==null ? request.plane : request.sceneLocation.sourcePlane;
		int view=request.sceneLocation==null ? point.getWorldView() : request.sceneLocation.source.getId();
		if(object instanceof GameObject && object.getWorldView()!=null)
		{
			Point min=((GameObject)object).getSceneMinLocation(), max=((GameObject)object).getSceneMaxLocation();
			if(min!=null && max!=null)
				return new SupportingFootprint(object.getWorldView().getId(),object.getPlane(),min.getX(),min.getY(),max.getX(),max.getY());
		}
		int size=1;
		if(request.renderable instanceof Actor)
		{
			Actor actor=(Actor)request.renderable;
			// Actor footprint size is in local units; the exclusion bounds use tiles.
			size=Math.max(1,(actor.getFootprintSize()+Perspective.LOCAL_TILE_SIZE-1)>>Perspective.LOCAL_COORD_BITS);
			if(actor.getWorldView()!=null && actor.getLocalLocation()!=null)
			{
				point=actor.getLocalLocation(); view=actor.getWorldView().getId(); plane=actor.getWorldView().getPlane();
			}
		}
		else if(object==null && !(request.renderable instanceof TileItem)) return null;
		else if(object!=null && object.getLocalLocation()!=null && object.getWorldView()!=null)
		{
			point=object.getLocalLocation(); view=object.getWorldView().getId(); plane=object.getPlane();
		}
		int half=(size-1)*Perspective.LOCAL_TILE_SIZE/2;
		int minX=(point.getX()-half)>>Perspective.LOCAL_COORD_BITS;
		int minY=(point.getY()-half)>>Perspective.LOCAL_COORD_BITS;
		return new SupportingFootprint(view,plane,minX,minY,minX+size-1,minY+size-1);
	}
}
