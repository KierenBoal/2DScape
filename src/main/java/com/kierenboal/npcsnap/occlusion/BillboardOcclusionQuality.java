package com.kierenboal.npcsnap.occlusion;

public enum BillboardOcclusionQuality
{
	OFF(0, Integer.MAX_VALUE),
	LOW(16, 8),
	MEDIUM(8, 4),
	HIGH(4, 1),
	ULTRA(2, 1),
	MAX(1, 1);

	private final int sampleStep;
	private final int vertexStride;

	BillboardOcclusionQuality(int sampleStep, int vertexStride)
	{
		this.sampleStep = sampleStep;
		this.vertexStride = vertexStride;
	}

	public int sampleStep()
	{
		return sampleStep;
	}

	public int vertexStride()
	{
		return vertexStride;
	}

	public static BillboardOcclusionQuality normalize(BillboardOcclusionQuality quality)
	{
		return quality != null ? quality : OFF;
	}
}
