package com.kierenboal.npcsnap;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Animation;
import net.runelite.api.Client;

@Singleton
class AnimationFrameSnapper
{
	private final Client client;
	private final Map<Integer, Integer> animationFrameCache = new HashMap<>();

	@Inject
	private AnimationFrameSnapper(Client client)
	{
		this.client = client;
	}

	int snapAnimationFrame(int animationId, int frame, int visibleFrameCount)
	{
		if (animationId < 0 || frame < 0 || visibleFrameCount <= 0)
		{
			return frame;
		}

		int totalFrames = animationFrameCache.computeIfAbsent(animationId, this::loadAnimationFrameCount);
		if (totalFrames <= 1 || visibleFrameCount >= totalFrames)
		{
			return frame;
		}

		int clampedFrame = Math.min(frame, totalFrames - 1);
		int snappedBucket = clampedFrame * visibleFrameCount / totalFrames;
		int snappedFrame = snappedBucket * totalFrames / visibleFrameCount;
		return Math.min(snappedFrame, totalFrames - 1);
	}

	int snapFrame(Animation animation, int frame, boolean enabled, int visibleFrameCount)
	{
		if (!enabled || animation == null)
		{
			return frame;
		}

		return snapAnimationFrame(animation.getId(), frame, visibleFrameCount);
	}

	void clear()
	{
		animationFrameCache.clear();
	}

	private int loadAnimationFrameCount(int animationId)
	{
		Animation animation = client.loadAnimation(animationId);
		if (animation == null)
		{
			return -1;
		}

		return animation.getNumFrames();
	}
}
