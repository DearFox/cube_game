package com.you.world;

import java.util.ArrayList;

public class Mesher {

    private static final int[] FACE_X = { 1, -1, 0, 0, 0, 0 };
    private static final int[] FACE_Y = { 0, 0, 1, -1, 0, 0 };
    private static final int[] FACE_Z = { 0, 0, 0, 0, 1, -1 };

    public static final class MeshPair {
        public final float[] opaque;
        public final float[] transparent;

        public MeshPair(float[] opaque, float[] transparent) {
            this.opaque = opaque;
            this.transparent = transparent;
        }
    }

    private static final float[][] NORMALS = {
            {  1, 0, 0 }, { -1, 0, 0 },
            {  0, 1, 0 }, {  0,-1, 0 },
            {  0, 0, 1 }, {  0, 0,-1 }
    };

    public MeshPair buildChunkMeshes(World world, Chunk c, int atlasW, int atlasH, int tileSizePx) {
        ArrayList<Float> opaque = new ArrayList<>();
        ArrayList<Float> transp = new ArrayList<>();

        int baseX = c.pos.cx() * Chunk.CHUNK_SIZE;
        int baseY = c.pos.cy() * Chunk.CHUNK_SIZE;
        int baseZ = c.pos.cz() * Chunk.CHUNK_SIZE;

        for (int ly = 0; ly < Chunk.CHUNK_SIZE; ly++) {
            for (int lz = 0; lz < Chunk.CHUNK_SIZE; lz++) {
                for (int lx = 0; lx < Chunk.CHUNK_SIZE; lx++) {

                    short packed = c.get(lx, ly, lz);
                    int kind = BlockData.kind(packed);

                    if (kind == Blocks.AIR_KIND) continue;

                    boolean inTranslucentPass = Blocks.isTranslucent(packed);
                    ArrayList<Float> out = inTranslucentPass ? transp : opaque;

                    int wx = baseX + lx;
                    int wy = baseY + ly;
                    int wz = baseZ + lz;

                    for (int f = 0; f < 6; f++) {
                        int nx = wx + FACE_X[f];
                        int ny = wy + FACE_Y[f];
                        int nz = wz + FACE_Z[f];

                        short nidPacked = world.getBlock(nx, ny, nz);

                        if (!inTranslucentPass) {
                            // SOLID pass (opaque + cutout glass):
                            // cull if neighbor is fully opaque
                            if (Blocks.isOpaque(nidPacked)) continue;

                            // prevent internal glass faces (Minecraft-style)
                            if (BlockData.kind(packed) == Blocks.GLASS_KIND &&
                                BlockData.kind(nidPacked) == Blocks.GLASS_KIND) {
                                continue;
                            }
                        } else {
                            // TRANSLUCENT pass (water, etc):
                            // typically only cull against opaque neighbors
                            if (Blocks.isOpaque(nidPacked)) continue;

                            // Later you can add "same-type" cull for water:
                            // if (BlockData.kind(packed) == Blocks.WATER_KIND && BlockData.kind(nidPacked) == Blocks.WATER_KIND) continue;
                        }

                        emitFace(world, out, wx, wy, wz, f, packed, atlasW, atlasH, tileSizePx);
                    }
                }
            }
        }

        return new MeshPair(toArray(opaque), toArray(transp));
    }

    private static float[] toArray(ArrayList<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private void emitFace(World world, ArrayList<Float> out, int bx, int by, int bz,
                          int face, short blockPacked, int atlasW, int atlasH, int tileSizePx) {

        float x0 = bx - 0.5f, x1 = bx + 0.5f;
        float y0 = by - 0.5f, y1 = by + 0.5f;
        float z0 = bz - 0.5f, z1 = bz + 0.5f;

        float nx = NORMALS[face][0];
        float ny = NORMALS[face][1];
        float nz = NORMALS[face][2];

        // sample light from the block just outside this face
        int sx = bx + (int) nx;
        int sy = by + (int) ny;
        int sz = bz + (int) nz;

        int L = world.getCombinedLight(sx, sy, sz); // 0..15
        float light01 = (L / 15.0f);

        // Optional: keep caves from being pitch black
        float minLight = 0.20f;
        light01 = minLight + (1.0f - minLight) * light01;

        float[] p0, p1, p2, p3;
        switch (face) {
            case 0 -> { p0 = new float[]{x1, y0, z0}; p1 = new float[]{x1, y0, z1}; p2 = new float[]{x1, y1, z1}; p3 = new float[]{x1, y1, z0}; } // +X
            case 1 -> { p0 = new float[]{x0, y0, z1}; p1 = new float[]{x0, y0, z0}; p2 = new float[]{x0, y1, z0}; p3 = new float[]{x0, y1, z1}; } // -X
            case 2 -> { p0 = new float[]{x0, y1, z0}; p1 = new float[]{x1, y1, z0}; p2 = new float[]{x1, y1, z1}; p3 = new float[]{x0, y1, z1}; } // +Y
            case 3 -> { p0 = new float[]{x0, y0, z1}; p1 = new float[]{x1, y0, z1}; p2 = new float[]{x1, y0, z0}; p3 = new float[]{x0, y0, z0}; } // -Y
            case 4 -> { p0 = new float[]{x0, y0, z1}; p1 = new float[]{x0, y1, z1}; p2 = new float[]{x1, y1, z1}; p3 = new float[]{x1, y0, z1}; } // +Z
            case 5 -> { p0 = new float[]{x1, y0, z0}; p1 = new float[]{x1, y1, z0}; p2 = new float[]{x0, y1, z0}; p3 = new float[]{x0, y0, z0}; } // -Z
            default -> throw new IllegalArgumentException("face " + face);
        }

        BlockType bt = Blocks.get(blockPacked);
        if (bt == null) return; // unknown kind => don't render (safety)

        int tileX = bt.tileX(face);
        int tileY = bt.tileY(face);

        float[] uv = tileUV(tileX, tileY, atlasW, atlasH, tileSizePx);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        // For your current face corner definitions, this mapping matches the “fixed” version you had:
        float p0u=0, p0v=0, p1u=0, p1v=0, p2u=0, p2v=0, p3u=0, p3v=0;

        switch (face) {
            case 0 -> { // +X
                p0u=u0; p0v=v0; p1u=u1; p1v=v0; p2u=u1; p2v=v1; p3u=u0; p3v=v1;
            }
            case 1 -> { // -X (mirrored)
                p1u=u0; p1v=v0; p0u=u1; p0v=v0; p3u=u1; p3v=v1; p2u=u0; p2v=v1;
            }
            case 4 -> { // +Z
                p0u=u0; p0v=v0; p3u=u1; p3v=v0; p2u=u1; p2v=v1; p1u=u0; p1v=v1;
            }
            case 5 -> { // -Z
                p0u=u0; p0v=v0; p3u=u1; p3v=v0; p2u=u1; p2v=v1; p1u=u0; p1v=v1;
            }
            case 2 -> { // +Y
                p0u=u0; p0v=v1; p1u=u1; p1v=v1; p2u=u1; p2v=v0; p3u=u0; p3v=v0;
            }
            case 3 -> { // -Y
                p3u=u0; p3v=v1; p2u=u1; p2v=v1; p1u=u1; p1v=v0; p0u=u0; p0v=v0;
            }
        }

        // triangles: (p0,p2,p1) and (p0,p3,p2)
        push(out, p0, nx, ny, nz, p0u, p0v, light01);
        push(out, p2, nx, ny, nz, p2u, p2v, light01);
        push(out, p1, nx, ny, nz, p1u, p1v, light01);

        push(out, p0, nx, ny, nz, p0u, p0v, light01);
        push(out, p3, nx, ny, nz, p3u, p3v, light01);
        push(out, p2, nx, ny, nz, p2u, p2v, light01);
    }

    private void push(ArrayList<Float> out, float[] p,
                      float nx, float ny, float nz,
                      float u, float v, float light01) {
        out.add(p[0]); out.add(p[1]); out.add(p[2]);
        out.add(nx);   out.add(ny);   out.add(nz);
        out.add(u);    out.add(v);
        out.add(light01);
    }

    // tile coords (tileX,tileY) where (0,0) is TOP-LEFT in your image editor.
    // Because we load with STB flip=true, we do NOT flip tileY here.
    private static float[] tileUV(int tileX, int tileY, int atlasW, int atlasH, int tileSizePx) {
        int tilesPerRow = atlasW / tileSizePx;
        int tilesPerCol = atlasH / tileSizePx;

        if (tileX < 0) tileX = 0;
        if (tileY < 0) tileY = 0;
        if (tileX >= tilesPerRow) tileX = tilesPerRow - 1;
        if (tileY >= tilesPerCol) tileY = tilesPerCol - 1;

        float uSize = (float) tileSizePx / (float) atlasW;
        float vSize = (float) tileSizePx / (float) atlasH;

        float u0 = tileX * uSize;
        float v0 = tileY * vSize;
        float u1 = u0 + uSize;
        float v1 = v0 + vSize;

        return new float[] { u0, v0, u1, v1 };
    }
}
