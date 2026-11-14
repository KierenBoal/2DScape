package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;

/** World-space shadow displacement per unit of silhouette height. */
public final class ShadowLightVector
{
	public static final ShadowLightVector CENTERED = new ShadowLightVector(0,0);
	public final double x, y;
	private ShadowLightVector(double x, double y) { this.x=x; this.y=y; }
	public ShadowLightVector view(double yaw)
	{
		return new ShadowLightVector(x*Math.cos(yaw)+y*Math.sin(yaw),-x*Math.sin(yaw)+y*Math.cos(yaw));
	}
	public static ShadowLightVector resolve(ShadowLighting mode, double targetX, double targetY,
		double playerX, double playerY, double cameraYaw)
	{
		double x=0, y=0;
		switch(mode)
		{
			case GLOBAL_POINT: x=(targetX-3200)/16; y=(targetY-3200)/16; break;
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
		if(!Double.isFinite(length)) return new ShadowLightVector(0,0);
		double scale=length>0.75 ? 0.75/length : 1;
		return new ShadowLightVector(x*scale,y*scale);
	}
}
