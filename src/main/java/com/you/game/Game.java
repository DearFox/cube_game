package com.you.game;

import com.you.engine.*;
import com.you.physics.Physics;
import com.you.player.*;
import com.you.render.*;
import com.you.world.*;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

public class Game {

	private Window window;
	private Input input;
	private Time time;
	private Camera camera;

	private World world;
	private Player player;
	private Physics physics;

	private Raycast raycast;
	private Mesher mesher;

	private Renderer renderer;
	private HudRenderer hud;

	private Texture atlas;

	Lighting lighting = new Lighting();

	public void run() {
		System.out.println("PID: " + ProcessHandle.current().pid());
		try {
			init();
			loop();
			System.out.println("Loop exited");
		} finally {
			cleanup();
			System.out.println("Cleanup finished");
		}
	}

	private void init() {
		window = new Window(1280, 720, "cube_game");
		window.create();

		// Create OpenGL capabilities AFTER context is current
		GL.createCapabilities();

		// Basic GL state
		glEnable(GL_DEPTH_TEST);
		glEnable(GL_CULL_FACE);
		// glEnable(GL_BLEND);
		// glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glDisable(GL_BLEND);
		glDepthMask(true);
		glCullFace(GL_BACK);
		glFrontFace(GL_CCW);
		glClearColor(0.55f, 0.75f, 0.95f, 1f);

		input = new Input(window);
		input.attachCallbacks();
		input.setCursorLocked(true);

		time = new Time();
		camera = new Camera(new Vector3f(0f, 1.5f, 5f));

		world = new World();
		Blocks.initDefaults(); // registers block types
		world.generateTestWorld();
		lighting.rebuildAll(world);

		player = new Player(new Vector3f(0f, 3f, 5f));
		physics = new Physics();

		raycast = new Raycast();
		mesher = new Mesher();

		renderer = new Renderer(mesher);
		renderer.init("/shaders/basic.vert", "/shaders/basic.frag", "src/main/resources/textures/blocks.png");

		atlas = renderer.getAtlas();
		hud = new HudRenderer(atlas, 16, player);
	}

	private void loop() {
		time.reset();

		while (!window.shouldClose()) {
			window.pollEvents();

			float dt = time.update();

			input.update();

			if (input.keyJustPressed(GLFW_KEY_E)) {
				hud.toggleInventoryOpen();
				input.setCursorLocked(!hud.isInventoryOpen());
			}

			if (!hud.isInventoryOpen()) {
				camera.updateLook(input);
				
				handleBlockInteraction();
			} else {
				hud.update(input, window);
			}
			physics.update(player, camera, world, input, dt);

			// render
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			renderer.render(window, camera, world);

			// HUD last
			hud.render(window);

			window.swapBuffers();
		}
	}

	private void handleBlockInteraction() {
		float maxDist = 6.0f;

		// hotbar selection via mouse wheel
		//int wheel = input.consumeScroll();
		int wheel = Math.round(input.consumeScrollDY());
		if (wheel != 0) {
			player.getInventory().cycleSelected(wheel);
		}

		// number keys 1..9 to select hotbar
		for (int i = 0; i < 9; i++) {
			int key = GLFW_KEY_1 + i;
			if (input.keyDown(key)) {
				player.getInventory().setSelectedSlot(i);
				break;
			}
		}

		if (input.mouseJustPressedLeft()) {
			RayHit hit = raycast.raycast(world, camera.getPosition(), camera.getForward(), maxDist);
			if (hit.hit) {
				// remove the hit block
				short old = world.setBlockReturningOld(hit.hitX, hit.hitY, hit.hitZ, Blocks.AIR);
				lighting.onBlockChanged(world, hit.hitX, hit.hitY, hit.hitZ, old, Blocks.AIR);

				// mesh: mark block's chunk + neighbors dirty (optional if setBlock already does
				// this)
				world.markDirtyAtBlock(hit.hitX, hit.hitY, hit.hitZ);
			}
		}

		if (input.mouseJustPressedRight()) {
		    RayHit hit = raycast.raycast(world, camera.getPosition(), camera.getForward(), maxDist);
		    if (hit.hit) {

		        // Decide where to place:
		        // - replace plants/non-solid blocks directly
		        // - otherwise place adjacent (classic)
		        int tx = hit.placeX, ty = hit.placeY, tz = hit.placeZ;
		        short hitBlock = world.getBlock(hit.hitX, hit.hitY, hit.hitZ);
		        
		        short hb = world.getBlock(hit.hitX, hit.hitY, hit.hitZ);
		        System.out.println("hit kind=" + BlockData.kind(hb) + " bt=" + Blocks.get(hb));
		        
		        if (Blocks.isReplaceable(hitBlock)) {
		            tx = hit.hitX; ty = hit.hitY; tz = hit.hitZ;
		        }

		        if (!world.isSolidBlock(tx, ty, tz) &&
		            !physics.aabbIntersectsBlock(player.getPos(), player.getHalf(), tx, ty, tz)) {

		            short place = player.getInventory().getSelected().packed; // or Blocks.GRASS for now
		            short old = world.setBlockReturningOld(tx, ty, tz, place);
		            lighting.onBlockChanged(world, tx, ty, tz, old, place);
		            world.markDirtyAtBlock(tx, ty, tz);
		        }
		    }
		}

	}

	private void cleanup() {
		if (renderer != null)
			renderer.cleanup();
		if (hud != null)
			hud.cleanup();
		if (window != null)
			window.destroy();	
	}
}
