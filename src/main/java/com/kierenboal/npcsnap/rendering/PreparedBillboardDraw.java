package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.occlusion.SupportingFootprint;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.TileObject;

public final class PreparedBillboardDraw
{
	public final BillboardRenderRequest request;
	public final BillboardRenderResult result;
	public final int paintOrder;
	public final BufferedImage image;
	public final Rectangle bounds;
	public final Rectangle sourceBounds;
	public final BillboardDrawGeometry geometry;
	public final TileObject occlusionIgnoredTileObject;
	public final SupportingFootprint supportingGround;
	public final int surfaceOcclusionRow;
	public final int modelHeight;
	public final boolean supportsWorldOcclusion;

	public PreparedBillboardDraw shadowDraw()
	{
		return result == null || result.shadow == null ? null :
			new PreparedBillboardDraw(this,result.shadow);
	}
	private PreparedBillboardDraw(PreparedBillboardDraw parent, BillboardRenderResult result)
	{
		this.request=parent.request; this.result=result; this.paintOrder=parent.paintOrder;
		this.image=result.image; this.bounds=result.bounds; this.sourceBounds=result.sourceBounds;
		this.geometry=result.geometry; this.occlusionIgnoredTileObject=parent.occlusionIgnoredTileObject;
		// Ground/water submersion clips the body, not its projected shadow.
		this.surfaceOcclusionRow=Integer.MAX_VALUE; this.modelHeight=Math.max(1,parent.modelHeight);
		this.supportsWorldOcclusion=true;
		this.supportingGround=parent.supportingGround;
	}

	public Rectangle visualBounds()
	{
		Rectangle visual=new Rectangle(bounds);
		for(BillboardRenderResult layer=result==null ? null : result.shadow; layer!=null; layer=layer.shadow)
			if(layer.bounds!=null) visual=visual.union(layer.bounds);
		return visual;
	}

	public PreparedBillboardDraw(BillboardRenderRequest request, BillboardRenderResult result, int paintOrder)
	{
		this(request, result, paintOrder, null);
	}

	public PreparedBillboardDraw(
		BillboardRenderRequest request,
		BillboardRenderResult result,
		int paintOrder,
		TileObject occlusionIgnoredTileObject)
	{
		this(request, result, paintOrder, occlusionIgnoredTileObject, Integer.MAX_VALUE);
	}

	public PreparedBillboardDraw(
		BillboardRenderRequest request,
		BillboardRenderResult result,
		int paintOrder,
		TileObject occlusionIgnoredTileObject,
		int surfaceOcclusionRow)
	{
		this.request = request;
		this.result = result;
		this.paintOrder = paintOrder;
		this.image = result != null ? result.image : null;
		this.bounds = result != null ? result.bounds : null;
		this.sourceBounds = result != null ? result.sourceBounds : null;
		this.geometry = result != null ? result.geometry : null;
		this.occlusionIgnoredTileObject = occlusionIgnoredTileObject;
		this.surfaceOcclusionRow = surfaceOcclusionRow;
		this.supportingGround=SupportingFootprint.capture(request,occlusionIgnoredTileObject);
		// Animated models can reuse their model and vertex buffers on the next
		// getModel() call. Capture these scalars before preparing other entities
		// or gathering scenery; compositing must not reclassify overwritten data.
		boolean worldObjectSprite = occlusionIgnoredTileObject != null;
		// A world-object sprite always uses a camera-facing depth plane. Its live
		// animation can be flat or have stale/zero height while an upright cached
		// sprite is displayed; those frames must not bypass scenery occlusion.
		int liveHeight = BillboardDepthSurface.modelHeight(request);
		this.modelHeight = worldObjectSprite ? Math.max(1, liveHeight) : liveHeight;
		this.supportsWorldOcclusion = worldObjectSprite
			|| (modelHeight > 0 && BillboardDepthSurface.supportsWorldOcclusion(request));
	}
}

