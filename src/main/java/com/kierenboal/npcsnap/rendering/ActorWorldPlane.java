package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;

/** Projects the captured sprite's model-space bounds onto a camera-facing world plane. */
public final class ActorWorldPlane
{
	private ActorWorldPlane()
	{
	}

	/** Horizontal camera-facing right axis, using the same camera conventions as projection. */
	public static double viewingBearing(Client client, LocalPoint location)
	{
		double cameraX = client.isGpu() ? client.getCameraFpX() : client.getCameraX();
		double cameraY = client.isGpu() ? client.getCameraFpY() : client.getCameraY();
		double dx = location.getX() - cameraX, dy = location.getY() - cameraY;
		if (Math.hypot(dx, dy) > 1.0d)
		{
			return Math.atan2(-dx, dy);
		}
		return client.isGpu() ? client.getCameraFpYaw()
			: client.getCameraYaw() * (2.0d * Math.PI / BillboardAngleUtils.CAMERA_FULL_CIRCLE);
	}

	public static BillboardDrawGeometry project(BillboardDepthCalculator calculator, LocalPoint location, double baseWorldZ,
		double viewingBearing, Rectangle image, Rectangle content)
	{
		if (location == null || image == null || image.isEmpty() || content == null || content.isEmpty()
			|| !Double.isFinite(baseWorldZ) || !Double.isFinite(viewingBearing))
		{
			return null;
		}
		// Sprite selection has already applied actor rotation and snapping. The
		// sheet itself faces the camera on yaw, independently of that captured view.
		double bearing = viewingBearing;
		double rightX = Math.cos(bearing), rightY = Math.sin(bearing);
		// Bounds are in captured model units, not texture pixels. Preserve both
		// dimensions and their offset from model zero, including outline padding.
		double left = image.x, right = image.getMaxX();
		double top = image.y, bottom = image.getMaxY();
		return BillboardDrawGeometry.projected(BillboardProjectedQuad.create(
			corner(calculator, location, baseWorldZ, rightX, rightY, left, top),
			corner(calculator, location, baseWorldZ, rightX, rightY, right, top),
			corner(calculator, location, baseWorldZ, rightX, rightY, right, bottom),
			corner(calculator, location, baseWorldZ, rightX, rightY, left, bottom), image, content));
	}

	private static BillboardCanvasPoint corner(BillboardDepthCalculator calculator, LocalPoint location,
		double baseWorldZ, double rightX, double rightY, double horizontal, double vertical)
	{
		return calculator.projectCanvasPoint(location.getX() + rightX * horizontal,
			location.getY() + rightY * horizontal, baseWorldZ + vertical);
	}
}
