package com.kierenboal.npcsnap;

import java.util.List;
import net.runelite.api.Skill;

final class SkillingBubbleAnimation
{
	private final SkillingActivityTracker tracker;

	SkillingBubbleAnimation(SkillingActivityTracker tracker)
	{
		this.tracker = tracker;
	}

	float bubbleAlpha(List<Skill> activeSkills, long nowMillis)
	{
		float alpha = 0.0f;
		for (Skill skill : activeSkills)
		{
			alpha = Math.max(alpha, skillAlpha(skill, nowMillis));
		}
		return alpha;
	}

	float introProgress(List<Skill> activeSkills, long nowMillis)
	{
		float progress = 1.0f;
		for (Skill skill : activeSkills)
		{
			long visibleFrom = tracker.getVisibleFromMillis(skill);
			if (visibleFrom > 0L)
			{
				progress = Math.min(progress, clamp((float) (nowMillis - visibleFrom) / SkillingThoughtBubbleOverlay.FADE_IN_MILLIS));
			}
		}
		return progress;
	}

	float skillAlpha(Skill skill, long nowMillis)
	{
		long visibleFrom = tracker.getVisibleFromMillis(skill);
		long activeUntil = tracker.getActiveUntilMillis(skill);
		if (visibleFrom <= 0L || activeUntil <= 0L)
		{
			return 0.0f;
		}

		if (nowMillis < visibleFrom + SkillingThoughtBubbleOverlay.FADE_IN_MILLIS)
		{
			return clamp((float) (nowMillis - visibleFrom) / SkillingThoughtBubbleOverlay.FADE_IN_MILLIS);
		}
		if (nowMillis <= activeUntil)
		{
			return 1.0f;
		}
		return clamp(1.0f - ((float) (nowMillis - activeUntil) / SkillingThoughtBubbleOverlay.FADE_OUT_MILLIS));
	}

	private static float clamp(float value)
	{
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}
