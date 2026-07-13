package com.kierenboal.npcsnap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Renderable;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BillboardUpdateQueueTest
{
	@Test
	public void frontBackInsertionIsOrderedAndDeduplicated()
	{
		BillboardUpdateQueue queue = new BillboardUpdateQueue();
		BillboardTargetKey first = key();
		BillboardTargetKey second = key();

		queue.addLast(first);
		queue.addLast(first);
		queue.addFirst(second);

		assertEquals(2, queue.size());
		assertEquals(List.of(second, first), entries(queue));
		assertTrue(queue.contains(first));
	}

	@Test
	public void pollRemovesMembershipSoTargetCanBeRequeued()
	{
		BillboardUpdateQueue queue = new BillboardUpdateQueue();
		BillboardTargetKey key = key();
		queue.addLast(key);

		assertEquals(key, queue.pollFirst());
		assertFalse(queue.contains(key));
		assertNull(queue.pollFirst());
		queue.addLast(key);
		assertEquals(1, queue.size());
	}

	@Test
	public void pruneReturnsRemovedKeysAndPreservesValidOrder()
	{
		BillboardUpdateQueue queue = new BillboardUpdateQueue();
		BillboardTargetKey first = key();
		BillboardTargetKey removed = key();
		BillboardTargetKey last = key();
		queue.addLast(first);
		queue.addLast(removed);
		queue.addLast(last);

		assertEquals(List.of(removed), queue.prune(new HashSet<>(List.of(first, last))));
		assertEquals(List.of(first, last), entries(queue));
	}

	@Test
	public void replacementRefreshesDebugPositionsAndScoresRemainAvailable()
	{
		BillboardUpdateQueue queue = new BillboardUpdateQueue();
		BillboardTargetKey first = key();
		BillboardTargetKey second = key();
		queue.replaceWith(List.of(
			new QueuedBillboardTarget(second, 20d, 1),
			new QueuedBillboardTarget(first, 10d, 0)));
		queue.putDebugScore(first, 10d);
		queue.putDebugScore(second, 20d);

		assertEquals(0, queue.debugPosition(second));
		assertEquals(1, queue.debugPosition(first));
		assertEquals(20d, queue.debugScore(second), 0d);
		assertTrue(queue.hasDebugScores());
		assertEquals(Set.of(10d, 20d), new HashSet<>(queue.debugScores()));
	}

	@Test
	public void clearResetsQueueAndDebugState()
	{
		BillboardUpdateQueue queue = new BillboardUpdateQueue();
		BillboardTargetKey key = key();
		queue.addLast(key);
		queue.putDebugScore(key, 1d);

		queue.clear();

		assertTrue(queue.isEmpty());
		assertFalse(queue.contains(key));
		assertFalse(queue.hasDebugScores());
		assertEquals(-1, queue.debugPosition(key));
	}

	private static List<BillboardTargetKey> entries(BillboardUpdateQueue queue)
	{
		List<BillboardTargetKey> entries = new ArrayList<>();
		queue.forEach(entries::add);
		return entries;
	}

	private static BillboardTargetKey key()
	{
		return BillboardTargetKey.forRenderable(
			BillboardTargetType.PLAYER, TestProxies.proxy(Renderable.class), null, 0);
	}
}
