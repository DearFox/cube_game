package com.you.render;

import com.you.engine.Window;
import com.you.player.Player;
import com.you.world.BlockType;
import com.you.world.Blocks;
import com.you.engine.Texture; // your Texture class
import com.you.engine.ShaderProgram; // adjust package if ShaderProgram is elsewhere

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import org.lwjgl.system.MemoryStack;
import com.you.engine.Input;
import com.you.player.PlayerInventory;
import com.you.world.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * HUD with hotbar icons. Construct with the atlas texture and tile size.
 *
 * Usage: // after renderer.init(...) so atlas exists: hud = new
 * HudRenderer(atlas, 16, player); ... hud.render(window);
 */
public class HudRenderer {

	private final Texture atlas;
	private final int atlasW, atlasH;
	private final int tileSizePx;
	private final Player player;

	private final ShaderProgram uiShader;
	private final ShaderProgram uiColorShader;

	private int vao = 0;
	private int vbo = 0;

	// quad vertex layout: pos.x pos.y (pixels), uv.x uv.y
	// 6 verts * 4 floats = 24 floats
	private static final int VERT_FLOATS = 4;
	private static final int VERT_COUNT = 6;
	private static final int VERT_STRIDE_BYTES = VERT_FLOATS * Float.BYTES;

	private boolean inventoryOpen = false;

	private int creativeCols = 9;
	private int creativeCell = 40;
	private int creativePad = 6;
	private int creativeRowsVisible = 5;
	private int creativeScrollRow = 0;

	// grid rect for hit-testing (framebuffer pixel coords, TOP-LEFT origin)
	private int gridX, gridY, gridW, gridH;

	public HudRenderer(Texture atlas, int tileSizePx, Player player) {
		this.atlas = atlas;
		this.atlasW = atlas.width();
		this.atlasH = atlas.height();
		this.tileSizePx = tileSizePx;
		this.player = player;

		// load small UI shader (vertex + fragment)
		uiShader = ShaderProgram.loadFromResources("/shaders/ui.vert", "/shaders/ui.frag");
		uiColorShader = ShaderProgram.loadFromResources("/shaders/ui.vert", "/shaders/ui_color.frag");

		// create a small quad VAO/VBO (we'll stream quad vertices each draw)
		vao = glGenVertexArrays();
		vbo = glGenBuffers();
		System.out.println("HUD vao=" + vao + " vbo=" + vbo);
		int err = glGetError();
		if (err != GL_NO_ERROR)
			System.err.println("HUD constructor GL error: " + err);

		glBindVertexArray(vao);
		glCheck("HUD after glBindVertexArray");
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glCheck("HUD after glBindBuffer ARRAY_BUFFER");

		glCheck("HUD after VAO/VBO bind");

		glBufferData(GL_ARRAY_BUFFER, VERT_COUNT * VERT_FLOATS * Float.BYTES, GL_DYNAMIC_DRAW);

		// location 0: vec2 position (pixel coords)
		glVertexAttribPointer(0, 2, GL_FLOAT, false, VERT_STRIDE_BYTES, 0L);
		glEnableVertexAttribArray(0);

		// location 1: vec2 uv
		glVertexAttribPointer(1, 2, GL_FLOAT, false, VERT_STRIDE_BYTES, 2L * Float.BYTES);
		glEnableVertexAttribArray(1);

		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);

		if (vao == 0 || vbo == 0) {
			throw new IllegalStateException(
					"HUD VAO/VBO not created. Did you construct HudRenderer before GL.createCapabilities()?");
		}
	}

	public void render(Window window) {
		glDrainErrors("HUD start");

		// Ensure default framebuffer/back buffer (renderer may have used MRT)
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glDrawBuffer(GL_BACK);
		glReadBuffer(GL_BACK);

		int[] fb = window.framebufferSize();
		int fbw = Math.max(1, fb[0]);
		int fbh = Math.max(1, fb[1]);

		// ---- HUD state (authoritative) ----
		glViewport(0, 0, fbw, fbh);

		// Save GL state we change
		boolean prevDepth = glIsEnabled(GL_DEPTH_TEST);
		boolean prevCull = glIsEnabled(GL_CULL_FACE);
		boolean prevBlend = glIsEnabled(GL_BLEND);

		float[] oldClear = new float[4];
		glGetFloatv(GL_COLOR_CLEAR_VALUE, oldClear);

		// UI canonical state
		glDisable(GL_DEPTH_TEST);
		glDepthMask(false);
		glDepthFunc(GL_ALWAYS); // robust for HUD
		glDisable(GL_CULL_FACE);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		// Draw crosshair (keeps its own scissor behavior)
		//TODO is this neccessary with the drawcrosshair below?
		drawCrosshair(fbw, fbh); 	
		glCheck("HUD after crosshair");

		if (inventoryOpen) {
			renderCreativeInventory(fbw, fbh);
		}

		// Prepare hotbar geometry
		Player p = player;
		if (p == null) {
			drawCrosshair(fbw, fbh);
			return;
		}
		var inv = p.getInventory();
		if (inv == null) {
			drawCrosshair(fbw, fbh);
			return;
		}

		int slots = 9;
		int slotSize = 28;
		int padding = 4;
		int totalW = slots * slotSize + (slots - 1) * padding;
		int startX = (fbw - totalW) / 2;
		int y = fbh - 40;

		// --- Hotbar background + selection clears (scissor-only, once) ---
		glEnable(GL_SCISSOR_TEST);

		// background behind whole hotbar
		scissorTL(fbw, fbh, startX - 8, y - 8, totalW + 16, slotSize + 16);
		glClearColor(0.07f, 0.07f, 0.07f, 0.9f);
		glClear(GL_COLOR_BUFFER_BIT);

		// selection highlight (outline + inner)
		int sel = inv.getSelectedSlot();
		int selX = startX + sel * (slotSize + padding);

		// outline
		scissorTL(fbw, fbh, selX - 2, y - 2, slotSize + 4, slotSize + 4);
		glClearColor(0.9f, 0.75f, 0.2f, 1f);
		glClear(GL_COLOR_BUFFER_BIT);

		// inner
		scissorTL(fbw, fbh, selX, y, slotSize, slotSize);
		glClearColor(0.15f, 0.15f, 0.15f, 1f);
		glClear(GL_COLOR_BUFFER_BIT);

		// done with scissor-clears
		glDisable(GL_SCISSOR_TEST);
		glCheck("HUD after scissor clears");

		// UI state for icon draw (no scissor, no culling)
		glDisable(GL_DEPTH_TEST);
		glDepthMask(false);
		glDisable(GL_CULL_FACE);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		// Use textured UI shader
		uiShader.use();
		atlas.bind(0);
		uiShader.setInt("uTex", 0);
		uiShader.setVec2("uViewport", (float) fbw, (float) fbh);
		glCheck("HUD after ui shader setup");

		// Bind VAO/VBO once and stream quads per slot
		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);

		for (int i = 0; i < slots; i++) {
			glCheck("HUD loop top slot " + i);
			int sx = startX + i * (slotSize + padding);

			var stack = inv.hotbar[i];
			short packed = (stack == null) ? Blocks.AIR : stack.packed;
			if (packed == Blocks.AIR)
				continue;

			BlockType bt = Blocks.get(packed);
			if (bt == null)
				continue;
			int tileX = bt.tileX(2);
			int tileY = bt.tileY(2);

			float[] uv = tileUV(tileX, tileY, atlasW, atlasH, tileSizePx);
			float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

			float x0 = sx;
			float y0 = y;
			float x1 = sx + slotSize;
			float y1 = y + slotSize;

			float[] verts = new float[] { x0, y0, u0, v1, x1, y1, u1, v0, x1, y0, u1, v1, x0, y0, u0, v1, x0, y1, u0,
					v0, x1, y1, u1, v0 };

			try (MemoryStack ms = MemoryStack.stackPush()) {
				FloatBuffer fbuff = ms.mallocFloat(verts.length);
				fbuff.put(verts).flip();
				glBufferSubData(GL_ARRAY_BUFFER, 0, fbuff);
			}

			glDrawArrays(GL_TRIANGLES, 0, VERT_COUNT);
			glCheck("HUD after glDrawArrays slot " + i);
		}

		// unbind
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
		uiShader.useNone();

		// restore depth state changed for HUD
		glDepthFunc(GL_LESS);
		glDepthMask(true);

		// restore GL state
		if (prevBlend)
			glEnable(GL_BLEND);
		else
			glDisable(GL_BLEND);
		if (prevCull)
			glEnable(GL_CULL_FACE);
		else
			glDisable(GL_CULL_FACE);
		if (prevDepth)
			glEnable(GL_DEPTH_TEST);
		else
			glDisable(GL_DEPTH_TEST);

		glClearColor(oldClear[0], oldClear[1], oldClear[2], oldClear[3]);
		glCheck("HUD end before restore");
	}

	private void drawCrosshair(int fbw, int fbh) {
		int cx = fbw / 2;
		int cy = fbh / 2;

		int length = 14;
		int thickness = 2;
		int gap = 4;

		boolean scissorWasOn = glIsEnabled(GL_SCISSOR_TEST);
		boolean depthWasOn = glIsEnabled(GL_DEPTH_TEST);
		boolean cullWasOn = glIsEnabled(GL_CULL_FACE);

		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
		glEnable(GL_SCISSOR_TEST);

		float[] oldClear = new float[4];
		glGetFloatv(GL_COLOR_CLEAR_VALUE, oldClear);

		glClearColor(1f, 1f, 1f, 1f);

		glScissor(cx - gap - length, cy - thickness / 2, length, thickness);
		glClear(GL_COLOR_BUFFER_BIT);

		glScissor(cx + gap, cy - thickness / 2, length, thickness);
		glClear(GL_COLOR_BUFFER_BIT);

		glScissor(cx - thickness / 2, cy - gap - length, thickness, length);
		glClear(GL_COLOR_BUFFER_BIT);

		glScissor(cx - thickness / 2, cy + gap, thickness, length);
		glClear(GL_COLOR_BUFFER_BIT);

		if (!scissorWasOn)
			glDisable(GL_SCISSOR_TEST);
		if (cullWasOn)
			glEnable(GL_CULL_FACE);
		if (depthWasOn)
			glEnable(GL_DEPTH_TEST);
	}

	// tile coords (tileX,tileY) where (0,0) is TOP-LEFT in your image editor.
	// Because the atlas is loaded with STB flip=true, we do NOT flip tileY here.
	private static float[] tileUV(int tileX, int tileY, int atlasW, int atlasH, int tileSizePx) {
		int tilesPerRow = atlasW / tileSizePx;
		int tilesPerCol = atlasH / tileSizePx;

		if (tileX < 0)
			tileX = 0;
		if (tileY < 0)
			tileY = 0;
		if (tileX >= tilesPerRow)
			tileX = tilesPerRow - 1;
		if (tileY >= tilesPerCol)
			tileY = tilesPerCol - 1;

		float uSize = (float) tileSizePx / (float) atlasW;
		float vSize = (float) tileSizePx / (float) atlasH;

		float u0 = tileX * uSize;
		float v0 = tileY * vSize;
		float u1 = u0 + uSize;
		float v1 = v0 + vSize;

		return new float[] { u0, v0, u1, v1 };
	}

	public void cleanup() {
		if (vbo != 0)
			glDeleteBuffers(vbo);
		if (vao != 0)
			glDeleteVertexArrays(vao);
		if (uiShader != null)
			uiShader.delete();
	}

	private static void glDrainErrors(String where) {
		int err;
		boolean any = false;
		while ((err = glGetError()) != GL_NO_ERROR) {
			System.err.println(where + " drained GL error: " + err);
			any = true;
		}
		if (any)
			System.err.println("----");
	}

	private static void glCheck(String where) {
		int err = glGetError();
		if (err != GL_NO_ERROR) {
			System.err.println(where + " GL error: " + err);

			// Dump common “why is this invalid?” state in core profile
			int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
			int vbo = glGetInteger(GL_ARRAY_BUFFER_BINDING);
			int prog = glGetInteger(GL_CURRENT_PROGRAM);
			int fbo = glGetInteger(GL_FRAMEBUFFER_BINDING);
			int sc = glIsEnabled(GL_SCISSOR_TEST) ? 1 : 0;
			int dt = glIsEnabled(GL_DEPTH_TEST) ? 1 : 0;
			int bl = glIsEnabled(GL_BLEND) ? 1 : 0;

			System.err.println("  state: fbo=" + fbo + " prog=" + prog + " vao=" + vao + " vbo=" + vbo + " scissor="
					+ sc + " depth=" + dt + " blend=" + bl);

			// drain any additional queued errors
			glDrainErrors(where);
		}
	}

	private static void scissorTL(int fbw, int fbh, int x, int yTop, int w, int h) {
		// Convert from top-left origin (your UI coords) to OpenGL scissor (bottom-left
		// origin)
		int y = fbh - (yTop + h);
		glScissor(x, y, w, h);
	}

	public boolean isInventoryOpen() {
		return inventoryOpen;
	}

	public void toggleInventoryOpen() {
		inventoryOpen = !inventoryOpen;
		// reset scroll optionally
		// if (inventoryOpen) creativeScrollRow = 0;
	}

	public void update(com.you.engine.Input input, com.you.engine.Window window) {
		if (!inventoryOpen)
			return;

		var inv = player.getInventory();
		var entries = inv.getCreativeEntries();

		// scroll rows
		float s = input.consumeScrollDY();
		if (s != 0f) {
			creativeScrollRow -= (int) Math.signum(s); // scroll up -> smaller row
			if (creativeScrollRow < 0)
				creativeScrollRow = 0;
		}

		// clamp scroll based on entry count
		int totalRows = (entries.size() + creativeCols - 1) / creativeCols;
		int maxScroll = Math.max(0, totalRows - creativeRowsVisible);
		if (creativeScrollRow > maxScroll)
			creativeScrollRow = maxScroll;

		// click to pick
		if (input.mouseJustPressedLeft()) {
			int[] fb = window.framebufferSize();
			int fbw = Math.max(1, fb[0]);
			int fbh = Math.max(1, fb[1]);

			int[] win = window.windowSize(); // IMPORTANT (HiDPI)
			float sx = (float) fbw / Math.max(1, win[0]);
			float sy = (float) fbh / Math.max(1, win[1]);

			int mx = (int) Math.round(input.mouseX() * sx);
			int my = (int) Math.round(input.mouseY() * sy);

			// hit-test grid (gridX/gridY are TOP-LEFT)
			if (mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH) {
				int relX = mx - gridX;
				int relY = my - gridY;

				int step = creativeCell + creativePad;
				int col = relX / step;
				int row = relY / step;

				if (col >= 0 && col < creativeCols && row >= 0 && row < creativeRowsVisible) {
					int inView = row * creativeCols + col;
					int idx = creativeScrollRow * creativeCols + inView;

					if (idx >= 0 && idx < entries.size()) {
						short packed = entries.get(idx);
						inv.setSlot(inv.getSelectedSlot(), packed, 64);
					}
				}
			}
		}
	}

	private void renderCreativeInventory(int fbw, int fbh) {
		var inv = player.getInventory();
		var entries = inv.getCreativeEntries();

		// --- Make sure blending is enabled for alpha compositing ---
		boolean prevBlend = glIsEnabled(GL_BLEND);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		// Use color shader and the same VAO/VBO you already have for UI quads.
		uiColorShader.use();
		uiColorShader.setVec2("uViewport", (float) fbw, (float) fbh);

		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);

		// helper lambda to draw a filled rect in pixel coords using the streaming VBO
		java.util.function.Consumer<float[]> drawRect = (float[] verts) -> {
			try (MemoryStack ms = MemoryStack.stackPush()) {
				java.nio.FloatBuffer fbuff = ms.mallocFloat(verts.length);
				fbuff.put(verts).flip();
				glBufferSubData(GL_ARRAY_BUFFER, 0, fbuff);
			}
			glDrawArrays(GL_TRIANGLES, 0, VERT_COUNT);
		};

		// --- Fullscreen dim (semi-transparent black) ---
		uiColorShader.setVec4("uColor", 0f, 0f, 0f, 0.55f); // RGBA
		float[] fullVerts = new float[] {
				// x, y, u, v (u/v ignored by color shader)
				0f, 0f, 0f, 0f, fbw, fbh, 0f, 0f, fbw, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, fbh, 0f, 0f, fbw, fbh, 0f, 0f };
		drawRect.accept(fullVerts);

		// --- Panel background (slightly lighter) ---
		int panelW = Math.min(fbw - 80, creativeCols * creativeCell + (creativeCols - 1) * creativePad + 40);
		int panelH = Math.min(fbh - 120,
				creativeRowsVisible * creativeCell + (creativeRowsVisible - 1) * creativePad + 80);
		int panelX = (fbw - panelW) / 2;
		int panelY = (fbh - panelH) / 2;

		uiColorShader.setVec4("uColor", 0.10f, 0.10f, 0.10f, 0.92f);
		float[] panelVerts = new float[] { panelX, panelY, 0f, 0f, panelX + panelW, panelY + panelH, 0f, 0f,
				panelX + panelW, panelY, 0f, 0f, panelX, panelY, 0f, 0f, panelX, panelY + panelH, 0f, 0f,
				panelX + panelW, panelY + panelH, 0f, 0f };
		drawRect.accept(panelVerts);

		// --- grid background inset inside panel ---
		gridX = panelX + 20;
		gridY = panelY + 40;
		int step = creativeCell + creativePad;
		gridW = creativeCols * creativeCell + (creativeCols - 1) * creativePad;
		gridH = creativeRowsVisible * creativeCell + (creativeRowsVisible - 1) * creativePad;

		uiColorShader.setVec4("uColor", 0.07f, 0.07f, 0.07f, 0.95f);
		float[] gridVerts = new float[] { gridX - 10, gridY - 10, 0f, 0f, gridX - 10 + gridW + 20,
				gridY - 10 + gridH + 20, 0f, 0f, gridX - 10 + gridW + 20, gridY - 10, 0f, 0f, gridX - 10, gridY - 10,
				0f, 0f, gridX - 10, gridY - 10 + gridH + 20, 0f, 0f, gridX - 10 + gridW + 20, gridY - 10 + gridH + 20,
				0f, 0f };
		drawRect.accept(gridVerts);

		// Done with color draws: restore bindings and shader
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
		uiColorShader.useNone();

		if (!prevBlend)
			glDisable(GL_BLEND);

		// draw icons
		uiShader.use();
		atlas.bind(0);
		uiShader.setInt("uTex", 0);
		uiShader.setVec2("uViewport", (float) fbw, (float) fbh);

		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);

		int startIndex = creativeScrollRow * creativeCols;
		int endIndex = Math.min(entries.size(), startIndex + creativeRowsVisible * creativeCols);

		for (int idx = startIndex; idx < endIndex; idx++) {
			int local = idx - startIndex;
			int row = local / creativeCols;
			int col = local % creativeCols;

			int x = gridX + col * step;
			int y = gridY + row * step;

			short packed = entries.get(idx);
			if (packed == Blocks.AIR)
				continue;

			BlockType bt = Blocks.get(packed);
			if (bt == null)
				continue;

			int tileX = bt.tileX(2);
			int tileY = bt.tileY(2);

			float[] uv = tileUV(tileX, tileY, atlasW, atlasH, tileSizePx);
			float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

			float x0 = x, y0 = y, x1 = x + creativeCell, y1 = y + creativeCell;

			float[] verts = new float[] { x0, y0, u0, v1, x1, y1, u1, v0, x1, y0, u1, v1, x0, y0, u0, v1, x0, y1, u0,
					v0, x1, y1, u1, v0 };
			/*
			float[] verts = new float[] { x0, y0, u0, v0, x1, y1, u1, v1, x1, y0, u1, v0, x0, y0, u0, v0, x0, y1, u0,
					v1, x1, y1, u1, v1 };*/

			try (MemoryStack ms = MemoryStack.stackPush()) {
				FloatBuffer fbuff = ms.mallocFloat(verts.length);
				fbuff.put(verts).flip();
				glBufferSubData(GL_ARRAY_BUFFER, 0, fbuff);
			}

			glDrawArrays(GL_TRIANGLES, 0, VERT_COUNT);
		}

		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
		uiShader.useNone();
	}

}
