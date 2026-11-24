package com.kierenboal.npcsnap;

public enum SpriteShadowType
{
	OFF("Off"), FLAT("Flat shadows"), HARD("Hard shadows"), RADIAL("Soft shadows"), BLOB("Blob shadows");
	private final String label;
	SpriteShadowType(String label) { this.label = label; }
	@Override public String toString() { return label; }
}
