package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.AABB;
import net.runelite.api.Model;
import net.runelite.api.coords.LocalPoint;

/** Immutable model-space bounds copied before the client applies frame snapping. */
public final class ActorWorldPlane
{
	private final double centerX, centerY, centerZ;
	private final double extremeX, extremeY, extremeZ;

	public ActorWorldPlane(double centerX, double centerY, double centerZ,
		double extremeX, double extremeY, double extremeZ)
	{
		this.centerX = centerX;
		this.centerY = centerY;
		this.centerZ = centerZ;
		this.extremeX = extremeX;
		this.extremeY = extremeY;
		this.extremeZ = extremeZ;
	}

	public static ActorWorldPlane sample(Model model)
	{
		AABB box = model != null ? model.getAABB(0) : null;
		return box != null && box.getExtremeY() > 0 && (box.getExtremeX() > 0 || box.getExtremeZ() > 0)
			? new ActorWorldPlane(box.getCenterX(), box.getCenterY(), box.getCenterZ(),
				box.getExtremeX(), box.getExtremeY(), box.getExtremeZ()) : null;
	}

	/** Plane right bearing, with the captured view's residual turn limited for readability. */
	public static double rightBearing(int capturedYaw, int worldOrientation, double viewingBearing)
	{
		double bearing = capturedYaw * (2.0d * Math.PI / BillboardAngleUtils.BILLBOARD_FULL_CIRCLE)
			- worldOrientation * (2.0d * Math.PI / BillboardAngleUtils.ACTOR_FULL_CIRCLE);
		double difference = Math.atan2(Math.sin(bearing - viewingBearing), Math.cos(bearing - viewingBearing));
		return viewingBearing + Math.max(-Math.PI / 3.0d, Math.min(Math.PI / 3.0d, difference));
	}

	public BillboardDrawGeometry project(BillboardDepthCalculator calculator, LocalPoint location, double baseWorldZ,
		int worldOrientation, int capturedYaw, double viewingBearing, Rectangle image, Rectangle content)
	{
		if (location == null || image == null || image.isEmpty() || content == null || content.isEmpty()
			|| !Double.isFinite(baseWorldZ) || !Double.isFinite(viewingBearing))
		{
			return null;
		}
		double bearing = rightBearing(capturedYaw, worldOrientation, viewingBearing);
		double rightX = Math.cos(bearing), rightY = Math.sin(bearing);
		double actorAngle = worldOrientation * (2.0d * Math.PI / BillboardAngleUtils.ACTOR_FULL_CIRCLE);
		// Project the eight unrotated AABB corners onto the sheet's horizontal axis.
		double localAngle = bearing + actorAngle;
		double localRightX = Math.cos(localAngle), localRightZ = Math.sin(localAngle);
		double center = centerX * localRightX + centerZ * localRightZ;
		double halfWidth = Math.abs(localRightX) * extremeX + Math.abs(localRightZ) * extremeZ;
		double width = 2.0d * halfWidth, height = 2.0d * extremeY;
		if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
		{
			return null;
		}
		// Map unpadded content to the live box, extending the same map over padding.
		double left = center - halfWidth + (image.x - content.x) * width / content.width;
		double right = center - halfWidth + (image.getMaxX() - content.x) * width / content.width;
		double top = centerY - extremeY + (image.y - content.y) * height / content.height;
		double bottom = centerY - extremeY + (image.getMaxY() - content.y) * height / content.height;
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
