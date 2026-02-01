package com.you.render;

import com.you.engine.*;
import com.you.world.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import org.lwjgl.system.MemoryStack;
import java.nio.IntBuffer;
import static org.lwjgl.opengl.GL33.*;

public class Renderer {

	private final Mesher mesher;

	private ShaderProgram shader;
	private Texture atlas;

	private final HashMap<ChunkPos, ChunkMeshGpu> gpu = new HashMap<>();

	// Lighting
	private float lightX = -0.4f, lightY = -1.0f, lightZ = -0.2f;
	private float ambX = 0.20f, ambY = 0.20f, ambZ = 0.20f;

	// Atlas settings
	private int tileSizePx = 16;

	// OIT FBO
	private int oitFbo = 0;
	private int oitAccumTex = 0;
	private int oitRevealTex = 0;
	private int oitDepthRbo = 0;

	private ShaderProgram transShader;
	private ShaderProgram oitCompositeShader;

	// fullscreen quad
	private int fsVao = 0;
	private int fsVbo = 0;

	private static final int FLOATS_PER_VERT = 9; // pos3 + normal3 + uv2 + light1
	private static final int STRIDE_BYTES = FLOATS_PER_VERT * Float.BYTES;

	private int lastFbW = -1, lastFbH = -1;
	
	public Renderer(Mesher mesher) {
		this.mesher = mesher;
	}

	public void init(String vertRes, String fragRes, String atlasPath) {
		shader = ShaderProgram.loadFromResources(vertRes, fragRes);
		transShader = ShaderProgram.loadFromResources("/shaders/basic.vert", "/shaders/translucent.frag");
		oitCompositeShader = ShaderProgram.loadFromResources("/shaders/oit_quad.vert", "/shaders/oit_composite.frag");
		createFullscreenQuad();
		atlas = Texture.load(atlasPath, true);
		System.out.println("Loaded atlas: " + atlas.width() + "x" + atlas.height());
	}

	public void render(Window window, Camera camera, World world) {
		boolean indexed = supportsIndexedBlending();
		if (shader == null)
			return;

		window.applyViewportIfNeeded();
		int[] fb = window.framebufferSize();
		int fbw = Math.max(1, fb[0]);
		int fbh = Math.max(1, fb[1]);

		ensureOitBuffers(fbw, fbh);

		// matrices
		Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), (float) fbw / (float) fbh, 0.1f,
				1000f);
		Matrix4f view = camera.getViewMatrix();
		Matrix4f viewProj = new Matrix4f(proj).mul(view);

		// rebuild dirty meshes
		for (Chunk c : world.getChunks()) {
			if (!c.dirty)
				continue;
			Mesher.MeshTriple tri = mesher.buildChunkMeshes(world, c, atlas.width(), atlas.height(), tileSizePx);
			uploadOrUpdate(c.pos, tri.opaque, tri.cutout, tri.transparent);
			c.dirty = false;
		}

		// -------------------------
		// PASS 1: SOLID (opaque + cutout glass) 
		// -------------------------
		//TODO put glass back here
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glViewport(0, 0, fbw, fbh);

		glDisable(GL_BLEND);
		glEnable(GL_DEPTH_TEST);
		glDepthMask(true);
		glEnable(GL_CULL_FACE);
		glCullFace(GL_BACK);

		shader.use();
		atlas.bind(0);
		shader.setInt("uTex", 0);

		shader.setMat4("uViewProj", viewProj);
		shader.setMat4("uModel", new Matrix4f().identity());

		shader.setVec3("uLightDir", lightX, lightY, lightZ);
		shader.setVec3("uAmbient", ambX, ambY, ambZ);
		shader.setVec3("uColor", 1f, 1f, 1f);

		for (Chunk c : world.getChunks()) {
			ChunkMeshGpu m = gpu.get(c.pos);
			if (m == null || m.countOpaque == 0)
				continue;
			glBindVertexArray(m.vaoOpaque);
			glDrawArrays(GL_TRIANGLES, 0, m.countOpaque);
		}
		glBindVertexArray(0);
		
		// -------------------------
		// PASS 1.5: CUTOUT (alpha test, writes depth, no blending, no culling)
		// For: plants, cutout glass, leaves, etc.
		// -------------------------
		glDisable(GL_BLEND);
		glEnable(GL_DEPTH_TEST);
		glDepthMask(true);

		// IMPORTANT: disable culling so cross-plants render from all angles
		glDisable(GL_CULL_FACE);

		// same shader + same atlas; your basic.frag already does alpha discard
		shader.use();
		atlas.bind(0);
		shader.setInt("uTex", 0);

		shader.setMat4("uViewProj", viewProj);
		shader.setMat4("uModel", new Matrix4f().identity());
		shader.setVec3("uLightDir", lightX, lightY, lightZ);
		shader.setVec3("uAmbient", ambX, ambY, ambZ);
		shader.setVec3("uColor", 1f, 1f, 1f);

		for (Chunk c : world.getChunks()) {
		    ChunkMeshGpu m = gpu.get(c.pos);
		    if (m == null || m.countCutout == 0) continue;
		    glBindVertexArray(m.vaoCutout);
		    glDrawArrays(GL_TRIANGLES, 0, m.countCutout);
		}
		glBindVertexArray(0);

		// restore culling state for later passes (optional; your translucent pass disables it anyway)
		glEnable(GL_CULL_FACE);
		glCullFace(GL_BACK);

		// If you currently draw HUD later, keep that after composite.

		// PASS 2: transparent
		boolean anyTrans = false;
		for (Chunk c : world.getChunks()) {
		    ChunkMeshGpu m = gpu.get(c.pos);
		    if (m != null && m.countTrans > 0) { anyTrans = true; break; }
		}

		if (anyTrans) {
		    if (indexed) {
		        // --- your existing OIT Pass 2 + Pass 3 exactly as you have it ---
		        // (keep your current code)
		    } else {
		        // --- fallback: classic alpha blending straight to main framebuffer ---
		        glBindFramebuffer(GL_FRAMEBUFFER, 0);
		        glViewport(0, 0, fbw, fbh);

		        glEnable(GL_DEPTH_TEST);
		        glDepthMask(false);              // don't write depth
		        glDisable(GL_CULL_FACE);         // or keep enabled if you want
		        glEnable(GL_BLEND);
		        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		        transShader.use();
		        atlas.bind(0);
		        transShader.setInt("uTex", 0);

		        transShader.setMat4("uViewProj", viewProj);
		        transShader.setMat4("uModel", new Matrix4f().identity());
		        transShader.setVec3("uLightDir", lightX, lightY, lightZ);
		        transShader.setVec3("uAmbient", ambX, ambY, ambZ);

		        for (Chunk c : world.getChunks()) {
		            ChunkMeshGpu m = gpu.get(c.pos);
		            if (m == null || m.countTrans == 0) continue;
		            glBindVertexArray(m.vaoTrans);
		            glDrawArrays(GL_TRIANGLES, 0, m.countTrans);
		        }
		        glBindVertexArray(0);

		        // restore
		        setDefaultFramebufferDrawBuffers();
		        glDisable(GL_BLEND);
		        glDepthMask(true);
		        glEnable(GL_CULL_FACE);
		        glCullFace(GL_BACK);
		        glFrontFace(GL_CCW);
		        
		     // IMPORTANT: restore default framebuffer draw/read buffers after MRT/OIT
		        glBindFramebuffer(GL_FRAMEBUFFER, 0);
		        glDrawBuffer(GL_BACK);
		        glReadBuffer(GL_BACK);
		    }
		}
	}

	private void uploadOrUpdate(ChunkPos pos, float[] opaque, float[] cutout, float[] trans) {
	    // Never allow null arrays
	    if (opaque == null) opaque = new float[0];
	    if (cutout == null) cutout = new float[0];
	    if (trans == null) trans = new float[0];

	    ChunkMeshGpu m = gpu.get(pos);
	    if (m == null) {
	        m = new ChunkMeshGpu();
	        gpu.put(pos, m);

	        // --- opaque VAO/VBO ---
	        m.vaoOpaque = glGenVertexArrays();
	        m.vboOpaque = glGenBuffers();
	        setupVao(m.vaoOpaque, m.vboOpaque);

	        // --- cutout VAO/VBO ---
	        m.vaoCutout = glGenVertexArrays();
	        m.vboCutout = glGenBuffers();
	        setupVao(m.vaoCutout, m.vboCutout);

	        // --- translucent VAO/VBO ---
	        m.vaoTrans = glGenVertexArrays();
	        m.vboTrans = glGenBuffers();
	        setupVao(m.vaoTrans, m.vboTrans);
	    }

	    // Upload opaque
	    glBindBuffer(GL_ARRAY_BUFFER, m.vboOpaque);
	    glBufferData(GL_ARRAY_BUFFER, opaque, GL_STATIC_DRAW);
	    m.countOpaque = opaque.length / FLOATS_PER_VERT;

	    // Upload cutout
	    glBindBuffer(GL_ARRAY_BUFFER, m.vboCutout);
	    glBufferData(GL_ARRAY_BUFFER, cutout, GL_STATIC_DRAW);
	    m.countCutout = cutout.length / FLOATS_PER_VERT;

	    // Upload translucent
	    glBindBuffer(GL_ARRAY_BUFFER, m.vboTrans);
	    glBufferData(GL_ARRAY_BUFFER, trans, GL_STATIC_DRAW);
	    m.countTrans = trans.length / FLOATS_PER_VERT;

	    glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	private void setupVao(int vao, int vbo) {
		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);

		// location 0: vec3 position
		glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE_BYTES, 0L);
		glEnableVertexAttribArray(0);

		// location 1: vec3 normal
		glVertexAttribPointer(1, 3, GL_FLOAT, false, STRIDE_BYTES, 3L * Float.BYTES);
		glEnableVertexAttribArray(1);

		// location 2: vec2 uv
		glVertexAttribPointer(2, 2, GL_FLOAT, false, STRIDE_BYTES, 6L * Float.BYTES);
		glEnableVertexAttribArray(2);

		// location 3: float light
		glVertexAttribPointer(3, 1, GL_FLOAT, false, STRIDE_BYTES, 8L * Float.BYTES);
		glEnableVertexAttribArray(3);

		// Unbind cleanly
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	public void cleanup() {
		for (ChunkMeshGpu m : gpu.values()) {
			if (m.vboOpaque != 0)
				glDeleteBuffers(m.vboOpaque);
			if (m.vaoOpaque != 0)
				glDeleteVertexArrays(m.vaoOpaque);
			if (m.vboTrans != 0)
				glDeleteBuffers(m.vboTrans);
			if (m.vaoTrans != 0)
				glDeleteVertexArrays(m.vaoTrans);
		}
		gpu.clear();

		if (fsVbo != 0)
			glDeleteBuffers(fsVbo);
		if (fsVao != 0)
			glDeleteVertexArrays(fsVao);

		if (oitAccumTex != 0)
			glDeleteTextures(oitAccumTex);
		if (oitRevealTex != 0)
			glDeleteTextures(oitRevealTex);
		if (oitDepthRbo != 0)
			glDeleteRenderbuffers(oitDepthRbo);
		if (oitFbo != 0)
			glDeleteFramebuffers(oitFbo);

		if (atlas != null)
			atlas.delete();
		if (shader != null)
			shader.delete();
		if (transShader != null)
			transShader.delete();
		if (oitCompositeShader != null)
			oitCompositeShader.delete();
	}

	private void createFullscreenQuad() {
		float[] v = {
				// pos // uv
				-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f,

				-1f, -1f, 0f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f };
		fsVao = glGenVertexArrays();
		fsVbo = glGenBuffers();
		glBindVertexArray(fsVao);
		glBindBuffer(GL_ARRAY_BUFFER, fsVbo);
		glBufferData(GL_ARRAY_BUFFER, v, GL_STATIC_DRAW);
		int stride = 4 * Float.BYTES;
		glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
		glEnableVertexAttribArray(1);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	private void ensureOitBuffers(int w, int h) {
		if (w == lastFbW && h == lastFbH && oitFbo != 0)
			return;
		lastFbW = w;
		lastFbH = h;

		// delete old
		if (oitAccumTex != 0)
			glDeleteTextures(oitAccumTex);
		if (oitRevealTex != 0)
			glDeleteTextures(oitRevealTex);
		if (oitDepthRbo != 0)
			glDeleteRenderbuffers(oitDepthRbo);
		if (oitFbo != 0)
			glDeleteFramebuffers(oitFbo);

		oitFbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, oitFbo);

		// accum
		oitAccumTex = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, oitAccumTex);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, oitAccumTex, 0);

		// revealage
		oitRevealTex = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, oitRevealTex);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_R16F, w, h, 0, GL_RED, GL_FLOAT, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, oitRevealTex, 0);

		// depth (so translucent respects solids)
		oitDepthRbo = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, oitDepthRbo);
		glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, oitDepthRbo);

		int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		if (status != GL_FRAMEBUFFER_COMPLETE) {
			throw new RuntimeException("OIT FBO incomplete: " + status);
		}

		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}

	private boolean supportsIndexedBlending() {
		var caps = org.lwjgl.opengl.GL.getCapabilities();
		return caps.OpenGL40 || caps.GL_ARB_draw_buffers_blend;
	}

	private void blendFuncIndex(int buf, int sfactor, int dfactor) {
		var caps = org.lwjgl.opengl.GL.getCapabilities();
		if (caps.OpenGL40) {
			org.lwjgl.opengl.GL40C.glBlendFunci(buf, sfactor, dfactor);
		} else if (caps.GL_ARB_draw_buffers_blend) {
			org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFunciARB(buf, sfactor, dfactor);
		} else {
			throw new IllegalStateException(
					"Indexed blending not supported (need OpenGL 4.0 or GL_ARB_draw_buffers_blend).");
		}
	}
	
	public Texture getAtlas() { return atlas; }
	
	private static void setDefaultFramebufferDrawBuffers() {
	    // Default framebuffer wants BACK (or BACK_LEFT) as draw buffer.
	    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
	    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);

	    // Use glDrawBuffers(…) not just glDrawBuffer, to fully reset MRT state.
	    try (MemoryStack stack = MemoryStack.stackPush()) {
	        IntBuffer bufs = stack.mallocInt(1);
	        bufs.put(0, GL_BACK); // if this errors on your platform, switch to GL_BACK_LEFT
	        glDrawBuffers(bufs);
	    }

	    glDrawBuffer(GL_BACK);
	    glReadBuffer(GL_BACK);
	}
}
