package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.SpriteShadowType;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Native pixel alpha projections, matching the supplied Python experiment. */
public final class SpriteShadowRenderer
{
	private SpriteShadowRenderer() { }

	public static BufferedImage render(BufferedImage sprite, SpriteShadowType type)
	{
		return render(sprite, type, new Scratch());
	}

	public static BufferedImage render(BufferedImage sprite, SpriteShadowType type, Scratch scratch)
	{
		int w = sprite.getWidth(), h = sprite.getHeight(), pad = horizontalPadding(w,type), ow = w + 2*pad, oh = h + (type == SpriteShadowType.BLOB ? Math.max(10,(int)Math.ceil(w*0.125)+2) : 10);
		BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = sprite.getRGB(0, 0, w, h, null, 0, w);
		int top = h, feet = -1;
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
			if ((pixels[y*w+x] >>> 24) != 0) { top = Math.min(top, y); feet = y; }
		if (feet < 0 || !type.projected()) return out;
		scratch.ensure(ow*oh);
		float[] mask = scratch.mask;
		Arrays.fill(mask,0);
		boolean soft = type != SpriteShadowType.FLAT;
		int rows = soft ? 7 : 5, below = soft ? 3 : 2;
		if (type != SpriteShadowType.BLOB)
		{
			for (int i = 0; i < rows; i++)
			{
				int start = top + (int)Math.rint(i * (feet + 1 - top) / (double)rows);
				int stop = Math.min(h, Math.max(start + 1, top + (int)Math.rint((i+1) * (feet+1-top) / (double)rows)));
				int dy = feet + below - rows + 1 + i;
				if (dy < 0 || start >= h) continue;
				for (int x = 0; x < w; x++)
				{
					float sum = 0, max = 0;
					for (int y = start; y < stop; y++)
					{
						float a = (pixels[y*w+x] >>> 24) / 255f;
						sum += a; max = Math.max(max, a);
					}
					float strength = (0.30f + 0.70f * (sum / (stop-start))) * max;
					int dx = soft ? x : (int)Math.rint((w-1)/2.0 + (x-(w-1)/2.0)*0.94);
					int index = dy*ow+dx+3;
					mask[index] = Math.max(mask[index], strength);
				}
			}
			if (soft)
			{
				int[] bytes = blur(mask, ow, oh, scratch);
				for (int i=0; i<mask.length; i++)
					mask[i] = type == SpriteShadowType.HARD
						? 0.80f * (float)Math.pow(bytes[i]/255f, 0.70)
						: (float)Math.floor(bytes[i]/255f*7f+0.75f)/7f*0.56f;
			}
			else for (int i=0; i<mask.length; i++) mask[i] = mask[i]>0.31f ? 0.49f : 0;
			for (int x=0; x<w; x++)
				for (int y=Math.max(0,feet-(soft ? 4 : 3)); y<=feet; y++)
					if ((pixels[y*w+x] >>> 24)!=0)
						mask[(feet+1)*ow+x+3] = Math.max(mask[(feet+1)*ow+x+3],type == SpriteShadowType.HARD ? 0.55f : soft ? 0.32f : 0.51f);
		}
		if (type == SpriteShadowType.BLOB)
		{
			Arrays.fill(mask,0);
			int left = w, right = -1;
			for (int y=top; y<=feet; y++) for(int x=0; x<w; x++)
				if ((pixels[y*w+x] >>> 24)!=0) { left = Math.min(left,x); right = Math.max(right,x); }
			double occupiedWidth = right-left+1;
			double width = occupiedWidth*1.2, height = Math.max(3,occupiedWidth*0.9*0.25);
			double cx = (left+right)/2.0+pad, cy = feet+1;
			for (int y=0; y<oh; y++) for(int x=0; x<ow; x++)
			{
				double dx = (x-cx)/Math.max(0.5,width/2), dy = (y-cy)/(height/2);
				double radius = dx*dx+dy*dy;
				if(radius<1) mask[y*ow+x] = (float)(0.45*(1-radius)*(1-radius));
			}
		}

		for (int y=0; y<oh; y++) for (int x=0; x<ow; x++)
			out.setRGB(x,y,((int)(mask[y*ow+x]*255)<<24) | 0x1a181b);
		return out;
	}

	private static int[] blur(float[] mask, int width, int height, Scratch scratch)
	{
		int[] bytes=scratch.bytes, temp=scratch.temp;
		for(int i=0;i<mask.length;i++) bytes[i]=(int)(mask[i]*255);

		// Pillow GaussianBlur(radius=2): three fractional box passes per axis.
		float sigma2 = 4f/3f;
		float a = (3 * (2 - 3*sigma2)) / (6*(sigma2-4));
		float radius = 1 + a;
		long ww = (long)((1 << 24)/(radius*2+1));
		long fw = ((1 << 24)-3*ww)/2;
		for (int axis=0; axis<2; axis++) for (int pass=0; pass<3; pass++)
		{
			for (int y=0; y<height; y++) for (int x=0; x<width; x++)
			{
				long bulk = 0;
				for (int k=-2; k<=2; k++)
				{
					int sx = axis==0 ? Math.max(0,Math.min(width-1,x+k)) : x;
					int sy = axis==1 ? Math.max(0,Math.min(height-1,y+k)) : y;
					bulk += bytes[sy*width+sx] * (Math.abs(k)==2 ? fw : ww);
				}
				temp[y*width+x] = (int)((bulk+(1<<23)) >> 24);
			}
			int[] swap = bytes; bytes = temp; temp = swap;
		}
		return bytes;
	}

	/** Unlike the short footprint, this texture retains every captured silhouette row. */
	private static SpriteShadowMask fullSilhouette(BufferedImage sprite, SpriteShadowType type, Scratch scratch)
	{
		// Three radius-two box passes need six transparent pixels on every side.
		// Blur the padded image, including below the feet, before cropping its alpha.
		int pad=6, w=sprite.getWidth(), h=sprite.getHeight(), width=w+2*pad, height=h+2*pad;
		scratch.ensure(width*height);
		float[] alpha=scratch.mask;
		Arrays.fill(alpha,0);
		for(int y=0;y<h;y++) for(int x=0;x<w;x++)
		{
			int a=sprite.getRGB(x,y)>>>24;
			alpha[(y+pad)*width+x+pad]=a/255f;
		}
		BufferedImage out=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
		int[] blurred=type==SpriteShadowType.FLAT ? null : blurCast(alpha,width,height,scratch);
		for(int y=0;y<height;y++) for(int x=0;x<width;x++)
		{
			int i=y*width+x;
			float density=blurred==null ? alpha[i] : blurred[i]/255f;
			float value=type==SpriteShadowType.FLAT ? density*0.49f
				: type==SpriteShadowType.HARD ? 0.8f*(float)Math.pow(density,0.7)
				: (float)Math.floor(density*7+0.75f)/7*0.56f;
			out.setRGB(x,y,((int)(value*255)<<24)|0x1a181b);
		}
		return new SpriteShadowMask(sprite,out,type,true,-pad,-pad);
	}

	/** A one-pixel blur for full-size casts; downward reference masks retain radius two. */
	private static int[] blurCast(float[] alpha,int width,int height,Scratch scratch)
	{
		int[] bytes=scratch.bytes,temp=scratch.temp;
		for(int i=0;i<alpha.length;i++) bytes[i]=(int)(alpha[i]*255);
		for(int axis=0;axis<2;axis++) for(int pass=0;pass<2;pass++)
		{
			for(int y=0;y<height;y++) for(int x=0;x<width;x++)
			{
				int before=axis==0 ? y*width+Math.max(0,x-1) : Math.max(0,y-1)*width+x;
				int after=axis==0 ? y*width+Math.min(width-1,x+1) : Math.min(height-1,y+1)*width+x;
				temp[y*width+x]=(bytes[before]+2*bytes[y*width+x]+bytes[after]+2)/4;
			}
			int[] swap=bytes; bytes=temp; temp=swap;
		}
		return bytes;
	}

	public static int horizontalPadding(int width, SpriteShadowType type)
	{
		return type==SpriteShadowType.BLOB ? Math.max(3,(int)Math.ceil(width*0.1)+1) : 3;
	}

	public static SpriteShadowMask capture(BufferedImage sprite, SpriteShadowType type, Scratch scratch,
		BillboardOutlineRenderer.Scratch outlineScratch, double legacyRatio)
	{
		if (type == SpriteShadowType.OFF) return null;
		BufferedImage mask;
		if (type == SpriteShadowType.LEGACY)
		{
			BufferedImage copy = new BufferedImage(sprite.getWidth(),sprite.getHeight(),BufferedImage.TYPE_INT_ARGB);
			copy.setRGB(0,0,sprite.getWidth(),sprite.getHeight(),sprite.getRGB(0,0,sprite.getWidth(),sprite.getHeight(),null,0,sprite.getWidth()),0,sprite.getWidth());
			BillboardOutlineRenderer.applyShadow(copy,outlineScratch,legacyRatio);
			mask = new BufferedImage(sprite.getWidth()+6,sprite.getHeight()+10,BufferedImage.TYPE_INT_ARGB);
			for(int y=0;y<sprite.getHeight();y++) for(int x=0;x<sprite.getWidth();x++)
				if((sprite.getRGB(x,y)>>>24)==0) mask.setRGB(x+3,y,copy.getRGB(x,y));
			copy.flush();
		}
		else mask = render(sprite,type,scratch);
		SpriteShadowMask result=new SpriteShadowMask(sprite,mask,type);
		if(type==SpriteShadowType.FLAT || type==SpriteShadowType.HARD || type==SpriteShadowType.RADIAL)
			result.setDirectionalCaster(fullSilhouette(sprite,type,scratch));
		return result;
	}

	public static final class Scratch
	{
		private float[] mask = new float[0];
		private int[] bytes = new int[0], temp = new int[0];
		public void clear()
		{
			mask = new float[0]; bytes = new int[0]; temp = new int[0];
		}
		private void ensure(int size)
		{
			if (mask.length != size)
			{
				mask = new float[size]; bytes = new int[size]; temp = new int[size];
			}
		}
	}

	/** Composite into an already padded capture without changing its coordinate system. */
	public static BufferedImage compositeInPlace(BufferedImage image, SpriteShadowType type)
	{
		BufferedImage shadow = render(image,type);
		behind(image,shadow);
		return image;
	}

	public static void behind(BufferedImage image, BufferedImage shadow)
	{
		Graphics2D g = image.createGraphics();
		try { g.setComposite(AlphaComposite.DstOver); g.drawImage(shadow,-3,0,null); }
		finally { g.dispose(); shadow.flush(); }
	}
}
