package com.you.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {

    private final Vector3f pos = new Vector3f();
    private final Vector3f front = new Vector3f(0, 0, -1);
    private final Vector3f up = new Vector3f(0, 1, 0);

    private float yaw = -90f;
    private float pitch = 0f;
    private float mouseSensitivity = 0.12f;

    public Camera(Vector3f startPos) {
        this.pos.set(startPos);
    }

    public void updateLook(Input input) {
        float dx = input.consumeMouseDX() * mouseSensitivity;
        float dy = input.consumeMouseDY() * mouseSensitivity;

        yaw += dx;
        pitch += dy;

        if (pitch > 89f) pitch = 89f;
        if (pitch < -89f) pitch = -89f;

        float cy = (float) Math.cos(Math.toRadians(yaw));
        float sy = (float) Math.sin(Math.toRadians(yaw));
        float cp = (float) Math.cos(Math.toRadians(pitch));
        float sp = (float) Math.sin(Math.toRadians(pitch));

        front.set(cy * cp, sp, sy * cp).normalize();
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(pos, new Vector3f(pos).add(front), up);
    }

    public Vector3f getPosition() {
        return new Vector3f(pos);
    }

    public void setPosition(Vector3f p) {
        pos.set(p);
    }

    public Vector3f getForward() {
        return new Vector3f(front);
    }

    public Vector3f getUp() {
        return new Vector3f(up);
    }
}

