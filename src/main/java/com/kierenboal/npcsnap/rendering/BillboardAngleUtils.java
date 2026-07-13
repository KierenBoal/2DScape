package com.kierenboal.npcsnap.rendering;

public final class BillboardAngleUtils
{
	public static final int LEGACY_FULL_CIRCLE = 2048;
	public static final int BILLBOARD_FULL_CIRCLE = 16384;
	public static final int CAMERA_FULL_CIRCLE = BILLBOARD_FULL_CIRCLE;
	public static final int ACTOR_FULL_CIRCLE = LEGACY_FULL_CIRCLE;
	public static final int PROJECTILE_FULL_CIRCLE = LEGACY_FULL_CIRCLE;
	public static final int MAX_PITCH = angleToBillboardUnits(512, LEGACY_FULL_CIRCLE);
	public static final int GROUND_ITEM_MIN_PITCH = angleToBillboardUnits(128, LEGACY_FULL_CIRCLE);
	public static final int COMBAT_YAW = angleToBillboardUnits(512, LEGACY_FULL_CIRCLE);
	public static final int OPPOSITE_COMBAT_YAW = angleToBillboardUnits(1536, LEGACY_FULL_CIRCLE);

	private BillboardAngleUtils()
	{
	}

	public static int angleToBillboardUnits(int angle, int sourceFullCircle)
	{
		int sourceUnits = Math.max(1, sourceFullCircle);
		int normalized = Math.floorMod(angle, sourceUnits);
		return Math.floorMod((int) Math.round((normalized * (double) BILLBOARD_FULL_CIRCLE) / sourceUnits), BILLBOARD_FULL_CIRCLE);
	}

	public static int combatYaw(int rawRelativeYaw)
	{
		int normalized = Math.floorMod(rawRelativeYaw, BILLBOARD_FULL_CIRCLE);
		int combatDistance = jauDistance(normalized, COMBAT_YAW);
		int oppositeDistance = jauDistance(normalized, OPPOSITE_COMBAT_YAW);
		return combatDistance <= oppositeDistance ? COMBAT_YAW : OPPOSITE_COMBAT_YAW;
	}

	public static int snapJauByAngles(int jau, int angleCount)
	{
		int clampedAngleCount = Math.max(1, angleCount);
		int step = Math.max(1, BILLBOARD_FULL_CIRCLE / clampedAngleCount);
		int normalized = Math.floorMod(jau, BILLBOARD_FULL_CIRCLE);
		return Math.floorMod(((normalized + (step / 2)) / step) * step, BILLBOARD_FULL_CIRCLE);
	}

	public static int snapPitchByAngles(int pitch, int minPitch, int angleCount)
	{
		int clampedMinPitch = Math.max(0, Math.min(minPitch, MAX_PITCH));
		int clampedPitch = Math.max(clampedMinPitch, Math.min(pitch, MAX_PITCH));
		int clampedAngleCount = Math.max(1, angleCount);
		int pitchRange = Math.max(1, MAX_PITCH - clampedMinPitch);
		if (clampedAngleCount == 1)
		{
			return clampedMinPitch;
		}

		int snappedIndex = Math.max(0, Math.min(clampedAngleCount - 1, ((clampedPitch - clampedMinPitch) * clampedAngleCount) / pitchRange));
		if (snappedIndex == 0)
		{
			return clampedMinPitch;
		}
		if (snappedIndex == clampedAngleCount - 1)
		{
			return MAX_PITCH;
		}

		int snappedPitch = clampedMinPitch + (int) Math.round(((snappedIndex + 0.5d) * pitchRange) / clampedAngleCount);
		return Math.max(clampedMinPitch, Math.min(MAX_PITCH, snappedPitch));
	}

	private static int jauDistance(int a, int b)
	{
		int distance = Math.abs(Math.floorMod(a, BILLBOARD_FULL_CIRCLE) - Math.floorMod(b, BILLBOARD_FULL_CIRCLE));
		return Math.min(distance, BILLBOARD_FULL_CIRCLE - distance);
	}
}
