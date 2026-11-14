package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.targeting.WorldViewLocationResolver;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** The actor's supporting floor, independent of its displayed model and animation elevation. */
public final class ActorGroundAnchor
{
	public final LocalPoint localPoint;
	public final double height;
	public final BillboardCanvasPoint canvas;
	public final double cameraDistance;

	private ActorGroundAnchor(LocalPoint point, double height, BillboardCanvasPoint canvas, double distance)
	{
		localPoint=point; this.height=height; this.canvas=canvas; cameraDistance=distance;
	}

	public static ActorGroundAnchor resolve(Client client, BillboardDepthCalculator calculator, Actor actor)
	{
		if(actor==null || actor.getWorldView()==null || actor.getLocalLocation()==null) return null;
		WorldView view=actor.getWorldView();
		LocalPoint point=actor.getLocalLocation();
		double height;
		if(view.isTopLevel())
			height=Perspective.getFootprintTileHeight(client,point,view.getPlane(),Math.max(1,actor.getFootprintSize()));
		else
		{
			WorldViewLocationResolver.ResolvedLocation resolved=WorldViewLocationResolver.resolve(client,actor);
			if(resolved==null) return null;
			point=resolved.mainPoint; height=resolved.height;
		}
		if(!Double.isFinite(height)) return null;
		BillboardCanvasPoint canvas=calculator.projectCanvasPoint(point.getX(),point.getY(),height);
		double distance=calculator.cameraDistance(point.getX(),point.getY(),height);
		return canvas==null || !Double.isFinite(canvas.x) || !Double.isFinite(canvas.y) || !Double.isFinite(distance)
			? null : new ActorGroundAnchor(point,height,canvas,distance);
	}
}
