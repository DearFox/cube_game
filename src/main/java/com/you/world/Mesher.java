package com.you.world;

import java.util.ArrayList;

public class Mesher {

    private static final int[] FACE_X = { 1, -1, 0, 0, 0, 0 };
    private static final int[] FACE_Y = { 0, 0, 1, -1, 0, 0 };
    private static final int[] FACE_Z = { 0, 0, 0, 0, 1, -1 };

    public static final class MeshTriple {
        public final float[] opaque;
        public final float[] cutout;
        public final float[] transparent;

        public MeshTriple(float[] opaque, float[] cutout, float[] transparent) {
            this.opaque = opaque;
            this.cutout = cutout;
            this.transparent = transparent;
        }
    }

    private static final float[][] NORMALS = {
            {  1, 0, 0 }, { -1, 0, 0 },
            {  0, 1, 0 }, {  0,-1, 0 },
            {  0, 0, 1 }, {  0, 0,-1 }
    };

    public MeshTriple buildChunkMeshes(World world, Chunk c, int atlasW, int atlasH, int tileSizePx) {
        ArrayList<Float> opaque = new ArrayList<>();
        ArrayList<Float> cutout = new ArrayList<>();
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

                    int wx = baseX + lx;
                    int wy = baseY + ly;
                    int wz = baseZ + lz;

                    // --- CROSS-PLANT special mesh (two quads) ---
                    // You provide this helper in Blocks (see notes below).
                    if (Blocks.isPlant(packed)) {
                        // Plants are alpha-cutout, not translucent blending.
                        emitCrossPlant(world, cutout, wx, wy, wz, packed, atlasW, atlasH, tileSizePx);
                        continue;
                    }

                    // --- Normal cube block ---
                    boolean inTranslucentPass = Blocks.isTranslucent(packed);

                    // Put GLASS (and other alpha-cutout cubes) in cutout bucket, not opaque.
                    boolean isCutout = Blocks.isCutout(packed);
                    ArrayList<Float> out =
                            inTranslucentPass ? transp :
                            isCutout ? cutout :
                            opaque;

                    for (int f = 0; f < 6; f++) {
                        int nx = wx + FACE_X[f];
                        int ny = wy + FACE_Y[f];
                        int nz = wz + FACE_Z[f];

                        short nidPacked = world.getBlock(nx, ny, nz);

                        if (!inTranslucentPass) {
                            // SOLID/CUTOUT cube pass:
                            // cull if neighbor is fully opaque (opaque neighbors hide faces)
                            if (Blocks.isOpaque(nidPacked)) continue;

                            // prevent internal glass faces
                            if (BlockData.kind(packed) == Blocks.GLASS_KIND &&
                                BlockData.kind(nidPacked) == Blocks.GLASS_KIND) {
                                continue;
                            }
                        } else {
                            // TRANSLUCENT pass:
                            if (Blocks.isOpaque(nidPacked)) continue;
                        }

                        emitFace(world, out, wx, wy, wz, f, packed, atlasW, atlasH, tileSizePx);
                    }
                }
            }
        }

        return new MeshTriple(toArray(opaque), toArray(cutout), toArray(transp));
    }

    private static float[] toArray(ArrayList<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    // ---------- Cube face emitter (unchanged except signature types) ----------

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
        if (bt == null) return;

        int tileX = bt.tileX(face);
        int tileY = bt.tileY(face);

        float[] uv = tileUV(tileX, tileY, atlasW, atlasH, tileSizePx);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        float p0u=0, p0v=0, p1u=0, p1v=0, p2u=0, p2v=0, p3u=0, p3v=0;

        switch (face) {
            case 0 -> { p0u=u0; p0v=v0; p1u=u1; p1v=v0; p2u=u1; p2v=v1; p3u=u0; p3v=v1; }
            case 1 -> { p1u=u0; p1v=v0; p0u=u1; p0v=v0; p3u=u1; p3v=v1; p2u=u0; p2v=v1; }
            case 4 -> { p0u=u0; p0v=v0; p3u=u1; p3v=v0; p2u=u1; p2v=v1; p1u=u0; p1v=v1; }
            case 5 -> { p0u=u0; p0v=v0; p3u=u1; p3v=v0; p2u=u1; p2v=v1; p1u=u0; p1v=v1; }
            case 2 -> { p0u=u0; p0v=v1; p1u=u1; p1v=v1; p2u=u1; p2v=v0; p3u=u0; p3v=v0; }
            case 3 -> { p3u=u0; p3v=v1; p2u=u1; p2v=v1; p1u=u1; p1v=v0; p0u=u0; p0v=v0; }
        }

        push(out, p0, nx, ny, nz, p0u, p0v, light01);
        push(out, p2, nx, ny, nz, p2u, p2v, light01);
        push(out, p1, nx, ny, nz, p1u, p1v, light01);

        push(out, p0, nx, ny, nz, p0u, p0v, light01);
        push(out, p3, nx, ny, nz, p3u, p3v, light01);
        push(out, p2, nx, ny, nz, p2u, p2v, light01);
    }

    // ---------- Cross-plant emitter (NEW) ----------

    private void emitCrossPlant(World world, ArrayList<Float> out, int bx, int by, int bz,
                                short blockPacked, int atlasW, int atlasH, int tileSizePx) {

        BlockType bt = Blocks.get(blockPacked);
        if (bt == null) return;

        // Use top tile for the icon/plant texture (face index 2 == +Y in your scheme)
        int tileX = bt.tileX(2);
        int tileY = bt.tileY(2);

        float[] uv = tileUV(tileX, tileY, atlasW, atlasH, tileSizePx);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        // Lighting: sample at block center (simple and stable)
        int L = world.getCombinedLight(bx, by, bz);
        float light01 = (L / 15.0f);
        float minLight = 0.20f;
        light01 = minLight + (1.0f - minLight) * light01;

        // Use a stable "upright" normal so your hemi lighting doesn't do weird diagonal shading.
        float nx = 0f, ny = 1f, nz = 0f;

        // Block bounds in world space
        float cx = bx;
        float cz = bz;
        float y0 = by - 0.5f;
        float y1 = by + 0.5f;

        // Cross quad half-width
        float r = 0.5f;

        // Quad A: along x=z diagonal
        float[] a0 = new float[]{ cx - r, y0, cz - r };
        float[] a1 = new float[]{ cx + r, y0, cz + r };
        float[] a2 = new float[]{ cx + r, y1, cz + r };
        float[] a3 = new float[]{ cx - r, y1, cz - r };

        // Quad B: along x=-z diagonal
        float[] b0 = new float[]{ cx - r, y0, cz + r };
        float[] b1 = new float[]{ cx + r, y0, cz - r };
        float[] b2 = new float[]{ cx + r, y1, cz - r };
        float[] b3 = new float[]{ cx - r, y1, cz + r };

        // UV mapping: bottom-left, bottom-right, top-right, top-left
        // (If your texture appears upside down, swap v0/v1 here.)
        emitQuad(out, a0, a1, a2, a3, nx, ny, nz, u0, v0, u1, v1, light01);
        emitQuad(out, b0, b1, b2, b3, nx, ny, nz, u0, v0, u1, v1, light01);
    }

    private void emitQuad(ArrayList<Float> out,
                          float[] p0, float[] p1, float[] p2, float[] p3,
                          float nx, float ny, float nz,
                          float u0, float v0, float u1, float v1,
                          float light01) {

        // tri 1: p0, p2, p1
        push(out, p0, nx, ny, nz, u0, v0, light01);
        push(out, p2, nx, ny, nz, u1, v1, light01);
        push(out, p1, nx, ny, nz, u1, v0, light01);

        // tri 2: p0, p3, p2
        push(out, p0, nx, ny, nz, u0, v0, light01);
        push(out, p3, nx, ny, nz, u0, v1, light01);
        push(out, p2, nx, ny, nz, u1, v1, light01);
    }

    private void push(ArrayList<Float> out, float[] p,
                      float nx, float ny, float nz,
                      float u, float v, float light01) {
        out.add(p[0]); out.add(p[1]); out.add(p[2]);
        out.add(nx);   out.add(ny);   out.add(nz);
        out.add(u);    out.add(v);
        out.add(light01);
    }

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
