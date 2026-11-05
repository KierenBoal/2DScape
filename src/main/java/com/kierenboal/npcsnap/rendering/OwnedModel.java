package com.kierenboal.npcsnap.rendering;

import lombok.experimental.Delegate;
import net.runelite.api.Model;
import net.runelite.api.AABB;

/** Capture-only model. Never submit this adapter to the client or mutate it. */
public final class OwnedModel implements Model
{
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
        verticesX = source.getVerticesX() == null ? null : source.getVerticesX().clone();
        verticesY = source.getVerticesY() == null ? null : source.getVerticesY().clone();
        verticesZ = source.getVerticesZ() == null ? null : source.getVerticesZ().clone();
        faceIndices1 = source.getFaceIndices1() == null ? null : source.getFaceIndices1().clone();
        faceIndices2 = source.getFaceIndices2() == null ? null : source.getFaceIndices2().clone();
        faceIndices3 = source.getFaceIndices3() == null ? null : source.getFaceIndices3().clone();
        faceColors1 = source.getFaceColors1() == null ? null : source.getFaceColors1().clone();
        faceColors2 = source.getFaceColors2() == null ? null : source.getFaceColors2().clone();
        faceColors3 = source.getFaceColors3() == null ? null : source.getFaceColors3().clone();
        texIndices1 = source.getTexIndices1() == null ? null : source.getTexIndices1().clone();
        texIndices2 = source.getTexIndices2() == null ? null : source.getTexIndices2().clone();
        texIndices3 = source.getTexIndices3() == null ? null : source.getTexIndices3().clone();
        vertexNormalsX = source.getVertexNormalsX() == null ? null : source.getVertexNormalsX().clone();
        vertexNormalsY = source.getVertexNormalsY() == null ? null : source.getVertexNormalsY().clone();
        vertexNormalsZ = source.getVertexNormalsZ() == null ? null : source.getVertexNormalsZ().clone();
        faceTextures = source.getFaceTextures() == null ? null : source.getFaceTextures().clone();
        unlitFaceColors = source.getUnlitFaceColors() == null ? null : source.getUnlitFaceColors().clone();
        faceTransparencies = source.getFaceTransparencies() == null ? null : source.getFaceTransparencies().clone();
        faceRenderPriorities = source.getFaceRenderPriorities() == null ? null : source.getFaceRenderPriorities().clone();
        faceBias = source.getFaceBias() == null ? null : source.getFaceBias().clone();
        textureFaces = source.getTextureFaces() == null ? null : source.getTextureFaces().clone();
        verticesCount = source.getVerticesCount();
        faceCount = source.getFaceCount();
        modelHeight = source.getModelHeight();
        transparency = source.getTransparency();
        overrideAmount = source.getOverrideAmount();
        overrideHue = source.getOverrideHue();
        overrideSaturation = source.getOverrideSaturation();
        overrideLuminance = source.getOverrideLuminance();
    }
    public float[] getVerticesX() { return verticesX; }
    public float[] getVerticesY() { return verticesY; }
    public float[] getVerticesZ() { return verticesZ; }
    public int[] getFaceIndices1() { return faceIndices1; }
    public int[] getFaceIndices2() { return faceIndices2; }
    public int[] getFaceIndices3() { return faceIndices3; }
    public int[] getFaceColors1() { return faceColors1; }
    public int[] getFaceColors2() { return faceColors2; }
    public int[] getFaceColors3() { return faceColors3; }
    public int[] getTexIndices1() { return texIndices1; }
    public int[] getTexIndices2() { return texIndices2; }
    public int[] getTexIndices3() { return texIndices3; }
    public int[] getVertexNormalsX() { return vertexNormalsX; }
    public int[] getVertexNormalsY() { return vertexNormalsY; }
    public int[] getVertexNormalsZ() { return vertexNormalsZ; }
    public short[] getFaceTextures() { return faceTextures; }
    public short[] getUnlitFaceColors() { return unlitFaceColors; }
    public byte[] getFaceTransparencies() { return faceTransparencies; }
    public byte[] getFaceRenderPriorities() { return faceRenderPriorities; }
    public byte[] getFaceBias() { return faceBias; }
    public byte[] getTextureFaces() { return textureFaces; }
    public int getVerticesCount() { return verticesCount; }
    public int getFaceCount() { return faceCount; }
    public int getModelHeight() { return modelHeight; }
    public byte getTransparency() { return transparency; }
    public byte getOverrideAmount() { return overrideAmount; }
    public byte getOverrideHue() { return overrideHue; }
    public byte getOverrideSaturation() { return overrideSaturation; }
    public byte getOverrideLuminance() { return overrideLuminance; }
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
