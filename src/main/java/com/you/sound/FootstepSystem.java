package com.you.sound;

import org.joml.Vector3f;

import com.you.player.Player;
import com.you.world.Blocks;
import com.you.world.World;

public class FootstepSystem {
    private final BlockSoundRegistry sounds;

    // Tuning knobs
    //TODO pass in actual player speed
    private float stepDistanceWalk = 1.75f;   // blocks per step (tune)
    private float stepDistanceSprint = 1.30f; // faster cadence
    private float minSpeed = 0.15f;           // ignore tiny drift

    private float distAccum = 0f;
    private final Vector3f lastPos = new Vector3f();
    private boolean initialized = false;

    public FootstepSystem(BlockSoundRegistry sounds) {
        this.sounds = sounds;
    }

    public void reset(Vector3f currentPos) {
        lastPos.set(currentPos);
        distAccum = 0f;
        initialized = true;
    }

    /**
     * Call once per frame/tick after Physics updates player position.
     */
    public void update(World world, Player player, boolean grounded, boolean sprinting) {
        Vector3f pos = player.getPos();

        if (!initialized) reset(pos);

        // Horizontal movement since last update
        float dx = pos.x - lastPos.x;
        float dz = pos.z - lastPos.z;
        float horizDist = (float) Math.sqrt(dx * dx + dz * dz);

        lastPos.set(pos);

        if (!grounded) return;
        if (horizDist < minSpeed * 0.016f) { // tiny motion; optional (assumes ~60fps)
            // Better: check velocity magnitude if you have it (recommended)
            return;
        }

        distAccum += horizDist;

        float stepDist = sprinting ? stepDistanceSprint : stepDistanceWalk;
        if (distAccum >= stepDist) {
            distAccum %= stepDist; // keep remainder for consistent cadence

            // block under feet
            int bx = (int) Math.floor(pos.x+0.5);
            int by = (int) Math.floor(pos.y - 0.001f + 0.5) - 1; // slightly below feet
            int bz = (int) Math.floor(pos.z + 0.5);

            short blockId = world.getBlock(bx, by, bz); // adapt to your World API
            SoundMaterial mat = Blocks.getMaterialForId(blockId);

            // Play at player position (or slightly below)
            sounds.play(mat, BlockSoundAction.STEP, pos.x, pos.y, pos.z);
        }
    }
}
