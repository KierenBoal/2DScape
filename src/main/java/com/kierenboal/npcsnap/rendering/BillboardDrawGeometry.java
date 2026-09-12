package com.kierenboal.npcsnap.rendering;

import java.awt.Polygon;
import java.awt.Rectangle;
import net.runelite.api.Point;

public final class BillboardDrawGeometry
{
	public final Rectangle unskewedBounds;
	public final Rectangle bounds;
	private final double contentTopY;
	private final double contentBottomY;
	private final double contentCenterX;
	private final double topOffsetX;
	private final double bottomOffsetX;

	private BillboardDrawGeometry(
		Rectangle unskewedBounds,
		double contentCenterX,
		double contentTopY,
		double contentBottomY,
		double topOffsetX,
		double bottomOffsetX)
	{
		this.unskewedBounds = new Rectangle(unskewedBounds);
		this.contentCenterX = contentCenterX;
		this.contentTopY = contentTopY;
		this.contentBottomY = contentBottomY;
		this.topOffsetX = Double.isFinite(topOffsetX) ? topOffsetX : 0.0d;
		this.bottomOffsetX = Double.isFinite(bottomOffsetX) ? bottomOffsetX : 0.0d;
		this.bounds = enclosingBounds();
	}

	public static BillboardDrawGeometry rectangular(Rectangle bounds)
	{
		if (bounds == null)
		{
			return null;
		}
		return new BillboardDrawGeometry(bounds, bounds.getCenterX(), bounds.y, bounds.y + bounds.height, 0.0d, 0.0d);
	}

	public static BillboardDrawGeometry sheared(
		Rectangle bounds,
		double contentCenterX,
		double contentTopY,
		double contentBottomY,
		double topOffsetX,
		double bottomOffsetX)
	{
		if (bounds == null || bounds.isEmpty() || !Double.isFinite(contentCenterX) || !Double.isFinite(contentTopY)
			|| !Double.isFinite(contentBottomY) || contentBottomY <= contentTopY
			|| !Double.isFinite(topOffsetX) || !Double.isFinite(bottomOffsetX))
		{
			return rectangular(bounds);
		}
		return new BillboardDrawGeometry(
			bounds, contentCenterX, contentTopY, contentBottomY, topOffsetX, bottomOffsetX);
	}

	public boolean isSheared()
	{
		return topOffsetX != 0.0d || bottomOffsetX != 0.0d;
	}

	public static BillboardDrawGeometry actorSkew(
		Rectangle drawBounds, Rectangle imageBounds, Rectangle contentBounds, double slope)
	{
		if (drawBounds == null || imageBounds == null || imageBounds.isEmpty()
			|| contentBounds == null || contentBounds.isEmpty() || !Double.isFinite(slope))
		{
			return rectangular(drawBounds);
		}

		double scaleX = drawBounds.width / (double) imageBounds.width;
		double scaleY = drawBounds.height / (double) imageBounds.height;
		double contentTop = drawBounds.y + (contentBounds.y - imageBounds.y) * scaleY;
		double contentBottom = drawBounds.y + (contentBounds.getMaxY() - imageBounds.y) * scaleY;
		double contentCenter = drawBounds.x + (contentBounds.getCenterX() - imageBounds.x) * scaleX;
		// Pivot around model-local zero. The bottom of a pitched or animated sprite
		// is not the actor's ground point, and may lie on either side of this pivot.
		return sheared(drawBounds, contentCenter, contentTop, contentBottom,
			-contentBounds.y * scaleY * slope, -contentBounds.getMaxY() * scaleY * slope);
	}

	public int rowLeftAt(int canvasY)
	{
		return unskewedBounds.x + (int) Math.round(offsetAt(canvasY + 0.5d));
	}

	public double offsetAt(double canvasY)
	{
		if (!isSheared())
		{
			return 0.0d;
		}
		double fractionFromTop = (canvasY - contentTopY) / (contentBottomY - contentTopY);
		return topOffsetX + ((bottomOffsetX - topOffsetX) * fractionFromTop);
	}

	public int sourceXAt(int canvasX, int canvasY, int sourceWidth)
	{
		if (sourceWidth <= 0 || unskewedBounds.width <= 0)
		{
			return -1;
		}
		int rowX = canvasX - rowLeftAt(canvasY);
		if (rowX < 0 || rowX >= unskewedBounds.width)
		{
			return -1;
		}
		return (int) (((long) rowX * sourceWidth) / unskewedBounds.width);
	}

	public int sourceYAt(int canvasY, int sourceHeight)
	{
		if (sourceHeight <= 0 || unskewedBounds.height <= 0)
		{
			return -1;
		}
		int rowY = canvasY - unskewedBounds.y;
		if (rowY < 0 || rowY >= unskewedBounds.height)
		{
			return -1;
		}
		return (int) (((long) rowY * sourceHeight) / unskewedBounds.height);
	}

	public Point canvasPoint(double sourceXFraction, double sourceYFraction)
	{
		double canvasY = unskewedBounds.y + (sourceYFraction * unskewedBounds.height);
		double canvasX = unskewedBounds.x + (sourceXFraction * unskewedBounds.width) + offsetAt(canvasY);
		return new Point((int) Math.round(canvasX), (int) Math.round(canvasY));
	}

	public Point contentTopCenter()
	{
		double canvasX = contentCenterX + offsetAt(contentTopY);
		return new Point((int) Math.round(canvasX), (int) Math.round(contentTopY));
	}

	public Polygon polygon()
	{
		int topY = unskewedBounds.y;
		int bottomY = unskewedBounds.y + unskewedBounds.height;
		int topOffset = (int) Math.round(offsetAt(topY));
		int bottomOffset = (int) Math.round(offsetAt(bottomY));
		return new Polygon(
			new int[] {
				unskewedBounds.x + topOffset,
				unskewedBounds.x + unskewedBounds.width + topOffset,
				unskewedBounds.x + unskewedBounds.width + bottomOffset,
				unskewedBounds.x + bottomOffset
			},
			new int[] {topY, topY, bottomY, bottomY},
			4);
	}

	private Rectangle enclosingBounds()
	{
		double topOffset = offsetAt(unskewedBounds.y);
		double bottomOffset = offsetAt(unskewedBounds.y + unskewedBounds.height);
		double minimumOffset = Math.min(topOffset, bottomOffset);
		double maximumOffset = Math.max(topOffset, bottomOffset);
		int left = (int) Math.floor(unskewedBounds.x + minimumOffset);
		int right = (int) Math.ceil(unskewedBounds.x + unskewedBounds.width + maximumOffset);
		return new Rectangle(left, unskewedBounds.y, Math.max(1, right - left), unskewedBounds.height);
	}
}
