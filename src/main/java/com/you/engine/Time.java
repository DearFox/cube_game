package com.you.engine;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class Time {
    private double last;

    public void reset() {
        last = glfwGetTime();
    }

    public float update() {
        double now = glfwGetTime();
        float dt = (float) (now - last);
        last = now;
        return dt;
    }
}

