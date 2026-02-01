package com.you.engine;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private final Window window;

    private boolean firstMouse = true;
    private double lastMouseX, lastMouseY;
    private float mouseDX, mouseDY;

    private double mouseX, mouseY;          // NEW: absolute cursor pos
    private float scrollDY = 0f;            // NEW: scroll accumulator

    private boolean prevLeftDown = false;
    private boolean prevRightDown = false;
    private boolean leftJustPressed = false;
    private boolean rightJustPressed = false;

    private boolean cursorLocked = true;

    // NEW: key edge tracking
    private final boolean[] prevKeys = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] justPressedKeys = new boolean[GLFW_KEY_LAST + 1];
    
 // mouse wheel accumulator (integer ticks)
    private int scrollAccum = 0;

    public Input(Window window) {
        this.window = window;
    }

    public void attachCallbacks() {
        glfwSetCursorPosCallback(window.handle(), (w, xpos, ypos) -> {
            // NEW: always store absolute mouse pos (needed for UI hit-testing)
            mouseX = xpos;
            mouseY = ypos;

            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }

            double dx = xpos - lastMouseX;
            double dy = lastMouseY - ypos; // invert Y for look

            lastMouseX = xpos;
            lastMouseY = ypos;

            mouseDX += (float) dx;
            mouseDY += (float) dy;
        });

        // NEW: scroll wheel
        glfwSetScrollCallback(window.handle(), (w, xoff, yoff) -> {
            scrollDY += (float) yoff;
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

    public boolean isCursorLocked() {
        return cursorLocked;
    }

    public void update() {
    	// mouse edge detection...
        boolean leftDown  = glfwGetMouseButton(window.handle(), GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean rightDown = glfwGetMouseButton(window.handle(), GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
        leftJustPressed  = leftDown && !prevLeftDown;
        rightJustPressed = rightDown && !prevRightDown;
        prevLeftDown = leftDown;
        prevRightDown = rightDown;

        // key edge detection: only for valid key codes
        // (optional) clear justPressed each frame
        for (int k = 0; k <= GLFW_KEY_LAST; k++) justPressedKeys[k] = false;

        // If you only care about a few keys, DO NOT loop all keys.
        trackKey(GLFW_KEY_E);
        trackKey(GLFW_KEY_1);
        trackKey(GLFW_KEY_2);
        trackKey(GLFW_KEY_3);
        trackKey(GLFW_KEY_4);
        trackKey(GLFW_KEY_5);
        trackKey(GLFW_KEY_6);
        trackKey(GLFW_KEY_7);
        trackKey(GLFW_KEY_8);
        trackKey(GLFW_KEY_9);
    }
    
    private void trackKey(int key) {
        if (key < 0 || key > GLFW_KEY_LAST) return;
        boolean down = glfwGetKey(window.handle(), key) == GLFW_PRESS;
        justPressedKeys[key] = down && !prevKeys[key];
        prevKeys[key] = down;
    }

    public boolean keyDown(int key) {
        return glfwGetKey(window.handle(), key) == GLFW_PRESS;
    }

    // NEW:
    public boolean keyJustPressed(int key) {
        if (key < 0 || key > GLFW_KEY_LAST) return false;
        return justPressedKeys[key];
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

    // NEW:
    public double mouseX() { return mouseX; }
    public double mouseY() { return mouseY; }

    // NEW:
    public float consumeScrollDY() {
        float v = scrollDY;
        scrollDY = 0f;
        return v;
    }
    
}
