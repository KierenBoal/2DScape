package com.kierenboal.npcsnap.features;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/** One reversible bubble transition, with independent reversible icon transitions. */
public final class SkillingBubbleAnimation
{
	private final SkillingActivityTracker tracker;
	private final Map<Skill, Icon> icons = new EnumMap<>(Skill.class);
	private long lastMillis = -1L;
	private float visibility;

	public SkillingBubbleAnimation(SkillingActivityTracker tracker)
	{
		this.tracker = tracker;
	}

	void refresh(Skill skill, long nowMillis, long expiresAt)
	{
		advance(nowMillis);
		icons.computeIfAbsent(skill, ignored -> new Icon()).expiresAt = expiresAt;
	}

	void clear()
	{
		icons.clear();
		visibility = 0f;
		lastMillis = -1L;
	}

	private void advance(long nowMillis)
	{
		if (lastMillis < 0L)
		{
			lastMillis = nowMillis;
		}
		if (nowMillis <= lastMillis)
		{
			return;
		}
		long activeUntil = lastMillis;
		for (Icon icon : icons.values())
		{
			activeUntil = Math.max(activeUntil, icon.expiresAt);
			icon.progress = advanceProgress(icon.progress, lastMillis, nowMillis, icon.expiresAt);
		}
		visibility = advanceProgress(visibility, lastMillis, nowMillis, activeUntil);
		icons.entrySet().removeIf(entry -> entry.getValue().progress == 0f && entry.getValue().expiresAt <= nowMillis);
		lastMillis = nowMillis;
	}

	private static float advanceProgress(float progress, long from, long to, long expiresAt)
	{
		long risingUntil = Math.max(from, Math.min(to, expiresAt));
		progress = clamp(progress + (risingUntil - from) / (float) SkillingThoughtBubbleOverlay.FADE_IN_MILLIS);
		return clamp(progress - (to - risingUntil) / (float) SkillingThoughtBubbleOverlay.FADE_OUT_MILLIS);
	}

	public List<Skill> renderableSkills(long nowMillis)
	{
		SkillingBubbleAnimation state = tracker.animation();
		state.advance(nowMillis);
		return new ArrayList<>(state.icons.keySet());
	}

	public float bubbleAlpha(List<Skill> activeSkills, long nowMillis)
	{
		return introProgress(activeSkills, nowMillis);
	}

	public float introProgress(List<Skill> activeSkills, long nowMillis)
	{
		SkillingBubbleAnimation state = tracker.animation();
		state.advance(nowMillis);
		return ease(state.visibility);
	}

	public float skillAlpha(Skill skill, long nowMillis)
	{
		SkillingBubbleAnimation state = tracker.animation();
		state.advance(nowMillis);
		Icon icon = state.icons.get(skill);
		return icon != null ? ease(icon.progress) : 0f;
	}

	private static float ease(float value)
	{
		return value * value * (3f - 2f * value);
	}

	private static float clamp(float value)
	{
		return Math.max(0f, Math.min(1f, value));
	}

	private static final class Icon
	{
		private float progress;
		private long expiresAt;
	}
}
