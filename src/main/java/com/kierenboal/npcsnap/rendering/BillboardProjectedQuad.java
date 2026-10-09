package com.kierenboal.npcsnap.rendering;

import java.awt.Polygon;
import java.awt.Rectangle;
import net.runelite.api.Point;

/** A world parallelogram projected through the camera, including its perspective divide. */
public final class BillboardProjectedQuad
{
	public final Rectangle bounds;
	private final double[] forward;
	private final double[] inverse;
	private final double contentCenterU;
	private final double contentTopV;

	private BillboardProjectedQuad(Rectangle bounds, double[] forward, double[] inverse,
		double contentCenterU, double contentTopV)
	{
		this.bounds = bounds;
		this.forward = forward;
		this.inverse = inverse;
		this.contentCenterU = contentCenterU;
		this.contentTopV = contentTopV;
	}

	public static BillboardProjectedQuad create(BillboardCanvasPoint topLeft, BillboardCanvasPoint topRight,
		BillboardCanvasPoint bottomRight, BillboardCanvasPoint bottomLeft, Rectangle image, Rectangle content)
	{
		BillboardCanvasPoint[] corners = {topLeft, topRight, bottomRight, bottomLeft};
		if (image == null || image.isEmpty() || content == null || content.isEmpty())
		{
			return null;
		}
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		double area = 0.0d;
		for (int i = 0; i < 4; i++)
		{
			BillboardCanvasPoint p = corners[i];
			if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y)
				|| !Double.isFinite(p.depth) || p.depth < 50.0d
				|| Math.abs(p.x) > 1_000_000 || Math.abs(p.y) > 1_000_000)
			{
				return null;
			}
			minX = Math.min(minX, p.x);
			minY = Math.min(minY, p.y);
			maxX = Math.max(maxX, p.x);
			maxY = Math.max(maxY, p.y);
		}
		for (int i = 0; i < 4; i++)
		{
			BillboardCanvasPoint a = corners[i], b = corners[(i + 1) % 4];
			area += a.x * b.y - b.x * a.y;
		}
		if (Math.abs(area) < 2.0d || maxX - minX < 1.0d || maxY - minY < 1.0d
			|| Math.ceil(maxX) - Math.floor(minX) > 8192 || Math.ceil(maxY) - Math.floor(minY) > 8192)
		{
			return null;
		}
		// Homogeneous camera coordinates are affine over a world plane. Columns
		// are its two edges and origin; division by the third row supplies depth.
		double x = topLeft.x * topLeft.depth, y = topLeft.y * topLeft.depth;
		double[] h = {
			topRight.x * topRight.depth - x, bottomLeft.x * bottomLeft.depth - x, x,
			topRight.y * topRight.depth - y, bottomLeft.y * bottomLeft.depth - y, y,
			topRight.depth - topLeft.depth, bottomLeft.depth - topLeft.depth, topLeft.depth
		};
		double[] inv = invert(h);
		if (inv == null)
		{
			return null;
		}
		Rectangle bounds = new Rectangle((int) Math.floor(minX), (int) Math.floor(minY),
			(int) Math.ceil(maxX) - (int) Math.floor(minX), (int) Math.ceil(maxY) - (int) Math.floor(minY));
		return new BillboardProjectedQuad(bounds, h, inv,
			(content.getCenterX() - image.x) / image.width, (content.y - image.y) / (double) image.height);
	}

	/** Reusable output avoids allocating in the pixel loop. Depth is camera-forward depth. */
	public static final class Sample
	{
		public double u;
		public double v;
		public double depth;
	}

	public boolean sample(double x, double y, Sample result)
	{
		double reciprocalDepth = inverse[6] * x + inverse[7] * y + inverse[8];
		if (!Double.isFinite(reciprocalDepth) || reciprocalDepth <= 0.0d)
		{
			return false;
		}
		result.u = (inverse[0] * x + inverse[1] * y + inverse[2]) / reciprocalDepth;
		result.v = (inverse[3] * x + inverse[4] * y + inverse[5]) / reciprocalDepth;
		result.depth = 1.0d / reciprocalDepth;
		return result.u >= 0.0d && result.u < 1.0d && result.v >= 0.0d && result.v < 1.0d
			&& Double.isFinite(result.depth);
	}

	public double depthAt(double u, double v)
	{
		return forward[6] * u + forward[7] * v + forward[8];
	}

	public Point canvasPoint(double u, double v)
	{
		double depth = depthAt(u, v);
		return new Point((int) Math.round((forward[0] * u + forward[1] * v + forward[2]) / depth),
			(int) Math.round((forward[3] * u + forward[4] * v + forward[5]) / depth));
	}

	public Point contentTopCenter()
	{
		return canvasPoint(contentCenterU, contentTopV);
	}

	public Polygon polygon()
	{
		Point a = canvasPoint(0, 0), b = canvasPoint(1, 0), c = canvasPoint(1, 1), d = canvasPoint(0, 1);
		return new Polygon(new int[] {a.getX(), b.getX(), c.getX(), d.getX()},
			new int[] {a.getY(), b.getY(), c.getY(), d.getY()}, 4);
	}

	private static double[] invert(double[] m)
	{
		double[] cofactors = {
			m[4] * m[8] - m[5] * m[7], m[2] * m[7] - m[1] * m[8], m[1] * m[5] - m[2] * m[4],
			m[5] * m[6] - m[3] * m[8], m[0] * m[8] - m[2] * m[6], m[2] * m[3] - m[0] * m[5],
			m[3] * m[7] - m[4] * m[6], m[1] * m[6] - m[0] * m[7], m[0] * m[4] - m[1] * m[3]
		};
		double determinant = m[0] * cofactors[0] + m[1] * cofactors[3] + m[2] * cofactors[6];
		if (!Double.isFinite(determinant) || Math.abs(determinant) < 1e-8d)
		{
			return null;
		}
		for (int i = 0; i < 9; i++)
		{
			cofactors[i] /= determinant;
			if (!Double.isFinite(cofactors[i]))
			{
				return null;
			}
		}
		return cofactors;
	}
}
