package com.you.player;

import org.joml.Vector3f;

public class Player {
    private final Vector3f pos = new Vector3f();
    private final Vector3f vel = new Vector3f();

    public final Vector3f half = new Vector3f(0.3f, 0.9f, 0.3f);
    private boolean onGround = false;

    // tuning
    public float gravity = 25.0f;
    public float jumpSpeed = 8.0f;
    public float accel = 35.0f;
    public float maxSpeed = 6.0f;
    public float friction = 14.0f;

    // NEW: inventory
    private final PlayerInventory inventory;

    public Player(Vector3f startPos) {
        this.pos.set(startPos);
        this.inventory = new PlayerInventory();
    }

    public PlayerInventory getInventory() { return inventory; }

    public Vector3f getPos() { return pos; }
    public Vector3f getVel() { return vel; }
    public Vector3f getHalf() { return half; }

    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean v) { onGround = v; }
}
