package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.TestProxies;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import org.junit.Test;

public class BillboardUpdateModelsTest
{
	@Test
	public void cadencedProjectileSnapshotIgnoresLiveFlightState()
	{
		UpdateHeuristicSnapshot snapshot = UpdateHeuristicSnapshot.cadencedProjectile(123.0d);

		assertEquals(Long.MIN_VALUE, snapshot.positionKey);
		assertEquals(1, snapshot.animationHash);
		assertEquals(1, snapshot.modelStateHash);
		assertEquals(1, snapshot.textureStateHash);
		assertEquals(1, snapshot.viewHash);
		assertTrue(snapshot.animated);
	}

	@Test
	public void cadencedProjectileUsesTheAnimationUpdateInterval()
	{
		BillboardUpdateState state = new BillboardUpdateState(1.0d);
		state.advance(100, 1, 1.0d, UpdateHeuristicSnapshot.cadencedProjectile(123.0d), 17);

		assertFalse(state.isReady(116, true));
		assertTrue(state.isReady(117, true));
	}

	@Test
	public void detectsModelChangesIndependentlyFromAnimation()
	{
		BillboardUpdateState state = new BillboardUpdateState(1.0d);
		state.observe(snapshot(100L, 10, 20, 30, 40));

		UpdateHeuristicSnapshot modelChanged = snapshot(100L, 10, 21, 30, 40);

		assertTrue(state.hasModelChanged(modelChanged));
		assertFalse(state.hasAnimationChanged(modelChanged));
		assertFalse(state.hasTextureChanged(modelChanged));
	}

	@Test
	public void detectsAnimationChangesIndependentlyFromModel()
	{
		BillboardUpdateState state = new BillboardUpdateState(1.0d);
		state.observe(snapshot(100L, 10, 20, 30, 40));

		UpdateHeuristicSnapshot animationChanged = snapshot(100L, 11, 20, 30, 40);

		assertTrue(state.hasAnimationChanged(animationChanged));
		assertFalse(state.hasModelChanged(animationChanged));
		assertFalse(state.hasTextureChanged(animationChanged));
	}

	@Test
	public void detectsViewChangesIndependentlyFromModelAndAnimation()
	{
		BillboardUpdateState state = new BillboardUpdateState(1.0d);
		state.advance(0, 8, 1.0d, snapshot(100L, 10, 20, 30, 40), 1);

		UpdateHeuristicSnapshot viewChanged = snapshot(100L, 10, 20, 30, 41);

		assertTrue(state.hasViewChangedSinceRedraw(viewChanged));
		assertFalse(state.hasAnimationChangedSinceRedraw(viewChanged));
		assertFalse(state.hasModelChangedSinceRedraw(viewChanged));
		assertFalse(state.hasTextureChangedSinceRedraw(viewChanged));
	}

	@Test
	public void modelChangeScoresAboveAnimationChange()
	{
		BillboardUpdateState modelState = observedState(snapshot(100L, 10, 20, 30, 40));
		BillboardUpdateState animationState = observedState(snapshot(100L, 10, 20, 30, 40));

		double modelScore = BillboardUpdatePriority.stateChangeScore(modelState, snapshot(100L, 10, 21, 30, 40));
		double animationScore = BillboardUpdatePriority.stateChangeScore(animationState, snapshot(100L, 11, 20, 30, 40));

		assertTrue(modelScore > animationScore);
	}

	@Test
	public void animationChangeScoresAboveMovement()
	{
		BillboardUpdateState animationState = observedState(snapshot(100L, 10, 20, 30, 40));
		BillboardUpdateState movementState = observedState(snapshot(100L, 10, 20, 30, 40));

		double animationScore = BillboardUpdatePriority.stateChangeScore(animationState, snapshot(100L, 11, 20, 30, 40));
		double movementScore = BillboardUpdatePriority.stateChangeScore(movementState, snapshot(101L, 10, 20, 30, 40));

		assertTrue(animationScore > movementScore);
	}

	@Test
	public void viewChangeReceivesPriorityBump()
	{
		BillboardUpdateState state = new BillboardUpdateState(1.0d);
		state.advance(0, 8, 1.0d, snapshot(100L, 10, 20, 30, 40), 1);

		double score = BillboardUpdatePriority.stateChangeScore(state, snapshot(100L, 10, 20, 30, 41));

		assertTrue(score >= 6_000.0d);
	}

	@Test
	public void modelPriorityPersistsUntilRedraw()
	{
		BillboardUpdateState state = new BillboardUpdateState(1.0d);
		state.advance(0, 8, 1.0d, snapshot(100L, 10, 20, 30, 40), 1);
		UpdateHeuristicSnapshot changed = snapshot(100L, 10, 21, 30, 40);
		state.observe(changed);

		double score = BillboardUpdatePriority.stateChangeScore(state, changed);

		assertTrue(score >= 25_000.0d);
	}

	@Test
	public void animationPriorityPersistsUntilRedraw()
	{
		BillboardUpdateState state = new BillboardUpdateState(1.0d);
		state.advance(0, 8, 1.0d, snapshot(100L, 10, 20, 30, 40), 1);
		UpdateHeuristicSnapshot changed = snapshot(100L, 11, 20, 30, 40);
		state.observe(changed);

		double score = BillboardUpdatePriority.stateChangeScore(state, changed);

		assertTrue(score >= 12_000.0d);
	}

	@Test
	public void queuedPlanDoesNotAdvanceStateBeforeSuccessfulRender()
	{
		BillboardUpdateState state = new BillboardUpdateState(0.5d);
		new FrameUpdatePlan(0.5d, false, state, snapshot(100L, 10, 20, 30, 40), 10, 8, 1.0d, 1);

		assertEquals(64, state.cyclesSinceRedraw(20));
		assertTrue(state.isReady(10, true));
	}

	@Test
	public void successfulRenderPlanAdvancesState()
	{
		BillboardUpdateState state = new BillboardUpdateState(0.5d);
		FrameUpdatePlan plan = new FrameUpdatePlan(0.5d, false, state, snapshot(100L, 10, 20, 30, 40), 10, 8, 1.0d, 1);

		plan.markRedrawSucceeded();

		assertEquals(0, state.cyclesSinceRedraw(10));
		assertFalse(state.isReady(13, true));
		assertTrue(state.isReady(14, true));
	}

	@Test
	public void modelOnlySnapshotIsNotAnimated()
	{
		UpdateHeuristicSnapshot snapshot = UpdateHeuristicSnapshot.fromRequest(request(-1, -1, -1, -1, -1), 100.0d, 0);

		assertFalse(snapshot.animated);
	}

	@Test
	public void animationAndTextureSnapshotsAreAnimated()
	{
		assertTrue(UpdateHeuristicSnapshot.fromRequest(request(7, -1, -1, -1, -1), 100.0d, 0).animated);
		assertTrue(UpdateHeuristicSnapshot.fromRequest(request(-1, -1, -1, -1, 3), 100.0d, 123).animated);
	}

	@Test
	public void cachedTargetsSeedAtFullQuality()
	{
		assertEquals(0.8d, BillboardUpdateScheduler.initialQualityScale(true, 0.8d, 2, 1), 0.00001d);
		assertEquals(0.2d, BillboardUpdateScheduler.initialQualityScale(false, 0.8d, 2, 1), 0.00001d);
	}

	@Test
	public void tileObjectRequiresAllRenderablePartsCached()
	{
		Renderable first = renderable();
		Renderable second = renderable();
		Set<Renderable> cached = new HashSet<>(Arrays.asList(first, second));

		assertTrue(BillboardUpdateScheduler.allTileObjectPartsCached(
			Arrays.asList(new ObjectRenderablePart(first, null, 0), new ObjectRenderablePart(second, null, 0)),
			cached::contains));

		cached.remove(second);
		assertFalse(BillboardUpdateScheduler.allTileObjectPartsCached(
			Arrays.asList(new ObjectRenderablePart(first, null, 0), new ObjectRenderablePart(second, null, 0)),
			cached::contains));
	}

	private static BillboardUpdateState observedState(UpdateHeuristicSnapshot snapshot)
	{
		BillboardUpdateState state = new BillboardUpdateState(1.0d);
		state.observe(snapshot);
		return state;
	}

	private static UpdateHeuristicSnapshot snapshot(long positionKey, int animationHash, int modelStateHash, int textureStateHash, int viewHash)
	{
		return new UpdateHeuristicSnapshot(
			100.0d,
			positionKey,
			animationHash,
			modelStateHash,
			textureStateHash,
			viewHash,
			animationHash >= 0);
	}

	private static BillboardRenderRequest request(int animationId, int animationFrame, int poseAnimationId, int poseAnimationFrame, int animatedTextureId)
	{
		return new BillboardRenderRequest(
			null,
			TestProxies.proxy(Model.class),
			null,
			0,
			0,
			0,
			0,
			animationId,
			animationFrame,
			poseAnimationId,
			poseAnimationFrame,
			animatedTextureId,
			false,
			false,
			VerticalAnchor.BOTTOM,
			null);
	}

	private static Renderable renderable()
	{
		return TestProxies.proxy(Renderable.class);
	}
}
