package com.runelitegreeter.npcsnap;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;
import net.runelite.api.WorldView;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "2DScape",
	description = "Janky RuneScape Classic inspired graphics"
)
public class NpcSnapPlugin extends Plugin
	implements RenderCallback
{
	private final Map<Actor, RenderState> mutatedActors = new HashMap<>();
	private final Map<Integer, Integer> animationFrameCache = new HashMap<>();
	private final Map<Integer, int[]> originalTexturePixels = new HashMap<>();
	private final Runnable restoreFrameListener = this::restoreNpcState;
	private boolean groundItemsSeeded;
	private boolean textureBandingApplied;
	private boolean textureBandingPending = true;
	private int appliedTextureBands = -1;

	@Inject
	private Client client;

	@Inject
	private DrawManager drawManager;

	@Inject
	private NpcSnapConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcBillboardOverlay billboardOverlay;

	@Inject
	private NpcSnapDebug debug;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Override
	protected void startUp()
	{
		textureBandingPending = true;
		drawManager.registerEveryFrameListener(restoreFrameListener);
		overlayManager.add(billboardOverlay);
		renderCallbackManager.register(this);
		log.debug("NPC Snap started");
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(this);
		overlayManager.remove(billboardOverlay);
		drawManager.unregisterEveryFrameListener(restoreFrameListener);
		restoreNpcState();
		billboardOverlay.clearGroundItems();
		animationFrameCache.clear();
		debug.clearFrameStates();
		groundItemsSeeded = false;
		restoreGlobalTextureQuality();
		log.debug("NPC Snap stopped");
	}

	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender)
	{
		restoreNpcState();
		debug.clearFrameStates();

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		syncGlobalTextureQuality();

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		if (config.applyToGroundItems() && !groundItemsSeeded)
		{
			billboardOverlay.seedGroundItems(worldView);
			groundItemsSeeded = true;
		}

		for (NPC npc : worldView.npcs())
		{
			if (npc == null || !config.applyToNpcs())
			{
				continue;
			}

			applyAnimationFrameSnap(npc);
		}

		if (config.applyToPlayers())
		{
			for (Player player : worldView.players())
			{
				if (player == null)
				{
					continue;
				}

				applyAnimationFrameSnap(player);
			}
		}
	}

	@Provides
	NpcSnapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcSnapConfig.class);
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		billboardOverlay.trackGroundItem(itemSpawned.getItem(), itemSpawned.getTile());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		billboardOverlay.untrackGroundItem(itemDespawned.getItem());
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged itemQuantityChanged)
	{
		billboardOverlay.trackGroundItem(itemQuantityChanged.getItem(), itemQuantityChanged.getTile());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!"npc-snap".equals(configChanged.getGroup()))
		{
			return;
		}

		if ("enableGlobalTextureBanding".equals(configChanged.getKey())
			|| "globalTextureColorBands".equals(configChanged.getKey()))
		{
			textureBandingPending = true;
		}
	}

	@Override
	public boolean addEntity(Renderable renderable, boolean drawingUi)
	{
		if (!config.enable2dBillboardSprites() || drawingUi)
		{
			return true;
		}

		if (renderable instanceof NPC)
		{
			return true;
		}

		if (renderable instanceof Player)
		{
			return true;
		}

		if (renderable instanceof Projectile)
		{
			return !config.applyToProjectiles() || !billboardOverlay.shouldHideProjectile((Projectile) renderable);
		}

		if (renderable instanceof TileItem)
		{
			return !config.applyToGroundItems() || !billboardOverlay.shouldHideGroundItem((TileItem) renderable);
		}

		return true;
	}

	@Override
	public boolean drawObject(Scene scene, TileObject tileObject)
	{
		if (!config.enable2dBillboardSprites())
		{
			return true;
		}

		if (tileObject instanceof GameObject)
		{
			return shouldDrawObjectRenderable(((GameObject) tileObject).getRenderable());
		}

		if (tileObject instanceof GroundObject)
		{
			return shouldDrawObjectRenderable(((GroundObject) tileObject).getRenderable());
		}

		if (tileObject instanceof ItemLayer)
		{
			return shouldDrawItemLayer((ItemLayer) tileObject);
		}

		return true;
	}

	private boolean shouldDrawObjectRenderable(Renderable renderable)
	{
		if (renderable instanceof NPC)
		{
			return !config.applyToNpcs() || !billboardOverlay.shouldHideNpc((NPC) renderable);
		}

		if (renderable instanceof Player)
		{
			return !config.applyToPlayers() || !billboardOverlay.shouldHidePlayer((Player) renderable);
		}

		return true;
	}

	private boolean shouldDrawItemLayer(ItemLayer itemLayer)
	{
		return shouldDrawItemLayerRenderable(itemLayer.getBottom())
			&& shouldDrawItemLayerRenderable(itemLayer.getMiddle())
			&& shouldDrawItemLayerRenderable(itemLayer.getTop());
	}

	private boolean shouldDrawItemLayerRenderable(Renderable renderable)
	{
		if (renderable instanceof TileItem)
		{
			return !config.applyToGroundItems() || !billboardOverlay.shouldHideGroundItem((TileItem) renderable);
		}

		return true;
	}

	private void applyAnimationFrameSnap(Actor actor)
	{
		if (!config.enableAnimationFrameSnapping())
		{
			return;
		}

		int actionFrameCount = config.animationFrameCount();
		if (actionFrameCount <= 0)
		{
			return;
		}

		int originalAnimationFrame = actor.getAnimationFrame();
		int snappedAnimationFrame = snapAnimationFrame(actor.getAnimation(), originalAnimationFrame, actionFrameCount);
		int originalPoseFrame = actor.getPoseAnimationFrame();
		int snappedPoseFrame = snapAnimationFrame(actor.getPoseAnimation(), originalPoseFrame, actionFrameCount);
		debug.recordActorFrames(actor, actor.getAnimation(), originalAnimationFrame, snappedAnimationFrame, originalPoseFrame, snappedPoseFrame);

		if (snappedAnimationFrame == originalAnimationFrame && snappedPoseFrame == originalPoseFrame)
		{
			return;
		}

		mutatedActors.put(actor, new RenderState(originalAnimationFrame, originalPoseFrame));

		if (snappedAnimationFrame != originalAnimationFrame)
		{
			actor.setAnimationFrame(snappedAnimationFrame);
		}

		if (snappedPoseFrame != originalPoseFrame)
		{
			actor.setPoseAnimationFrame(snappedPoseFrame);
		}

		// RuneLite exposes Actor#getOrientation()/getCurrentOrientation(), but not a public setter.
		// The rotation config is kept so the supported API can be wired in immediately if that changes.
	}

	private int snapAnimationFrame(int animationId, int frame, int visibleFrameCount)
	{
		if (animationId < 0 || frame < 0 || visibleFrameCount <= 0)
		{
			return frame;
		}

		int totalFrames = animationFrameCache.computeIfAbsent(animationId, this::loadAnimationFrameCount);
		if (totalFrames <= 1 || visibleFrameCount >= totalFrames)
		{
			return frame;
		}

		int clampedFrame = Math.min(frame, totalFrames - 1);
		int snappedBucket = clampedFrame * visibleFrameCount / totalFrames;
		int snappedFrame = snappedBucket * totalFrames / visibleFrameCount;
		return Math.min(snappedFrame, totalFrames - 1);
	}

	private int loadAnimationFrameCount(int animationId)
	{
		Animation animation = client.loadAnimation(animationId);
		if (animation == null)
		{
			return -1;
		}

		return animation.getNumFrames();
	}

	private void syncGlobalTextureQuality()
	{
		boolean enabled = config.enableGlobalTextureBanding();
		int bands = config.globalTextureColorBands();
		if (!textureBandingPending
			&& textureBandingApplied == enabled
			&& (!enabled || appliedTextureBands == bands))
		{
			return;
		}

		restoreGlobalTextureQuality();
		textureBandingPending = false;

		if (enabled)
		{
			applyGlobalTextureQuality();
		}
	}

	private void applyGlobalTextureQuality()
	{
		TextureProvider textureProvider = client.getTextureProvider();
		if (textureProvider == null)
		{
			textureBandingPending = true;
			return;
		}

		Texture[] textures = textureProvider.getTextures();
		if (textures == null)
		{
			textureBandingPending = true;
			return;
		}

		resetTextureProviderCache(textureProvider);
		int bands = config.globalTextureColorBands();
		int changed = 0;
		for (int textureId = 0; textureId < textures.length; textureId++)
		{
			int[] pixels = textureProvider.load(textureId);
			Texture texture = textures[textureId];
			if (texture != null && texture.getPixels() != null)
			{
				pixels = texture.getPixels();
			}

			if (pixels == null || pixels.length == 0)
			{
				continue;
			}

			originalTexturePixels.put(textureId, pixels.clone());
			for (int i = 0; i < pixels.length; i++)
			{
				pixels[i] = NpcSnapColorBanding.snapTexturePixel(pixels[i], bands);
			}

			changed++;
		}

		textureBandingApplied = true;
		appliedTextureBands = bands;
		log.debug("Applied global texture banding to {} textures with {} bands", changed, bands);
	}

	private void restoreGlobalTextureQuality()
	{
		if (!textureBandingApplied && originalTexturePixels.isEmpty())
		{
			return;
		}

		TextureProvider textureProvider = client.getTextureProvider();
		Texture[] textures = textureProvider != null ? textureProvider.getTextures() : null;
		for (Map.Entry<Integer, int[]> entry : originalTexturePixels.entrySet())
		{
			int textureId = entry.getKey();
			int[] pixels = null;
			if (textures != null && textureId >= 0 && textureId < textures.length)
			{
				Texture texture = textures[textureId];
				if (texture != null)
				{
					pixels = texture.getPixels();
				}
			}

			if (pixels == null && textureProvider != null)
			{
				pixels = textureProvider.load(textureId);
			}

			int[] originalPixels = entry.getValue();
			if (pixels != null && pixels.length == originalPixels.length)
			{
				System.arraycopy(originalPixels, 0, pixels, 0, originalPixels.length);
			}
		}

		originalTexturePixels.clear();
		textureBandingApplied = false;
		appliedTextureBands = -1;
		if (textureProvider != null)
		{
			resetTextureProviderCache(textureProvider);
		}
	}

	private static void resetTextureProviderCache(TextureProvider textureProvider)
	{
		textureProvider.setBrightness(textureProvider.getBrightness());
	}

	private void restoreNpcState()
	{
		if (mutatedActors.isEmpty())
		{
			return;
		}

		for (Map.Entry<Actor, RenderState> entry : mutatedActors.entrySet())
		{
			Actor actor = entry.getKey();
			RenderState state = entry.getValue();
			actor.setAnimationFrame(state.getAnimationFrame());
			actor.setPoseAnimationFrame(state.getPoseAnimationFrame());
		}

		mutatedActors.clear();
	}

	@Value
	private static class RenderState
	{
		int animationFrame;
		int poseAnimationFrame;
	}
}
