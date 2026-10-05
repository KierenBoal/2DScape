package com.kierenboal.npcsnap.targeting;

import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.client.callback.RenderCallbackManager;

/** Keeps sprites on a boat subject to the visibility of their parent scene. */
@Singleton
public class BillboardWorldViewVisibility
{
	private final Client client;
	private final RenderCallbackManager renderCallbacks;
	private final Map<WorldView, WorldEntity> parents = new IdentityHashMap<>();
	private final Map<Scene, Boolean> sceneVisibility = new IdentityHashMap<>();

	@Inject
	public BillboardWorldViewVisibility(Client client, RenderCallbackManager renderCallbacks)
	{
		this.client = client;
		this.renderCallbacks = renderCallbacks;
	}

	public void beginFrame()
	{
		clear();
		WorldView topLevel = client.getTopLevelWorldView();
		if (topLevel == null || topLevel.worldEntities() == null)
		{
			return;
		}
		// Only inspect the tracked boats, never the scene's tiles or models.
		for (WorldEntity entity : topLevel.worldEntities())
		{
			if (entity != null && entity.getWorldView() != null)
			{
				parents.put(entity.getWorldView(), entity);
			}
		}
	}

	public boolean isVisible(Actor actor)
	{
		return actor != null && isVisible(actor.getWorldView());
	}

	public boolean isVisible(WorldView view)
	{
		if (view == null)
		{
			return false;
		}
		if (view.isTopLevel())
		{
			return true;
		}
		WorldEntity parent = parents.get(view);
		// Overlap state may change during scene drawing, after BeforeRender.
		// Read it live even when the callback decision has already been cached.
		if (parent == null || parent.isHiddenForOverlap())
		{
			return false;
		}
		Scene scene = view.getScene();
		if (scene == null)
		{
			return false;
		}
		// Entity Hider makes its boat decision on this Scene, preserving its
		// exceptions for the local player's boat and non-player boats.
		return sceneVisibility.computeIfAbsent(scene, value -> renderCallbacks.addEntity(value, false));
	}

	public void clear()
	{
		parents.clear();
		sceneVisibility.clear();
	}
}
