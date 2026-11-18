package com.kierenboal.npcsnap.rendering;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Delegate;
import net.runelite.api.AABB;
import net.runelite.api.Model;

/** Capture-only model. Never submit this adapter to the client or mutate it. */
@Getter
public final class OwnedModel implements Model
{
    @Getter(AccessLevel.NONE)
    @Delegate(excludes = Captured.class)
    private final Model source;
    private final float[] verticesX;
    private final float[] verticesY;
    private final float[] verticesZ;
    private final int[] faceIndices1;
    private final int[] faceIndices2;
    private final int[] faceIndices3;
    private final int[] faceColors1;
    private final int[] faceColors2;
    private final int[] faceColors3;
    private final int[] texIndices1;
    private final int[] texIndices2;
    private final int[] texIndices3;
    private final int[] vertexNormalsX;
    private final int[] vertexNormalsY;
    private final int[] vertexNormalsZ;
    private final short[] faceTextures;
    private final short[] unlitFaceColors;
    private final byte[] faceTransparencies;
    private final byte[] faceRenderPriorities;
    private final byte[] faceBias;
    private final byte[] textureFaces;
    private final int verticesCount;
    private final int faceCount;
    private final int modelHeight;
    private final byte transparency;
    private final byte overrideAmount;
    private final byte overrideHue;
    private final byte overrideSaturation;
    private final byte overrideLuminance;

    public OwnedModel(Model source)
    {
        this.source = source;
        verticesX = copy(source.getVerticesX());
        verticesY = copy(source.getVerticesY());
        verticesZ = copy(source.getVerticesZ());
        faceIndices1 = copy(source.getFaceIndices1());
        faceIndices2 = copy(source.getFaceIndices2());
        faceIndices3 = copy(source.getFaceIndices3());
        faceColors1 = copy(source.getFaceColors1());
        faceColors2 = copy(source.getFaceColors2());
        faceColors3 = copy(source.getFaceColors3());
        texIndices1 = copy(source.getTexIndices1());
        texIndices2 = copy(source.getTexIndices2());
        texIndices3 = copy(source.getTexIndices3());
        vertexNormalsX = copy(source.getVertexNormalsX());
        vertexNormalsY = copy(source.getVertexNormalsY());
        vertexNormalsZ = copy(source.getVertexNormalsZ());
        faceTextures = copy(source.getFaceTextures());
        unlitFaceColors = copy(source.getUnlitFaceColors());
        faceTransparencies = copy(source.getFaceTransparencies());
        faceRenderPriorities = copy(source.getFaceRenderPriorities());
        faceBias = copy(source.getFaceBias());
        textureFaces = copy(source.getTextureFaces());
        verticesCount = source.getVerticesCount();
        faceCount = source.getFaceCount();
        modelHeight = source.getModelHeight();
        transparency = source.getTransparency();
        overrideAmount = source.getOverrideAmount();
        overrideHue = source.getOverrideHue();
        overrideSaturation = source.getOverrideSaturation();
        overrideLuminance = source.getOverrideLuminance();
    }

    private static float[] copy(float[] values)
    {
        return values == null ? null : values.clone();
    }

    private static int[] copy(int[] values)
    {
        return values == null ? null : values.clone();
    }

    private static short[] copy(short[] values)
    {
        return values == null ? null : values.clone();
    }

    private static byte[] copy(byte[] values)
    {
        return values == null ? null : values.clone();
    }

    public Model getModel() { return this; }
    public AABB getAABB(int orientation) { return null; }

    private interface Captured
    {
        float[] getVerticesX();
        float[] getVerticesY();
        float[] getVerticesZ();
        int[] getFaceIndices1();
        int[] getFaceIndices2();
        int[] getFaceIndices3();
        int[] getFaceColors1();
        int[] getFaceColors2();
        int[] getFaceColors3();
        int[] getTexIndices1();
        int[] getTexIndices2();
        int[] getTexIndices3();
        int[] getVertexNormalsX();
        int[] getVertexNormalsY();
        int[] getVertexNormalsZ();
        short[] getFaceTextures();
        short[] getUnlitFaceColors();
        byte[] getFaceTransparencies();
        byte[] getFaceRenderPriorities();
        byte[] getFaceBias();
        byte[] getTextureFaces();
        int getVerticesCount();
        int getFaceCount();
        int getModelHeight();
        byte getTransparency();
        byte getOverrideAmount();
        byte getOverrideHue();
        byte getOverrideSaturation();
        byte getOverrideLuminance();
        Model getModel();
        AABB getAABB(int orientation);
    }
}
