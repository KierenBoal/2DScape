package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.NpcSnapConfig;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;

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
		return relativeYaw(actor, actor != null ? actor.getCurrentOrientation() : 0);
	}

	public int relativeYaw(Actor actor, int actorOrientation)
	{
		int rawRelativeYaw = cameraYaw() + BillboardAngleUtils.angleToBillboardUnits(actorOrientation, BillboardAngleUtils.ACTOR_FULL_CIRCLE);
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

	/** Body-only correction: spot animations and exported angles retain their existing yaw. */
	public int relativeActorBodyYaw(Actor actor, int actorOrientation, LocalPoint mainWorldLocation)
	{
		int yaw = actorBodyRawYaw(actorOrientation, mainWorldLocation);
		return shouldCombatSnap(actor) ? BillboardAngleUtils.combatYaw(yaw) : snappedYaw(yaw);
	}

	public int actorBodyRawYaw(int actorOrientation, LocalPoint mainWorldLocation)
	{
		int viewYaw = cameraYaw();
		if (!config.ignoreCameraAwareRotation() && mainWorldLocation != null)
		{
			double cameraX = client.isGpu() ? client.getCameraFpX() : client.getCameraX();
			double cameraY = client.isGpu() ? client.getCameraFpY() : client.getCameraY();
			double dx = mainWorldLocation.getX() - cameraX;
			double dy = mainWorldLocation.getY() - cameraY;
			if (Double.isFinite(dx) && Double.isFinite(dy) && Math.hypot(dx, dy) > 1.0d)
			{
				// Camera right = x*cos(yaw) + y*sin(yaw), so the viewing
				// direction that centers this actor has yaw = atan2(-dx, dy).
				viewYaw = Math.floorMod((int) Math.round(Math.atan2(-dx, dy)
					* BillboardAngleUtils.BILLBOARD_FULL_CIRCLE / (2.0d * Math.PI)),
					BillboardAngleUtils.BILLBOARD_FULL_CIRCLE);
			}
		}
		return Math.floorMod(viewYaw + BillboardAngleUtils.angleToBillboardUnits(actorOrientation,
			BillboardAngleUtils.ACTOR_FULL_CIRCLE), BillboardAngleUtils.BILLBOARD_FULL_CIRCLE);
	}

	public int relativeGroundItemYaw()
	{
		return snappedYaw(cameraYaw());
	}

	public int relativePitch()
	{
		return relativePitch(false);
	}

	public int relativePitch(boolean lowProfile)
	{
		int rawPitch = cameraPitch();
		if (!config.enableRotationSnapping())
		{
			return -rawPitch;
		}

		int minimumPitch = lowProfile ? BillboardAngleUtils.GROUND_ITEM_MIN_PITCH : 0;
		int snappedPitch = BillboardAngleUtils.snapPitchByAngles(rawPitch, minimumPitch, config.numberOfPitchRotationAngles());
		// Invert because model pitch is applied opposite to camera pitch.
		return -snappedPitch;
	}

	public int relativePitch(Actor actor)
	{
		return relativePitch(actor, false);
	}

	public int relativePitch(Actor actor, boolean lowProfile)
	{
		if (shouldCombatSnap(actor))
		{
			return 0;
		}

		return relativePitch(lowProfile);
	}

	public int relativeGroundItemPitch()
	{
		return relativePitch(true);
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
