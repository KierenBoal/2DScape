package com.kierenboal.npcsnap;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
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
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.StatChanged;
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
	private final Map<Integer, int[]> originalTexturePixels = new HashMap<>();
	private final Runnable restoreFrameListener = this::restoreNpcState;
	private boolean textureBandingApplied;
	private boolean textureBandingPending = true;
	private boolean pendingSkillXpSeed;
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
	private SkillingThoughtBubbleOverlay skillingThoughtBubbleOverlay;

	@Inject
	private SkillingActivityTracker skillingActivityTracker;

	@Inject
	private NpcSnapDebug debug;

	@Inject
	private AnimationFrameSnapper animationFrameSnapper;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Override
	protected void startUp()
	{
		textureBandingPending = true;
		pendingSkillXpSeed = client.getGameState() == GameState.LOGGED_IN;
		drawManager.registerEveryFrameListener(restoreFrameListener);
		overlayManager.add(billboardOverlay);
		overlayManager.add(skillingThoughtBubbleOverlay);
		renderCallbackManager.register(this);
		log.debug("2DScape started");
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(this);
		overlayManager.remove(skillingThoughtBubbleOverlay);
		overlayManager.remove(billboardOverlay);
		drawManager.unregisterEveryFrameListener(restoreFrameListener);
		restoreNpcState();
		billboardOverlay.clearGroundItems();
		billboardOverlay.clearTileObjects();
		billboardOverlay.clearTextureCache();
		skillingActivityTracker.clear();
		pendingSkillXpSeed = false;
		animationFrameSnapper.clear();
		debug.clearFrameStates();
		restoreGlobalTextureQuality();
		log.debug("2DScape stopped");
	}

	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender)
	{
		restoreNpcState();
		debug.clearFrameStates();
		billboardOverlay.beginFrame();

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

		if (config.applyToGroundItems())
		{
			billboardOverlay.syncGroundItems(worldView);
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
	public void onStatChanged(StatChanged statChanged)
	{
		long timeoutMillis = Math.max(1, config.skillingTimeoutSeconds()) * 1000L;
		skillingActivityTracker.recordXp(statChanged.getSkill(), statChanged.getXp(), System.currentTimeMillis(), timeoutMillis);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (!pendingSkillXpSeed || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		seedCurrentSkillXp();
		pendingSkillXpSeed = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			skillingActivityTracker.clear();
			pendingSkillXpSeed = true;
		}
		else
		{
			skillingActivityTracker.clear();
			pendingSkillXpSeed = false;
		}
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
			billboardOverlay.clearTextureCache();
		}
	}

	@Override
	public boolean addEntity(Renderable renderable, boolean drawingUi)
	{
		if (!config.enable2dBillboardSprites() || drawingUi)
		{
			return true;
		}

		// Actors must stay in the scene entity pipeline so right-click targeting still works.
		// Their visual replacement happens without suppressing addEntity().
		if (renderable instanceof NPC || renderable instanceof Player)
		{
			return true;
		}

		return !billboardOverlay.shouldHideRenderable(renderable);
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
			if (config.applyToObjects() || config.applyToGraphicsObjects())
			{
				billboardOverlay.observeTileObject(tileObject);
				if (billboardOverlay.shouldHideTileObject(tileObject))
				{
					return false;
				}
			}

			return !billboardOverlay.shouldHideRenderable(((GameObject) tileObject).getRenderable());
		}

		if (tileObject instanceof GroundObject)
		{
			if (config.applyToObjects() || config.applyToGraphicsObjects())
			{
				billboardOverlay.observeTileObject(tileObject);
				if (billboardOverlay.shouldHideTileObject(tileObject))
				{
					return false;
				}
			}

			return !billboardOverlay.shouldHideRenderable(((GroundObject) tileObject).getRenderable());
		}

		if (tileObject instanceof DecorativeObject)
		{
			if (config.applyToObjects() || config.applyToGraphicsObjects())
			{
				billboardOverlay.observeTileObject(tileObject);
				if (billboardOverlay.shouldHideTileObject(tileObject))
				{
					return false;
				}
			}

			return !billboardOverlay.shouldHideRenderable(((DecorativeObject) tileObject).getRenderable())
				&& !billboardOverlay.shouldHideRenderable(((DecorativeObject) tileObject).getRenderable2());
		}

		if (tileObject instanceof WallObject)
		{
			if (config.applyToObjects() || config.applyToGraphicsObjects())
			{
				billboardOverlay.observeTileObject(tileObject);
				if (billboardOverlay.shouldHideTileObject(tileObject))
				{
					return false;
				}
			}

			return !billboardOverlay.shouldHideRenderable(((WallObject) tileObject).getRenderable1())
				&& !billboardOverlay.shouldHideRenderable(((WallObject) tileObject).getRenderable2());
		}

		if (tileObject instanceof ItemLayer)
		{
			return !billboardOverlay.shouldHideRenderable(((ItemLayer) tileObject).getBottom())
				&& !billboardOverlay.shouldHideRenderable(((ItemLayer) tileObject).getMiddle())
				&& !billboardOverlay.shouldHideRenderable(((ItemLayer) tileObject).getTop());
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
		int snappedAnimationFrame = animationFrameSnapper.snapAnimationFrame(actor.getAnimation(), originalAnimationFrame, actionFrameCount);
		int originalPoseFrame = actor.getPoseAnimationFrame();
		int snappedPoseFrame = animationFrameSnapper.snapAnimationFrame(actor.getPoseAnimation(), originalPoseFrame, actionFrameCount);
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

	private void seedCurrentSkillXp()
	{
		for (net.runelite.api.Skill skill : skillingActivityTracker.getTrackedSkills())
		{
			skillingActivityTracker.seedXp(skill, client.getSkillExperience(skill));
		}
	}

	@Value
	private static class RenderState
	{
		int animationFrame;
		int poseAnimationFrame;
	}
}
