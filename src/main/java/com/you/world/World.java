package com.you.world;

import java.util.Collection;
import java.util.HashMap;

public class World {

    private final HashMap<ChunkPos, Chunk> chunks = new HashMap<>();

    public Collection<Chunk> getChunks() {
        return chunks.values();
    }

    public boolean isSolidBlock(int bx, int by, int bz) {
        return Blocks.isSolid(getBlock(bx, by, bz));
    }

    // returns packed short (kind+state). If chunk missing => AIR
    public short getBlock(int bx, int by, int bz) {
        int cx = floorDiv(bx, Chunk.CHUNK_SIZE);
        int cy = floorDiv(by, Chunk.CHUNK_SIZE);
        int cz = floorDiv(bz, Chunk.CHUNK_SIZE);

        Chunk c = chunks.get(new ChunkPos(cx, cy, cz));
        if (c == null) return Blocks.AIR;

        int lx = floorMod(bx, Chunk.CHUNK_SIZE);
        int ly = floorMod(by, Chunk.CHUNK_SIZE);
        int lz = floorMod(bz, Chunk.CHUNK_SIZE);

        return c.get(lx, ly, lz);
    }

    // set packed short block value
    public void setBlock(int bx, int by, int bz, short packed) {
        int cx = floorDiv(bx, Chunk.CHUNK_SIZE);
        int cy = floorDiv(by, Chunk.CHUNK_SIZE);
        int cz = floorDiv(bz, Chunk.CHUNK_SIZE);

        Chunk c = getOrCreateChunk(cx, cy, cz);

        int lx = floorMod(bx, Chunk.CHUNK_SIZE);
        int ly = floorMod(by, Chunk.CHUNK_SIZE);
        int lz = floorMod(bz, Chunk.CHUNK_SIZE);

        c.set(lx, ly, lz, packed);

        // boundary dirty neighbors
        if (lx == 0) markDirty(cx - 1, cy, cz);
        if (lx == Chunk.CHUNK_SIZE - 1) markDirty(cx + 1, cy, cz);
        if (ly == 0) markDirty(cx, cy - 1, cz);
        if (ly == Chunk.CHUNK_SIZE - 1) markDirty(cx, cy + 1, cz);
        if (lz == 0) markDirty(cx, cy, cz - 1);
        if (lz == Chunk.CHUNK_SIZE - 1) markDirty(cx, cy, cz + 1);
    }

    public int getSkyLight(int bx, int by, int bz) {
        Chunk c = chunks.get(new ChunkPos(
                floorDiv(bx, Chunk.CHUNK_SIZE),
                floorDiv(by, Chunk.CHUNK_SIZE),
                floorDiv(bz, Chunk.CHUNK_SIZE)
        ));
        if (c == null) return 0;

        int lx = floorMod(bx, Chunk.CHUNK_SIZE);
        int ly = floorMod(by, Chunk.CHUNK_SIZE);
        int lz = floorMod(bz, Chunk.CHUNK_SIZE);
        return c.getSky(lx, ly, lz);
    }

    public int getBlockLight(int bx, int by, int bz) {
        Chunk c = chunks.get(new ChunkPos(
                floorDiv(bx, Chunk.CHUNK_SIZE),
                floorDiv(by, Chunk.CHUNK_SIZE),
                floorDiv(bz, Chunk.CHUNK_SIZE)
        ));
        if (c == null) return 0;

        int lx = floorMod(bx, Chunk.CHUNK_SIZE);
        int ly = floorMod(by, Chunk.CHUNK_SIZE);
        int lz = floorMod(bz, Chunk.CHUNK_SIZE);
        return c.getBlockLight(lx, ly, lz);
    }

    public int getCombinedLight(int bx, int by, int bz) {
        return Math.max(getSkyLight(bx, by, bz), getBlockLight(bx, by, bz));
    }

    public void setSkyLight(int bx, int by, int bz, int v) {
        Chunk c = chunks.get(new ChunkPos(
                floorDiv(bx, Chunk.CHUNK_SIZE),
                floorDiv(by, Chunk.CHUNK_SIZE),
                floorDiv(bz, Chunk.CHUNK_SIZE)
        ));
        if (c == null) return;

        int lx = floorMod(bx, Chunk.CHUNK_SIZE);
        int ly = floorMod(by, Chunk.CHUNK_SIZE);
        int lz = floorMod(bz, Chunk.CHUNK_SIZE);
        c.setSky(lx, ly, lz, v);
    }

    public void setBlockLight(int bx, int by, int bz, int v) {
        Chunk c = chunks.get(new ChunkPos(
                floorDiv(bx, Chunk.CHUNK_SIZE),
                floorDiv(by, Chunk.CHUNK_SIZE),
                floorDiv(bz, Chunk.CHUNK_SIZE)
        ));
        if (c == null) return;

        int lx = floorMod(bx, Chunk.CHUNK_SIZE);
        int ly = floorMod(by, Chunk.CHUNK_SIZE);
        int lz = floorMod(bz, Chunk.CHUNK_SIZE);
        c.setBlockLight(lx, ly, lz, v);
    }

    public void clearAllLighting() {
        for (Chunk c : chunks.values()) {
            for (int i = 0; i < c.skyLight.length; i++) {
                c.skyLight[i] = 0;
                c.blockLight[i] = 0;
            }
        }
    }

    private Chunk getOrCreateChunk(int cx, int cy, int cz) {
        ChunkPos p = new ChunkPos(cx, cy, cz);
        Chunk c = chunks.get(p);
        if (c == null) {
            c = new Chunk(p);
            chunks.put(p, c);
        }
        return c;
    }

    private void markDirty(int cx, int cy, int cz) {
        Chunk n = chunks.get(new ChunkPos(cx, cy, cz));
        if (n != null) n.dirty = true;
    }

    public static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    public static int floorMod(int a, int b) {
        return a - floorDiv(a, b) * b;
    }

    public void generateTestWorld() {
        int R = 48;
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                float fx = x * 0.12f;
                float fz = z * 0.12f;
                int h = (int) Math.floor(4
                        + Math.sin(fx) * 3.0f
                        + Math.cos(fz * 0.9f) * 2.0f
                        + Math.sin((fx + fz) * 0.6f) * 1.5f);

                if (h < -4) h = -4;
                if (h > 10) h = 10;

                setBlock(x, h, z, Blocks.GRASS);
                for (int y = h - 1; y >= h - 3; y--) setBlock(x, y, z, Blocks.DIRT);
                for (int y = h - 4; y >= -16; y--) setBlock(x, y, z, Blocks.STONE);

                if ((Math.abs(x * 17 + z * 59) % 23) < 3) {
                    setBlock(x, h + 1, z, Blocks.STONE);
                }
            }
        }
    }

    public short setBlockReturningOld(int bx, int by, int bz, short id) {
        short old = getBlock(bx, by, bz);
        setBlock(bx, by, bz, id); // your existing method
        return old;
    }

    public void markDirtyAtBlock(int bx, int by, int bz) {
        int cx = floorDiv(bx, Chunk.CHUNK_SIZE);
        int cy = floorDiv(by, Chunk.CHUNK_SIZE);
        int cz = floorDiv(bz, Chunk.CHUNK_SIZE);
        Chunk c = chunks.get(new ChunkPos(cx, cy, cz));
        if (c != null) c.dirty = true;
    }

    public Chunk getChunk(int cx,int cy,int cz){
        return chunks.get(new ChunkPos(cx,cy,cz));
    }

}
