package com.kierenboal.npcsnap.features;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.api.Skill;

@Singleton
public class SkillingActivityTracker
{
	private static final EnumSet<Skill> TRACKED_SKILLS = EnumSet.of(
		Skill.MINING,
		Skill.AGILITY,
		Skill.SMITHING,
		Skill.HERBLORE,
		Skill.FISHING,
		Skill.THIEVING,
		Skill.COOKING,
		Skill.CRAFTING,
		Skill.FIREMAKING,
		Skill.FLETCHING,
		Skill.WOODCUTTING,
		Skill.RUNECRAFT,
		Skill.FARMING,
		Skill.CONSTRUCTION,
		Skill.HUNTER,
		Skill.SAILING
	);

	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private final Map<Skill, Long> visibleFromMillis = new EnumMap<>(Skill.class);
	private final Map<Skill, Long> activeUntilMillis = new EnumMap<>(Skill.class);

	public Collection<Skill> getTrackedSkills()
	{
		return TRACKED_SKILLS;
	}

	public boolean isTrackedSkill(Skill skill)
	{
		return skill != null && TRACKED_SKILLS.contains(skill);
	}

	public boolean isTrackedXpIncrease(Skill skill, int xp)
	{
		if (!isTrackedSkill(skill))
		{
			return false;
		}

		Integer previousXp = lastXp.get(skill);
		return previousXp != null && xp > previousXp;
	}

	public void seedXp(Skill skill, int xp)
	{
		if (!isTrackedSkill(skill))
		{
			return;
		}

		lastXp.put(skill, xp);
	}

	public boolean recordXp(Skill skill, int xp, long nowMillis, long timeoutMillis)
	{
		if (skill == null)
		{
			return false;
		}

		Integer previousXp = lastXp.put(skill, xp);
		if (previousXp == null || xp <= previousXp || !isTrackedSkill(skill))
		{
			return false;
		}

		Long expiresAt = activeUntilMillis.get(skill);
		if (expiresAt == null || expiresAt <= nowMillis)
		{
			visibleFromMillis.put(skill, nowMillis);
		}

		activeUntilMillis.put(skill, nowMillis + Math.max(0L, timeoutMillis));
		return true;
	}

	public List<Skill> getRenderableSkills(long nowMillis, long fadeOutMillis)
	{
		List<Skill> activeSkills = new ArrayList<>(activeUntilMillis.size());
		long removalThreshold = nowMillis - Math.max(0L, fadeOutMillis);
		Iterator<Map.Entry<Skill, Long>> iterator = activeUntilMillis.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<Skill, Long> entry = iterator.next();
			if (entry.getValue() <= removalThreshold)
			{
				visibleFromMillis.remove(entry.getKey());
				iterator.remove();
			}
		}

		for (Skill skill : TRACKED_SKILLS)
		{
			Long expiresAt = activeUntilMillis.get(skill);
			if (expiresAt != null && expiresAt > removalThreshold)
			{
				activeSkills.add(skill);
			}
		}

		return activeSkills;
	}

	public long getVisibleFromMillis(Skill skill)
	{
		return visibleFromMillis.getOrDefault(skill, 0L);
	}

	public long getActiveUntilMillis(Skill skill)
	{
		return activeUntilMillis.getOrDefault(skill, 0L);
	}

	public void clear()
	{
		lastXp.clear();
		visibleFromMillis.clear();
		activeUntilMillis.clear();
	}
}
