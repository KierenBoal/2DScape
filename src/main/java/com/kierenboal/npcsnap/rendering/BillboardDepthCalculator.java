package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.ObservedTileObject;
import com.kierenboal.npcsnap.targeting.WorldViewLocationResolver;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Perspective;
import net.runelite.api.Projectile;
import net.runelite.api.Point;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;

public final class BillboardDepthCalculator
{
	private static final double MIN_CANVAS_PROJECTION_DEPTH = 50.0d;
	private final Client client;
	private Camera frameCamera;

	public void beginFrame()
	{
		// BeforeRender precedes the scene's final camera, viewport and zoom setup.
		frameCamera = null;
	}

	public void beginRender()
	{
		frameCamera = new Camera(client);
	}

	public void endRender()
	{
		frameCamera = null;
	}

	public String projectionDebugState()
	{
		Camera camera = camera();
		return "gpu=" + camera.gpu + " camera=(" + camera.x + "," + camera.y + "," + camera.z
			+ ") scale=" + camera.scale + " viewport=(" + camera.viewportX + "," + camera.viewportY
			+ "," + camera.width + "," + camera.height + ")";
	}

	private Camera camera()
	{
		return frameCamera != null ? frameCamera : new Camera(client);
	}

	private static final class Camera
	{
		final boolean gpu;
		final int fixedYawSin, fixedYawCos, fixedPitchSin, fixedPitchCos;
		final double x, y, z, yawSin, yawCos, pitchSin, pitchCos, scale;
		final double distanceX, distanceY, distanceZ;
		final int yaw, pitch;
		final int viewportX, viewportY, width, height;
		Camera(Client client)
		{
			gpu = client.isGpu();
			x = gpu ? client.getCameraFpX() : client.getCameraX();
			y = gpu ? client.getCameraFpY() : client.getCameraY();
			z = gpu ? client.getCameraFpZ() : client.getCameraZ();
			yaw = Math.floorMod(client.getCameraYaw(), Perspective.SINE14.length);
			pitch = Math.floorMod(client.getCameraPitch(), Perspective.SINE14.length);
			fixedYawSin = Perspective.SINE14[yaw]; fixedYawCos = Perspective.COSINE14[yaw];
			fixedPitchSin = Perspective.SINE14[pitch]; fixedPitchCos = Perspective.COSINE14[pitch];
			// Radial paint ordering retains its existing floating-point origin.
			distanceX = client.getCameraFpX(); distanceY = client.getCameraFpY(); distanceZ = client.getCameraFpZ();
			yawSin = gpu ? Math.sin(client.getCameraFpYaw()) : Perspective.SINE14[yaw] / 65536d;
			yawCos = gpu ? Math.cos(client.getCameraFpYaw()) : Perspective.COSINE14[yaw] / 65536d;
			pitchSin = gpu ? Math.sin(client.getCameraFpPitch()) : Perspective.SINE14[pitch] / 65536d;
			pitchCos = gpu ? Math.cos(client.getCameraFpPitch()) : Perspective.COSINE14[pitch] / 65536d;
			scale = client.getScale();
			viewportX = client.getViewportXOffset(); viewportY = client.getViewportYOffset();
			width = client.getViewportWidth(); height = client.getViewportHeight();
		}
	}

	public BillboardDepthCalculator(Client client)
	{
		this.client = client;
	}

	/** Matches RuneLite Perspective's integer CPU and float GPU rasterization. */
	public Point localToCanvas(int x, int y, int z)
	{
		int extension = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
		int minimum = -extension << Perspective.LOCAL_COORD_BITS;
		int maximum = (Constants.SCENE_SIZE + extension) << Perspective.LOCAL_COORD_BITS;
		if (x < minimum || y < minimum || x > maximum || y > maximum) return null;
		Camera camera = camera();
		if (camera.gpu)
		{
			float fx = x - (float) camera.x, fy = y - (float) camera.y, fz = z - (float) camera.z;
			float ys = (float) camera.yawSin, yc = (float) camera.yawCos;
			float ps = (float) camera.pitchSin, pc = (float) camera.pitchCos;
			float right = fx * yc + fy * ys;
			float forward = fy * yc - fx * ys;
			float vertical = fz * pc - forward * ps;
			float depth = forward * pc + fz * ps;
			if (depth < MIN_CANVAS_PROJECTION_DEPTH) return null;
			return new Point(camera.viewportX + Math.round(camera.width / 2f + right * (int) camera.scale / depth),
				camera.viewportY + Math.round(camera.height / 2f + vertical * (int) camera.scale / depth));
		}
		x -= (int) camera.x; y -= (int) camera.y; z -= (int) camera.z;
		int right = (x * camera.fixedYawCos + y * camera.fixedYawSin) >> 16;
		int forward = (y * camera.fixedYawCos - x * camera.fixedYawSin) >> 16;
		int vertical = (z * camera.fixedPitchCos - forward * camera.fixedPitchSin) >> 16;
		int depth = (forward * camera.fixedPitchCos + z * camera.fixedPitchSin) >> 16;
		return depth < MIN_CANVAS_PROJECTION_DEPTH ? null
			: new Point(camera.viewportX + camera.width / 2 + right * (int) camera.scale / depth,
				camera.viewportY + camera.height / 2 + vertical * (int) camera.scale / depth);
	}

	public double depth(Projectile projectile)
	{
		// Use the same flight origin as the sprite, independent of animated height.
		return cameraDistance(projectile.getX(), projectile.getY(), projectile.getZ());
	}

	public double depth(GraphicsObject graphicsObject)
	{
		LocalPoint localPoint = graphicsObject.getLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		double verticalOffset = BillboardEffectGeometry.graphicsVerticalOffset(client, graphicsObject)
			+ (graphicsObject.getModelHeight() / 2.0);
		return cameraDistance(localPoint, graphicsObject.getLevel(), verticalOffset);
	}

	public double depth(ActorSpotAnim actorSpotAnim, Actor actor)
	{
		if (actor != null && actor.getWorldView() != null && !actor.getWorldView().isTopLevel())
		{
			WorldViewLocationResolver.ResolvedLocation location = WorldViewLocationResolver.resolve(client, actor);
			return location == null ? Double.NEGATIVE_INFINITY
				: cameraDistance(location.mainPoint.getX(), location.mainPoint.getY(), location.height
					- BillboardEffectGeometry.actorSpotVerticalOffset(actorSpotAnim) - actorSpotAnim.getModelHeight() / 2d);
		}
		LocalPoint localPoint = actor != null
			? WorldViewLocationResolver.toMainWorld(client, actor)
			: null;
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		double verticalOffset = BillboardEffectGeometry.actorSpotVerticalOffset(actorSpotAnim);
		int plane = actor.getWorldView() != null && actor.getWorldView().isTopLevel()
			? actor.getWorldView().getPlane()
			: 0;
		return cameraDistance(localPoint, plane,
			verticalOffset + (actorSpotAnim.getModelHeight() / 2.0));
	}

	public double depth(TileItem item, GroundItemBillboard groundItem)
	{
		if (groundItem == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(
			groundItem.localPoint,
			groundItem.plane,
			groundItem.verticalOffset + (item.getModelHeight() / 2.0));
	}

	public double depth(ObservedTileObject observed)
	{
		double nearest = Double.POSITIVE_INFINITY;
		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.localPoint == null || part.renderable == null)
			{
				continue;
			}

			// Sort scenery by its fixed world origin, independently of the live
			// animated height while a previously captured sprite is displayed.
			double distance;
			if (part.localPoint.getWorldView() != net.runelite.api.WorldView.TOPLEVEL)
			{
				net.runelite.api.WorldView view = client.getWorldView(part.localPoint.getWorldView());
				if (view == null) continue;
				double height = part.worldHeight != Integer.MIN_VALUE ? part.worldHeight
					: view.getTileHeight(part.localPoint.getX(), part.localPoint.getY(), part.plane);
				WorldViewLocationResolver.ResolvedLocation location = WorldViewLocationResolver.resolve(client, view, part.localPoint, part.plane, height);
				if (location == null) continue;
				distance = cameraDistance(location.mainPoint.getX(), location.mainPoint.getY(), location.height);
			}
			else distance = cameraDistance(part.localPoint, part.plane, 0);
			if (distance < nearest)
			{
				nearest = distance;
			}
		}

		return Double.isFinite(nearest) ? nearest : Double.NEGATIVE_INFINITY;
	}

	public double cameraDistance(LocalPoint localPoint, int plane, double verticalOffset)
	{
		double groundHeight = Perspective.getTileHeight(client, localPoint, plane);
		return cameraDistance(localPoint.getX(), localPoint.getY(), worldHeight(groundHeight, verticalOffset));
	}

	private double cameraDistance(double localX, double localY, double worldZ)
	{
		Camera camera = camera();
		double dx = localX - camera.distanceX;
		double dy = localY - camera.distanceY;
		double dz = worldZ - camera.distanceZ;
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	/**
	 * Converts RuneLite's positive height offset (above the tile) to its world
	 * z coordinate. RuneLite's world z axis increases downwards, so height is
	 * subtracted from the tile height.
	 */
	public static double worldHeight(double tileHeight, double verticalOffset)
	{
		return tileHeight - verticalOffset;
	}

	public double cameraForwardDepth(LocalPoint localPoint, int plane, double verticalOffset)
	{
		if (localPoint == null)
		{
			return Double.NaN;
		}

		double worldHeight = worldHeight(Perspective.getTileHeight(client, localPoint, plane), verticalOffset);
		return cameraForwardDepth(localPoint.getX(), localPoint.getY(), worldHeight);
	}

	public double cameraForwardDepth(int localX, int localY, double worldHeight)
	{
		Camera camera = camera();
		double forward = (localY - camera.y) * camera.yawCos - (localX - camera.x) * camera.yawSin;
		return (worldHeight - camera.z) * camera.pitchSin + forward * camera.pitchCos;
	}

	public double cameraForwardDepthOnVerticalPlane(int localX, int localY, int canvasY)
	{
		Camera camera = camera();
		double x = localX - camera.x;
		double y = localY - camera.y;
		double yawSin = camera.yawSin, yawCos = camera.yawCos;
		double pitchSin = camera.pitchSin, pitchCos = camera.pitchCos;
		double cameraY = (y * yawCos) - (x * yawSin);
		double scale = camera.scale;
		if (!Double.isFinite(cameraY) || cameraY <= 0.0d || !Double.isFinite(scale) || scale <= 0.0d)
		{
			return Double.NaN;
		}

		double viewportCenterY = camera.viewportY + (camera.height / 2.0d);
		double screenSlope = (canvasY - viewportCenterY) / scale;
		double denominator = pitchCos - (screenSlope * pitchSin);
		if (!Double.isFinite(denominator) || denominator <= 0.0d)
		{
			return Double.NaN;
		}

		// Intersect the pixel's camera ray with the yaw-facing world-vertical
		// plane through the target. In camera coordinates:
		// cameraY * (cos²(pitch) + sin²(pitch)) = depth *
		// (cos(pitch) - screenSlope * sin(pitch)). Retain CPU table rounding.
		return cameraY * (pitchCos * pitchCos + pitchSin * pitchSin) / denominator;
	}

	public BillboardCanvasPoint projectCanvasPoint(LocalPoint localPoint, int plane, double verticalOffset)
	{
		if (localPoint == null || !Double.isFinite(verticalOffset))
		{
			return null;
		}

		double worldZ = worldHeight(Perspective.getTileHeight(client, localPoint, plane), verticalOffset);
		return projectCanvasPoint(localPoint.getX(), localPoint.getY(), worldZ);
	}

	public BillboardCanvasPoint projectCanvasPoint(double localX, double localY, double worldZ)
	{
		// Match Perspective.localToCanvas's GPU/CPU camera conventions, retaining
		// subpixel precision until rasterization. RuneLite's vertical axis points
		// down, so world-up is decreasing worldZ.
		Camera camera = camera();
		double cameraX = camera.x, cameraY = camera.y, cameraZ = camera.z;
		double yawSin = camera.yawSin, yawCos = camera.yawCos;
		double pitchSin = camera.pitchSin, pitchCos = camera.pitchCos;
		double translatedX = localX - cameraX;
		double translatedY = localY - cameraY;
		double translatedZ = worldZ - cameraZ;
		double cameraRight = (translatedX * yawCos) + (translatedY * yawSin);
		double cameraForward = (translatedY * yawCos) - (translatedX * yawSin);
		double cameraVertical = (translatedZ * pitchCos) - (cameraForward * pitchSin);
		double depth = (cameraForward * pitchCos) + (translatedZ * pitchSin);
		double scale = camera.scale;
		if (!Double.isFinite(depth) || depth < MIN_CANVAS_PROJECTION_DEPTH
			|| !Double.isFinite(scale) || scale <= 0.0d)
		{
			return null;
		}

		double canvasX = camera.viewportX + (camera.width / 2.0d)
			+ ((cameraRight * scale) / depth);
		double canvasY = camera.viewportY + (camera.height / 2.0d)
			+ ((cameraVertical * scale) / depth);
		// Differentiate the perspective divide with respect to world-up. Removing
		// the common scale/depth factor gives (right*sin(pitch)/depth, forward/depth)
		// for unit-length trig values. Retain the CPU table's rounding below.
		// Unlike a base-to-model-top delta, this never depends on animated height.
		return Double.isFinite(canvasX) && Double.isFinite(canvasY)
			? new BillboardCanvasPoint(canvasX, canvasY, depth,
				cameraRight * pitchSin / depth,
				cameraForward * (pitchCos * pitchCos + pitchSin * pitchSin) / depth)
			: null;
	}

	public int cameraYawIndex()
	{
		return cameraAngleIndex(camera().yaw);
	}

	public int cameraPitchIndex()
	{
		return cameraAngleIndex(camera().pitch);
	}

	private int cameraAngleIndex(int cameraAngle)
	{
		return Math.floorMod(
			(int) Math.round((Math.floorMod(cameraAngle, BillboardAngleUtils.CAMERA_FULL_CIRCLE) * (double) Perspective.SINE.length) / BillboardAngleUtils.CAMERA_FULL_CIRCLE),
			Perspective.SINE.length
		);
	}

}
