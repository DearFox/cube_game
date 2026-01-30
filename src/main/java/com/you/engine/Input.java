package com.you.engine;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private final Window window;

    private boolean firstMouse = true;
    private double lastMouseX, lastMouseY;
    private float mouseDX, mouseDY;

    private boolean prevLeftDown = false;
    private boolean prevRightDown = false;
    private boolean leftJustPressed = false;
    private boolean rightJustPressed = false;

    private boolean cursorLocked = true;

    // mouse wheel accumulator (integer ticks)
    private int scrollAccum = 0;

    public Input(Window window) {
        this.window = window;
    }

    public void attachCallbacks() {
        glfwSetCursorPosCallback(window.handle(), (w, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }

            double dx = xpos - lastMouseX;
            double dy = lastMouseY - ypos; // invert Y

            lastMouseX = xpos;
            lastMouseY = ypos;

            mouseDX += (float) dx;
            mouseDY += (float) dy;
        });

        glfwSetScrollCallback(window.handle(), (w, xoffset, yoffset) -> {
            // accumulate scroll as integer wheel ticks
            // positive yoffset: scroll up (usually move right in hotbar)
            if (yoffset > 0.0) scrollAccum += (int) Math.ceil(yoffset);
            else if (yoffset < 0.0) scrollAccum += (int) Math.floor(yoffset);
        });

        glfwSetKeyCallback(window.handle(), (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                cursorLocked = !cursorLocked;
                setCursorLocked(cursorLocked);
                firstMouse = true;
            }
        });
    }

    public void setCursorLocked(boolean locked) {
        glfwSetInputMode(window.handle(), GLFW_CURSOR, locked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        cursorLocked = locked;
        firstMouse = true;
    }

    public void update() {
        boolean leftDown = glfwGetMouseButton(window.handle(), GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean rightDown = glfwGetMouseButton(window.handle(), GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;

        leftJustPressed = leftDown && !prevLeftDown;
        rightJustPressed = rightDown && !prevRightDown;

        prevLeftDown = leftDown;
        prevRightDown = rightDown;
    }

    public void postUpdate() {
        // call once at end of frame if you need that behavior; not strictly necessary
    }

    public void resetMouseDeltas() {
        mouseDX = 0f;
        mouseDY = 0f;
    }

    public boolean keyDown(int key) {
        return glfwGetKey(window.handle(), key) == GLFW_PRESS;
    }

    public boolean mouseJustPressedLeft() {
        return leftJustPressed;
    }

    public boolean mouseJustPressedRight() {
        return rightJustPressed;
    }

    public float consumeMouseDX() {
        float v = mouseDX;
        mouseDX = 0f;
        return v;
    }

    public float consumeMouseDY() {
        float v = mouseDY;
        mouseDY = 0f;
        return v;
    }

    /** Consume mouse wheel ticks (integer). Positive = wheel up, negative = down. */
    public int consumeScroll() {
        int v = scrollAccum;
        scrollAccum = 0;
        return v;
    }
}
