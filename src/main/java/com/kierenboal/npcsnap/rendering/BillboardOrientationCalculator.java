package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.NpcSnapConfig;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Projectile;

public final class BillboardOrientationCalculator
{
	private final Client client;
	private final NpcSnapConfig config;

	public BillboardOrientationCalculator(Client client, NpcSnapConfig config)
	{
		this.client = client;
		this.config = config;
	}

	public int relativeYaw()
	{
		return snappedYaw(cameraYaw());
	}

	public int relativeYaw(Actor actor)
	{
		int rawRelativeYaw = cameraYaw() + actorYaw(actor);
		if (shouldCombatSnap(actor))
		{
			return BillboardAngleUtils.combatYaw(rawRelativeYaw);
		}

		return snappedYaw(rawRelativeYaw);
	}

	public int relativeYaw(Projectile projectile)
	{
		return snappedYaw(cameraYaw() + projectileYaw(projectile));
	}

	public int relativeGroundItemYaw()
	{
		return snappedYaw(cameraYaw());
	}

	public int relativePitch()
	{
		int rawPitch = cameraPitch();
		if (!config.enableRotationSnapping())
		{
			return -rawPitch;
		}

		int snappedPitch = BillboardAngleUtils.snapPitchByAngles(rawPitch, 0, config.numberOfPitchRotationAngles());
		// Invert because model pitch is applied opposite to camera pitch.
		return -snappedPitch;
	}

	public int relativePitch(Actor actor)
	{
		if (shouldCombatSnap(actor))
		{
			return 0;
		}

		return relativePitch();
	}

	public int relativeGroundItemPitch()
	{
		int rawPitch = cameraPitch();
		if (!config.enableRotationSnapping())
		{
			return -rawPitch;
		}

		return -BillboardAngleUtils.snapPitchByAngles(rawPitch, BillboardAngleUtils.GROUND_ITEM_MIN_PITCH, config.numberOfPitchRotationAngles());
	}

	private int snappedYaw(int rawRelativeYaw)
	{
		if (!config.enableRotationSnapping())
		{
			return Math.floorMod(rawRelativeYaw, BillboardAngleUtils.BILLBOARD_FULL_CIRCLE);
		}

		return BillboardAngleUtils.snapJauByAngles(rawRelativeYaw, config.numberOfYawRotationAngles());
	}

	private int cameraYaw()
	{
		return BillboardAngleUtils.angleToBillboardUnits(client.getCameraYaw(), BillboardAngleUtils.CAMERA_FULL_CIRCLE);
	}

	private int cameraPitch()
	{
		int pitch = BillboardAngleUtils.angleToBillboardUnits(client.getCameraPitch(), BillboardAngleUtils.CAMERA_FULL_CIRCLE);
		return Math.max(0, Math.min(BillboardAngleUtils.MAX_PITCH, pitch));
	}

	private static int actorYaw(Actor actor)
	{
		return actor == null
			? 0
			: BillboardAngleUtils.angleToBillboardUnits(actor.getCurrentOrientation(), BillboardAngleUtils.ACTOR_FULL_CIRCLE);
	}

	private static int projectileYaw(Projectile projectile)
	{
		return projectile == null
			? 0
			: BillboardAngleUtils.angleToBillboardUnits(projectile.getOrientation(), BillboardAngleUtils.PROJECTILE_FULL_CIRCLE);
	}

	private boolean shouldCombatSnap(Actor actor)
	{
		return config.enableBillboardCombatSnapping() && isMutuallyInteracting(actor);
	}

	private static boolean isMutuallyInteracting(Actor actor)
	{
		if (actor == null)
		{
			return false;
		}

		Actor interacting = actor.getInteracting();
		return interacting != null && interacting.getInteracting() == actor;
	}
}
