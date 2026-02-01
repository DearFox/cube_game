
package com.you.engine;

import org.lwjgl.opengl.GL;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.glViewport;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;

public class Window {

	private final int initialWidth;
	private final int initialHeight;
	private final String title;

	private long handle;

	// Track framebuffer size safely (updated by callback, used by render thread)
	private volatile int fbWidth = 1;
	private volatile int fbHeight = 1;
	private volatile boolean fbResized = false;

	// Expose whether OpenGL 4.0 is available (for renderer fallback decisions)
	private boolean supportsGL40 = false;

	public boolean supportsGL40() {
		return supportsGL40;
	}

	public Window(int width, int height, String title) {
		this.initialWidth = width;
		this.initialHeight = height;
		this.title = title;
	}

	/** Call from your render loop each frame (or before rendering) */
	public void applyViewportIfNeeded() {
		if (!fbResized)
			return;
		fbResized = false;

		int w = Math.max(1, fbWidth);
		int h = Math.max(1, fbHeight);
		glViewport(0, 0, w, h);
	}

	public int[] framebufferSize() {
		return new int[] { fbWidth, fbHeight };
	}

	public void create() {
		GLFWErrorCallback.createPrint(System.err).set();
		if (!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW");

		// Request an OpenGL 4.0 core profile context
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

		// On macOS you must request forward-compatible core profile
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

		glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

		handle = glfwCreateWindow(initialWidth, initialHeight, title, NULL, NULL);
		if (handle == NULL)
			throw new RuntimeException("Failed to create GLFW window");

		glfwMakeContextCurrent(handle);

		// Create OpenGL capabilities AFTER context is current
		org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.createCapabilities();
		supportsGL40 = caps.OpenGL40;

		System.out.println("GL_VENDOR:   " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));
		System.out.println("GL_RENDERER: " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
		System.out.println("GL_VERSION:  " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));

		if (!supportsGL40) {
			System.err.println(
					"Warning: OpenGL 4.0 not available. " + "You must avoid GL 4.0-only calls like glBlendFunci().");
		}

		glfwSwapInterval(1); // vsync

		// Framebuffer callback: DO NOT call GL here; just store size
		glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
			if (w <= 0 || h <= 0)
				return;
			fbWidth = w;
			fbHeight = h;
			fbResized = true;
		});

		// Initial framebuffer size
		try (var stack = stackPush()) {
			IntBuffer pw = stack.mallocInt(1);
			IntBuffer ph = stack.mallocInt(1);
			glfwGetFramebufferSize(handle, pw, ph);
			fbWidth = Math.max(1, pw.get(0));
			fbHeight = Math.max(1, ph.get(0));
			fbResized = true; // triggers viewport set on first frame
		}
	}

	public long handle() {
		return handle;
	}

	public void pollEvents() {
		glfwPollEvents();
	}

	public void swapBuffers() {
		glfwSwapBuffers(handle);
	}

	public boolean shouldClose() {
		return glfwWindowShouldClose(handle);
	}

	/*
	 * public int[] framebufferSize() { try (var stack = stackPush()) { IntBuffer pw
	 * = stack.mallocInt(1); IntBuffer ph = stack.mallocInt(1);
	 * glfwGetFramebufferSize(handle, pw, ph); return new int[]{pw.get(0),
	 * ph.get(0)}; } }
	 */

	public void destroy() {
	    if (handle != 0) {
	        glfwFreeCallbacks(handle);     // IMPORTANT: frees cursor/key/framebuffer callbacks, etc.
	        glfwSetCursorPosCallback(handle, null);
	        glfwSetKeyCallback(handle, null);
	        glfwSetFramebufferSizeCallback(handle, null);
	        glfwDestroyWindow(handle);
	        handle = 0;
	    }

	    glfwTerminate();

	    var cb = glfwSetErrorCallback(null);
	    if (cb != null) cb.free();
	}
	
	public int[] windowSize() {
	    try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
	        var w = stack.mallocInt(1);
	        var h = stack.mallocInt(1);
	        org.lwjgl.glfw.GLFW.glfwGetWindowSize(handle, w, h);
	        return new int[]{ w.get(0), h.get(0) };
	    }
	}
}
