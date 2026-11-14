package com.kierenboal.npcsnap;

public enum ShadowLighting
{
	DIRECTIONAL_DOWN("Directional light down"),
	GLOBAL_POINT("Global point light"),
	PLAYER_LANTERN("Player lantern"),
	WORLD_DIRECTIONAL("World directional light"),
	CAMERA_DIRECTIONAL("Camera directional light");
	private final String label;
	ShadowLighting(String label) { this.label = label; }
	@Override public String toString() { return label; }
}
