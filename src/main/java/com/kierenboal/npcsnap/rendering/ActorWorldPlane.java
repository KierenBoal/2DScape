package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;

/** Projects the captured sprite's model-space bounds onto a camera-facing world plane. */
public final class ActorWorldPlane
{
	public static final double MIN_HEIGHT_RATIO = 0.25d;

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
		ProjectionBasis basis = projectionBasis(calculator, location, baseWorldZ, rightX, rightY);
		if (basis == null) return null;
		double left = image.x, right = image.getMaxX();
		double top = image.y, bottom = image.getMaxY();
		return BillboardDrawGeometry.projected(BillboardProjectedQuad.create(
			corner(calculator, location, baseWorldZ, rightX, rightY, left, top, basis),
			corner(calculator, location, baseWorldZ, rightX, rightY, right, top, basis),
			corner(calculator, location, baseWorldZ, rightX, rightY, right, bottom, basis),
			corner(calculator, location, baseWorldZ, rightX, rightY, left, bottom, basis), image, content));
	}

	public static double heightRatio(BillboardDepthCalculator calculator, LocalPoint location,
		double baseWorldZ, double viewingBearing)
	{
		ProjectionBasis basis = projectionBasis(calculator, location, baseWorldZ,
			Math.cos(viewingBearing), Math.sin(viewingBearing));
		return basis == null ? 1.0d : basis.ratio;
	}

	private static ProjectionBasis projectionBasis(BillboardDepthCalculator calculator, LocalPoint location,
		double baseWorldZ, double rightX, double rightY)
	{
		if (location == null) return null;
		BillboardCanvasPoint base = calculator.projectCanvasPoint(location.getX(), location.getY(), baseWorldZ);
		BillboardCanvasPoint right = calculator.projectCanvasPoint(location.getX() + rightX,
			location.getY() + rightY, baseWorldZ);
		if (base == null || right == null) return null;
		// A yaw-facing world plane rolls in screen space as the camera moves over
		// it. Its horizontal axis can even reverse after the camera passes overhead.
		// Resolve that roll from the final projection and remove it about model zero.
		double widthX = right.x - base.x, widthY = right.y - base.y;
		double width = Math.hypot(widthX, widthY);
		if (width < 1e-9) return null;
		double axisX = widthX / width, axisY = widthY / width;
		double height = axisY * base.verticalRight + axisX * base.verticalUp;
		double missingHeight = Math.max(0.0d, MIN_HEIGHT_RATIO - height);
		return new ProjectionBasis(base, axisX, axisY, missingHeight,
			Math.max(MIN_HEIGHT_RATIO, Math.min(1.0d, height)));
	}

	private static final class ProjectionBasis
	{
		final BillboardCanvasPoint base;
		final double axisX, axisY, missingHeight, ratio;

		ProjectionBasis(BillboardCanvasPoint base, double axisX, double axisY, double missingHeight, double ratio)
		{
			this.base = base;
			this.axisX = axisX;
			this.axisY = axisY;
			this.missingHeight = missingHeight;
			this.ratio = ratio;
		}
	}

	public static double shadowHeightRatio(double heightRatio)
	{
		// Keep actor shadows at the original default height at every camera pitch.
		return BillboardOutlineRenderer.DEFAULT_SPRITE_SHADOW_HEIGHT_RATIO;
	}

	private static BillboardCanvasPoint corner(BillboardDepthCalculator calculator, LocalPoint location,
		double baseWorldZ, double rightX, double rightY, double horizontal, double vertical, ProjectionBasis basis)
	{
		BillboardCanvasPoint point = calculator.projectCanvasPoint(location.getX() + rightX * horizontal,
			location.getY() + rightY * horizontal, baseWorldZ + vertical);
		if (point == null) return null;
		// A rotation about the feet is affine in screen space, so it preserves the
		// perspective divide, inverse mapping and per-pixel depth. Apply the height
		// floor along the now-upright screen axis, never along the rolled plane.
		double dx = point.x - basis.base.x, dy = point.y - basis.base.y;
		double lift = vertical * basis.missingHeight * calculator.projectionScale() / point.depth;
		return new BillboardCanvasPoint(
			basis.base.x + basis.axisX * dx + basis.axisY * dy,
			basis.base.y - basis.axisY * dx + basis.axisX * dy + lift,
			point.depth,
			basis.axisX * point.verticalRight - basis.axisY * point.verticalUp,
			basis.axisY * point.verticalRight + basis.axisX * point.verticalUp + basis.missingHeight);
	}
}
