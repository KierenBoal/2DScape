package com.kierenboal.npcsnap.occlusion;

/** Controls how partially transparent scenery affects billboard pixels. */
public enum BillboardOcclusionComposition
{
	TRANSPARENCY_AWARE("Transparency-aware"),
	HARD_CUTOUT("Hard cutout");

	private final String displayName;

	BillboardOcclusionComposition(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}

	public static BillboardOcclusionComposition normalize(BillboardOcclusionComposition composition)
	{
		return composition != null ? composition : TRANSPARENCY_AWARE;
	}
}
