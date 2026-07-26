package com.kierenboal.npcsnap.export;

import java.util.ArrayList;
import java.util.List;

public final class BillboardExportAnimationFrames
{
	private BillboardExportAnimationFrames()
	{
	}

	public static List<Integer> sampledFrames(int totalFrames, int visibleFrameCount)
	{
		int frameCount = Math.max(1, totalFrames);
		int sampleCount = Math.max(1, Math.min(frameCount, visibleFrameCount));
		List<Integer> frames = new ArrayList<>(sampleCount);
		for (int sample = 0; sample < sampleCount; sample++)
		{
			frames.add((sample * frameCount) / sampleCount);
		}
		return frames;
	}
}
