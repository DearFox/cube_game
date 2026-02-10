package com.you.player;

import com.you.world.Blocks;
import com.you.world.ItemStack;
import com.you.world.BlockData;

import java.util.ArrayList;
import java.util.List;

public class PlayerInventory {

    public final ItemStack[] hotbar = new ItemStack[9];
    private int selectedSlot = 0;

    // creative entries (list of packed shorts you can select from)
    // for now we populate with one entry per kind (state=0)
    private final List<Short> creativeEntries = new ArrayList<>();

    // whether creative mode is enabled (infinite stacks / fill hotbar on click)
    private boolean creativeMode = true;

    public PlayerInventory() {
        // init empty
        for (int i = 0; i < hotbar.length; i++) hotbar[i] = ItemStack.empty();

        // example starting hotbar: grass in slot 0
        hotbar[0] = new ItemStack(Blocks.GRASS, 64);
        hotbar[1] = new ItemStack(Blocks.DIRT, 64);
        hotbar[2] = new ItemStack(Blocks.COBBLESTONE, 64);
        hotbar[3] = new ItemStack(Blocks.GLASS, 64);
        hotbar[4] = new ItemStack(Blocks.STONEBRICK, 64);
        hotbar[5] = new ItemStack(Blocks.SAND, 64);
        // rest left empty

        // populate creative palette (kinds only, state 0)
        // Blocks.getKind() registry is expected to hold kinds; we add known kinds:
        // NOTE: if you maintain dynamic registration, replace the following with a registry walk.
        creativeEntries.add(Blocks.GRASS);
        creativeEntries.add(Blocks.DIRT);
        creativeEntries.add(Blocks.COBBLESTONE);
        creativeEntries.add(Blocks.STONE);
        creativeEntries.add(Blocks.GLASS);
        creativeEntries.add(Blocks.STONEBRICK);
        creativeEntries.add(Blocks.SAND);
        creativeEntries.add(Blocks.MOSSYCOBBLE);
        creativeEntries.add(Blocks.MOSSYSTONEBRICK);
        creativeEntries.add(Blocks.PLANT_GRASS);
        creativeEntries.add(Blocks.PLANT_TRITELEIA);
        creativeEntries.add(Blocks.MUSHROOM_RED);
        creativeEntries.add(Blocks.MUSHROOM_BROWN);
        creativeEntries.add(Blocks.MUSHROOM_BLUE);
        creativeEntries.add(Blocks.PLANT_GOLD_POPPY);
    }

    public ItemStack getSelected() {
        return hotbar[selectedSlot];
    }

    public int getSelectedSlot() { return selectedSlot; }
    public void setSelectedSlot(int s) {
        if (s < 0) s = 0;
        if (s >= hotbar.length) s = hotbar.length - 1;
        selectedSlot = s;
    }

    public void cycleSelected(int delta) {
        int n = hotbar.length;
        selectedSlot = (selectedSlot + delta) % n;
        if (selectedSlot < 0) selectedSlot += n;
    }

    public List<Short> getCreativeEntries() { return creativeEntries; }

    public boolean isCreativeMode() { return creativeMode; }
    public void setCreativeMode(boolean v) { creativeMode = v; }

    public void placeOneFromSelected() {
        if (creativeMode) return; // infinite
        ItemStack s = hotbar[selectedSlot];
        if (s != null && !s.isEmpty()) {
            s.decrement();
            if (s.isEmpty()) hotbar[selectedSlot] = ItemStack.empty();
        }
    }

    public void setSlot(int slot, short packed, int count) {
        if (slot < 0 || slot >= hotbar.length) return;
        hotbar[slot] = new ItemStack(packed, count);
    }
}
