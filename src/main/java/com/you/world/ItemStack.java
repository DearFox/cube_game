package com.you.world;

/**
 * Very small item stack for hotbar/inventory.
 * `packed` is the BlockData-packed short (block kind + state).
 * `count` is the number of items in the stack. For creative mode you can
 * use a sentinel like -1 to mean "infinite".
 */
public class ItemStack {
    public short packed;
    public int count;

    public ItemStack(short packed, int count) {
        this.packed = packed;
        this.count = count;
    }

    public ItemStack(short packed) { this(packed, 1); }

    public boolean isEmpty() {
        return packed == Blocks.AIR;
    }

    public void decrement() {
        if (count > 0) {
            count--;
            if (count == 0) {
                packed = Blocks.AIR;
            }
        }
    }

    public static ItemStack empty() { return new ItemStack(Blocks.AIR, 0); }
}
