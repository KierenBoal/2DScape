package com.kierenboal.npcsnap.rendering;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Actor;
import net.runelite.api.Client;

@Singleton
@Slf4j
public class AnimationFrameSnapper
{
	private static final int GAME_FRAMES_PER_SECOND = 50;
	private final Client client;
	private final Map<Integer, AnimationMetadata> animationFrameCache = new HashMap<>();
	private final Map<Actor, ActorAnimationState> actorAnimationStates = new IdentityHashMap<>();

	@Inject
	public AnimationFrameSnapper(Client client)
	{
		this.client = client;
	}

	public int snapAnimationFrame(int animationId, int frame, int visibleFrameCount)
	{
		return snapAnimationFrame(animationId, frame, visibleFrameCount, true);
	}

	public int snapAnimationFrame(int animationId, int frame, int visibleFrameCount, boolean deterministicLooping)
	{
		if (animationId < 0 || frame < 0 || visibleFrameCount <= 0)
		{
			return frame;
		}

		AnimationMetadata metadata = animationFrameCache.computeIfAbsent(animationId, this::loadAnimationMetadata);
		boolean looped = !deterministicLooping && frame >= metadata.totalFrames && hasPartialLoop(metadata);
		return snap(metadata, frame, visibleFrameCount, deterministicLooping, looped, false);
	}

	public int snapActorAnimationFrame(
		Actor actor,
		int animationId,
		int frame,
		int visibleFrameCount,
		boolean deterministicLooping,
		boolean poseAnimation)
	{
		return snapActorAnimationFrame(actor, animationId, frame, visibleFrameCount,
			deterministicLooping, poseAnimation, false);
	}

	public int snapActorAnimationFrame(
		Actor actor,
		int animationId,
		int frame,
		int visibleFrameCount,
		boolean deterministicLooping,
		boolean poseAnimation,
		boolean logAnimationData)
	{
		if (actor == null)
		{
			return frame;
		}

		ActorAnimationState state = actorAnimationStates.computeIfAbsent(actor, ignored -> new ActorAnimationState());
		AnimationTrack track = poseAnimation ? state.pose : state.action;
		if (animationId < 0 || frame < 0)
		{
			// The same emote can be played again after it has finished. Do not carry the
			// previous run's loop offset into the new animation.
			track.reset();
			if (logAnimationData)
			{
				log.debug("Billboard animation track reset: actor={}, track={}, animation={}, frame={}",
					actor, trackName(poseAnimation), animationId, frame);
			}
			return frame;
		}

		if (visibleFrameCount <= 0)
		{
			return frame;
		}

		AnimationMetadata metadata = animationFrameCache.computeIfAbsent(animationId, this::loadAnimationMetadata);
		boolean looped = hasPartialLoop(metadata)
			&& track.animationId == animationId
			&& frame < track.lastFrame
			&& track.lastFrame >= loopStart(metadata);

		if (track.animationId != animationId)
		{
			track.start(animationId, frame);
		}

		if (!deterministicLooping && hasPartialLoop(metadata))
		{
			long accumulatedFrame = accumulateActorFrame(track, metadata, frame);
			long scheduledFrame = accumulatedFrame / frameInterval(visibleFrameCount) * frameInterval(visibleFrameCount);
			int renderedFrame = animationFrameAt(metadata, scheduledFrame);
			if (logAnimationData)
			{
				log.debug("Billboard animation: actor={}, track={}, animation={}, rawFrame={}, totalFrames={}, frameStep={}, loopStart={}, detectedLoopReset={}, deterministic={}, frameInterval={}, accumulatedFrame={}, scheduledFrame={}, renderedFrame={}",
					actor, trackName(poseAnimation), animationId, frame, metadata.totalFrames, metadata.frameStep,
					loopStart(metadata), looped, false, frameInterval(visibleFrameCount), accumulatedFrame, scheduledFrame, renderedFrame);
			}
			track.lastFrame = frame;
			return renderedFrame;
		}

		int normalizedFrame = snap(metadata, frame, visibleFrameCount, deterministicLooping, false, false);
		if (logAnimationData)
		{
			log.debug("Billboard animation: actor={}, track={}, animation={}, rawFrame={}, totalFrames={}, frameStep={}, loopStart={}, detectedLoopReset={}, deterministic={}, visibleFrames={}, renderedFrame={}",
				actor, trackName(poseAnimation), animationId, frame, metadata.totalFrames, metadata.frameStep,
				loopStart(metadata), looped, deterministicLooping, visibleFrameCount, normalizedFrame);
		}
		track.lastFrame = frame;
		return normalizedFrame;
	}

	public int snapFrame(Animation animation, int frame, boolean enabled, int visibleFrameCount)
	{
		return snapFrame(animation, frame, enabled, visibleFrameCount, true);
	}

	public int snapFrame(Animation animation, int frame, boolean enabled, int visibleFrameCount, boolean deterministicLooping)
	{
		if (!enabled || animation == null || frame < 0 || visibleFrameCount <= 0)
		{
			return frame;
		}

		int totalFrames = animation.getNumFrames();
		if (totalFrames <= 1)
		{
			return frame;
		}

		AnimationMetadata metadata = new AnimationMetadata(totalFrames, animation.getFrameStep());
		boolean looped = !deterministicLooping && frame >= totalFrames && hasPartialLoop(metadata);
		return snap(metadata, frame, visibleFrameCount, deterministicLooping, looped, false);
	}

	public void clear()
	{
		animationFrameCache.clear();
		actorAnimationStates.clear();
	}

	private int snap(
		AnimationMetadata metadata,
		int frame,
		int visibleFrameCount,
		boolean deterministicLooping,
		boolean loopReferenceActive,
		boolean loopSourceStartsAtZero)
	{
		if (metadata.totalFrames <= 1)
		{
			return frame;
		}

		int normalizedFrame = normalizeFrame(frame, metadata.totalFrames, metadata.frameStep,
			deterministicLooping, loopSourceStartsAtZero);
		if (!deterministicLooping && loopReferenceActive && hasPartialLoop(metadata))
		{
			return snapRange(normalizedFrame, loopStart(metadata), metadata.frameStep, visibleFrameCount);
		}

		return snapRange(normalizedFrame, 0, metadata.totalFrames, visibleFrameCount);
	}

	private int snapRange(int frame, int rangeStart, int rangeLength, int visibleFrameCount)
	{
		if (visibleFrameCount >= rangeLength)
		{
			return Math.min(frame, rangeStart + rangeLength - 1);
		}

		int clampedFrame = Math.max(rangeStart, Math.min(frame, rangeStart + rangeLength - 1));
		int snappedBucket = (clampedFrame - rangeStart) * visibleFrameCount / rangeLength;
		return rangeStart + snappedBucket * rangeLength / visibleFrameCount;
	}

	private long accumulateActorFrame(AnimationTrack track, AnimationMetadata metadata, int frame)
	{
		if (track.lastFrame >= 0)
		{
			if (frame >= track.lastFrame)
			{
				track.accumulatedFrame += frame - track.lastFrame;
			}
			else
			{
				int cycleEnd = metadata.totalFrames - 1;
				track.accumulatedFrame += Math.max(0, cycleEnd - track.lastFrame)
					+ Math.max(0, frame - loopStart(metadata));
			}
		}

		return track.accumulatedFrame;
	}

	private int frameInterval(int framesPerSecond)
	{
		return Math.max(1, GAME_FRAMES_PER_SECOND / framesPerSecond);
	}

	private int animationFrameAt(AnimationMetadata metadata, long accumulatedFrame)
	{
		int cycleEnd = metadata.totalFrames - 1;
		if (accumulatedFrame <= cycleEnd)
		{
			return (int) accumulatedFrame;
		}

		return loopStart(metadata) + (int) ((accumulatedFrame - cycleEnd) % metadata.frameStep);
	}

	private int normalizeFrame(int frame, int totalFrames, int frameStep, boolean deterministicLooping, boolean loopSourceStartsAtZero)
	{
		if (frame < totalFrames)
		{
			if (!deterministicLooping && loopSourceStartsAtZero && frameStep > 0 && frameStep < totalFrames)
			{
				return totalFrames - frameStep + frame % frameStep;
			}

			return frame;
		}

		if (deterministicLooping)
		{
			return totalFrames - 1;
		}

		if (frameStep > 0 && frameStep <= totalFrames)
		{
			int loopStart = totalFrames - frameStep;
			return loopStart + (frame - totalFrames) % frameStep;
		}

		if (frameStep == 0)
		{
			return frame % totalFrames;
		}

		return totalFrames - 1;
	}

	private int loopStart(AnimationMetadata metadata)
	{
		return hasPartialLoop(metadata)
			? metadata.totalFrames - metadata.frameStep
			: metadata.totalFrames;
	}

	private boolean hasPartialLoop(AnimationMetadata metadata)
	{
		return metadata.frameStep > 0 && metadata.frameStep < metadata.totalFrames;
	}

	private String trackName(boolean poseAnimation)
	{
		return poseAnimation ? "pose" : "action";
	}

	private AnimationMetadata loadAnimationMetadata(int animationId)
	{
		Animation animation = client.loadAnimation(animationId);
		if (animation == null)
		{
			return new AnimationMetadata(-1, -1);
		}

		return new AnimationMetadata(animation.getNumFrames(), animation.getFrameStep());
	}

	private static final class AnimationMetadata
	{
		private final int totalFrames;
		private final int frameStep;

		private AnimationMetadata(int totalFrames, int frameStep)
		{
			this.totalFrames = totalFrames;
			this.frameStep = frameStep;
		}
	}

	private static final class ActorAnimationState
	{
		private final AnimationTrack action = new AnimationTrack();
		private final AnimationTrack pose = new AnimationTrack();
	}

	private static final class AnimationTrack
	{
		private int animationId = -1;
		private int lastFrame = -1;
		private long accumulatedFrame;

		private void start(int animationId, int frame)
		{
			this.animationId = animationId;
			lastFrame = -1;
			accumulatedFrame = frame;
		}

		private void reset()
		{
			animationId = -1;
			lastFrame = -1;
			accumulatedFrame = 0;
		}
	}
}
