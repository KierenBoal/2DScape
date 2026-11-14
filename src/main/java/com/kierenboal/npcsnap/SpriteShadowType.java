package com.kierenboal.npcsnap;

public enum SpriteShadowType
{
	OFF("Off"), LEGACY("Legacy"), FLAT("Flat shadows"), HARD("Hard shadows"), RADIAL("Soft shadows"), BLOB("Blob shadows");
	private final String label;
	SpriteShadowType(String label) { this.label = label; }
	public boolean projected() { return this != OFF && this != LEGACY; }
	@Override public String toString() { return label; }
}
