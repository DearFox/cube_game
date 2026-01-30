package com.you.world;

public class Chunk {
    public static final int CHUNK_SIZE = 16;

    public final ChunkPos pos;
    public final short[] blocks = new short[CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE];

    // Lighting (0..15 stored in low nibble)
    public final byte[] skyLight   = new byte[CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE];
    public final byte[] blockLight = new byte[CHUNK_SIZE * CHUNK_SIZE * CHUNK_SIZE];

    public boolean dirty = true;

    public Chunk(ChunkPos pos) {
        this.pos = pos;
    }

    private int idx(int lx, int ly, int lz) {
        return lx + CHUNK_SIZE * (lz + CHUNK_SIZE * ly);
    }

    // blocks are packed shorts (kind+state)
    public short get(int lx, int ly, int lz) {
        return blocks[idx(lx, ly, lz)];
    }

    public void set(int lx, int ly, int lz, short packed) {
        blocks[idx(lx, ly, lz)] = packed;
        dirty = true;
    }

    // skylight: stored in a byte but only low 4 bits used
    public int getSky(int lx, int ly, int lz) {
        return skyLight[idx(lx, ly, lz)] & 0xF;
    }

    public void setSky(int lx, int ly, int lz, int v) {
        skyLight[idx(lx, ly, lz)] = (byte) (v & 0xF);
    }

    // block light: stored in a byte but only low 4 bits used
    public int getBlockLight(int lx, int ly, int lz) {
        return blockLight[idx(lx, ly, lz)] & 0xF;
    }

    public void setBlockLight(int lx, int ly, int lz, int v) {
        blockLight[idx(lx, ly, lz)] = (byte) (v & 0xF);
    }
}
