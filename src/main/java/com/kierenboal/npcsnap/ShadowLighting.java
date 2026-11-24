package com.kierenboal.npcsnap;

public enum ShadowLighting
{
	DIRECTIONAL_DOWN("Down"),
	PLAYER_LANTERN("Player lantern"),
	WORLD_DIRECTIONAL("World directional"),
	CAMERA_DIRECTIONAL("Camera directional");
	private final String label;
	ShadowLighting(String label) { this.label = label; }
	@Override public String toString() { return label; }
}
