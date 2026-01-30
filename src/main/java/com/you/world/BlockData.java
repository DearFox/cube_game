package com.you.world;

// 16-bit packed: [ kind:12 ][ state:4 ]
public final class BlockData {
    public static final int STATE_BITS = 4;
    public static final int STATE_MASK = (1 << STATE_BITS) - 1; // 0xF
    public static final int KIND_SHIFT = STATE_BITS;

    public static short pack(int kind, int state) {
        return (short) (((kind & 0xFFF) << KIND_SHIFT) | (state & STATE_MASK));
    }

    public static int kind(short packed) {
        return (packed >>> KIND_SHIFT) & 0xFFF;
    }

    public static int state(short packed) {
        return packed & STATE_MASK;
    }

    public static short withState(short packed, int state) {
        return (short) ((packed & 0xFFF0) | (state & STATE_MASK));
    }
}
