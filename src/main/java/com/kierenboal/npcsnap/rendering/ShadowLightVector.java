package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

/** World-space shadow displacement per unit of silhouette height. */
public final class ShadowLightVector
{
	public static final ShadowLightVector CENTERED = new ShadowLightVector(0,0,false);
	public final double x, y;
	private final boolean pointLight;

	private ShadowLightVector(double x, double y, boolean pointLight)
	{
		this.x = x;
		this.y = y;
		this.pointLight = pointLight;
	}

	public ShadowLightVector view(double yaw)
	{
		return new ShadowLightVector(x*Math.cos(yaw)+y*Math.sin(yaw),-x*Math.sin(yaw)+y*Math.cos(yaw),pointLight);
	}

	boolean usesGroundAlignedWidth()
	{
		return pointLight && Math.hypot(x, y) >= 1e-6;
	}

	/** Both positions are in the top-level scene. Keep the lantern falloff in tile units. */
	public static ShadowLightVector lantern(LocalPoint caster, LocalPoint player)
	{
		if (caster == null || player == null)
		{
			return CENTERED;
		}
		double x = (caster.getX() - player.getX()) / (double) Perspective.LOCAL_TILE_SIZE;
		double y = (caster.getY() - player.getY()) / (double) Perspective.LOCAL_TILE_SIZE;
		return resolve(ShadowLighting.PLAYER_LANTERN, x, y, 0, 0, 0);
	}

	public static ShadowLightVector resolve(ShadowLighting mode, double targetX, double targetY,
		double playerX, double playerY, double cameraYaw)
	{
		double x=0, y=0;
		switch(mode)
		{
			case PLAYER_LANTERN:
				if(Double.isFinite(playerX) && Double.isFinite(playerY))
				{ x=(targetX-playerX)/2; y=(targetY-playerY)/2; }
				break;
			case WORLD_DIRECTIONAL: x=1/Math.sqrt(2); y=-x; break;
			case CAMERA_DIRECTIONAL:
				x=(Math.cos(cameraYaw)+Math.sin(cameraYaw))/Math.sqrt(2);
				y=(Math.sin(cameraYaw)-Math.cos(cameraYaw))/Math.sqrt(2);
				break;
			default: break;
		}
		double length=Math.hypot(x,y);
		if(!Double.isFinite(length)) return CENTERED;
		double scale=length>0.75 ? 0.75/length : 1;
		return new ShadowLightVector(x*scale,y*scale,
			mode == ShadowLighting.PLAYER_LANTERN);
	}
}
