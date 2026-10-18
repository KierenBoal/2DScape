package com.kierenboal.npcsnap.occlusion;

import java.util.function.Predicate;

/** Cached model-space hierarchy. Rejection is conservative; leaf faces retain draw order. */
final class SceneryFaceBoundsTree
{
	private static final int LEAF_SIZE = 32;
	private final float[] x, y, z;
	private final int[] a, b, c, faces;
	private final Node root;

	SceneryFaceBoundsTree(float[] x, float[] y, float[] z, int[] a, int[] b, int[] c, int faceCount)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.a = a;
		this.b = b;
		this.c = c;
		faces = new int[faceCount];
		for (int i = 0; i < faceCount; i++) faces[i] = i;
		root = build(0, faceCount);
	}

	void markVisible(Predicate<Node> intersects, boolean[] visible)
	{
		mark(root, intersects, visible);
	}

	private void mark(Node node, Predicate<Node> intersects, boolean[] visible)
	{
		if (node == null || (node.valid && !intersects.test(node))) return;
		if (node.left == null)
		{
			for (int i = node.from; i < node.to; i++) visible[faces[i]] = true;
		}
		else
		{
			mark(node.left, intersects, visible);
			mark(node.right, intersects, visible);
		}
	}

	private Node build(int from, int to)
	{
		if (from == to) return null;
		Node node = new Node(from, to);
		for (int i = from; i < to; i++)
		{
			int face = faces[i];
			include(node, a[face]);
			include(node, b[face]);
			include(node, c[face]);
		}
		if (to - from > LEAF_SIZE)
		{
			int axis = node.maxX - node.minX >= node.maxY - node.minY ? 0 : 1;
			float extent = axis == 0 ? node.maxX - node.minX : node.maxY - node.minY;
			if (node.maxZ - node.minZ > extent) axis = 2;
			int middle = (from + to) >>> 1;
			partition(from, to - 1, middle, axis);
			node.left = build(from, middle);
			node.right = build(middle, to);
		}
		return node;
	}

	private void include(Node node, int vertex)
	{
		if (vertex < 0 || vertex >= x.length || vertex >= y.length || vertex >= z.length
			|| !Float.isFinite(x[vertex]) || !Float.isFinite(y[vertex]) || !Float.isFinite(z[vertex]))
		{
			node.valid = false;
			return;
		}
		node.minX = Math.min(node.minX, x[vertex]); node.maxX = Math.max(node.maxX, x[vertex]);
		node.minY = Math.min(node.minY, y[vertex]); node.maxY = Math.max(node.maxY, y[vertex]);
		node.minZ = Math.min(node.minZ, z[vertex]); node.maxZ = Math.max(node.maxZ, z[vertex]);
	}

	private float centroid(int face, int axis)
	{
		float[] values = axis == 0 ? x : axis == 1 ? y : z;
		int v0 = a[face], v1 = b[face], v2 = c[face];
		if (v0 < 0 || v1 < 0 || v2 < 0 || v0 >= values.length || v1 >= values.length || v2 >= values.length) return 0f;
		float result = (values[v0] + values[v1] + values[v2]) / 3f;
		return Float.isFinite(result) ? result : 0f;
	}

	private void partition(int low, int high, int middle, int axis)
	{
		while (low < high)
		{
			float pivot = centroid(faces[(low + high) >>> 1], axis);
			int left = low, right = high;
			while (left <= right)
			{
				while (left <= high && centroid(faces[left], axis) < pivot) left++;
				while (right >= low && centroid(faces[right], axis) > pivot) right--;
				if (left <= right)
				{
					int swap = faces[left]; faces[left++] = faces[right]; faces[right--] = swap;
				}
			}
			if (middle <= right) high = right;
			else if (middle >= left) low = left;
			else return;
		}
	}

	static final class Node
	{
		final int from, to;
		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
		boolean valid = true;
		Node left, right;
		Node(int from, int to) { this.from = from; this.to = to; }
	}
}
