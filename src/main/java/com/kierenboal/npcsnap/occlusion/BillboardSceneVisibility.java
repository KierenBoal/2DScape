package com.kierenboal.npcsnap.occlusion;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.experimental.Delegate;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Projection;
import net.runelite.api.Scene;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;

/** Reads the same floor range and roof groups that the renderer receives. */
@Singleton
public final class BillboardSceneVisibility
{
	private final Supplier<DrawCallbacks> callbackReader;
	private final Consumer<DrawCallbacks> callbackWriter;
	private final Supplier<GameState> gameStateReader;
	private final Consumer<BooleanSupplier> clientThreadInvoker;
	private final IdentityHashMap<Scene, Snapshot> snapshots = new IdentityHashMap<>();
	private Observer observer;
	private boolean enabled;

	@Inject
	public BillboardSceneVisibility(Client client, ClientThread clientThread)
	{
		this(client::getDrawCallbacks, client::setDrawCallbacks, client::getGameState, clientThread::invoke);
	}

	BillboardSceneVisibility(Supplier<DrawCallbacks> reader, Consumer<DrawCallbacks> writer)
	{
		this(reader, writer, () -> GameState.LOGGED_IN, task -> task.getAsBoolean());
	}

	BillboardSceneVisibility(
		Supplier<DrawCallbacks> reader,
		Consumer<DrawCallbacks> writer,
		Supplier<GameState> gameStateReader,
		Consumer<BooleanSupplier> clientThreadInvoker)
	{
		this.callbackReader = reader;
		this.callbackWriter = writer;
		this.gameStateReader = gameStateReader;
		this.clientThreadInvoker = clientThreadInvoker;
	}

	public void beginFrame()
	{
		enabled = true;
		snapshots.clear();
		DrawCallbacks active = callbackReader.get();
		if (active == observer && observer != null)
		{
			return;
		}
		observer = null;
		// 117 HD and GPU replace their callbacks while loading a scene. Changing
		// them again during LOADING can make the renderer load the scene twice.
		if (active == null || gameStateReader.get() != GameState.LOGGED_IN)
		{
			return;
		}
		Observer installedObserver = new Observer(active);
		callbackWriter.accept(installedObserver);
		observer = installedObserver;
	}

	public void stop()
	{
		clientThreadInvoker.accept(this::stopNow);
	}

	private boolean stopNow()
	{
		enabled = false;
		snapshots.clear();
		Observer installedObserver = observer;
		if (installedObserver == null || callbackReader.get() != installedObserver)
		{
			observer = null;
			return true;
		}
		if (gameStateReader.get() == GameState.LOADING)
		{
			return false;
		}
		callbackWriter.accept(installedObserver.delegate);
		observer = null;
		return true;
	}

	Snapshot snapshot(Scene scene)
	{
		return enabled && callbackReader.get() == observer ? snapshots.get(scene) : null;
	}

	public static DrawCallbacks renderer(DrawCallbacks callbacks)
	{
		return callbacks instanceof BillboardSceneVisibility.Observer
			? ((BillboardSceneVisibility.Observer) callbacks).delegate : callbacks;
	}

	String describe(Scene scene)
	{
		Snapshot snapshot = snapshot(scene);
		return snapshot == null ? "unavailable" : "min=" + snapshot.minLevel + ":current=" + snapshot.level
			+ ":max=" + snapshot.maxLevel + ":hiddenRoofIds=" + snapshot.hiddenRoofIds;
	}

	private void capture(Scene scene, int minLevel, int level, int maxLevel, Set<Integer> hidden)
	{
		if (enabled)
		{
			snapshots.put(scene, new Snapshot(minLevel, level, maxLevel, hidden));
		}
	}

	static final class Snapshot
	{
		final int minLevel;
		final int level;
		final int maxLevel;
		final Set<Integer> hiddenRoofIds;

		Snapshot(int minLevel, int level, int maxLevel, Set<Integer> hidden)
		{
			this.minLevel = minLevel;
			this.level = level;
			this.maxLevel = maxLevel;
			this.hiddenRoofIds = hidden == null ? new HashSet<>() : new HashSet<>(hidden);
		}

		boolean visible(int drawLevel, int roofId)
		{
			return drawLevel >= minLevel && drawLevel <= maxLevel
				&& (drawLevel <= level || roofId <= 0 || !hiddenRoofIds.contains(roofId));
		}
	}

	// Lombok emits ordinary forwarding methods for the complete installed API,
	// including overloaded/default methods. No reflection or renderer replacement.
	private final class Observer implements DrawCallbacks
	{
		@Delegate(excludes = SceneStart.class)
		private final DrawCallbacks delegate;

		private Observer(DrawCallbacks delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public void preSceneDraw(Scene scene, Projection projection,
			float x, float y, float z, float pitch, float yaw,
			int minLevel, int level, int maxLevel, Set<Integer> hidden)
		{
			delegate.preSceneDraw(scene, projection, x, y, z, pitch, yaw, minLevel, level, maxLevel, hidden);
			capture(scene, minLevel, level, maxLevel, hidden);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void preSceneDraw(Scene scene,
			float x, float y, float z, float pitch, float yaw,
			int minLevel, int level, int maxLevel, Set<Integer> hidden)
		{
			delegate.preSceneDraw(scene, x, y, z, pitch, yaw, minLevel, level, maxLevel, hidden);
			capture(scene, minLevel, level, maxLevel, hidden);
		}
	}

	private interface SceneStart
	{
		void preSceneDraw(Scene scene, Projection projection, float x, float y, float z, float pitch, float yaw,
			int minLevel, int level, int maxLevel, Set<Integer> hidden);
		void preSceneDraw(Scene scene, float x, float y, float z, float pitch, float yaw,
			int minLevel, int level, int maxLevel, Set<Integer> hidden);
	}
}
