package com.you.game;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFrontFace;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL;

import com.you.engine.Camera;
import com.you.engine.Input;
import com.you.engine.Texture;
import com.you.engine.Time;
import com.you.engine.Window;
import com.you.physics.Physics;
import com.you.player.Player;
import com.you.render.HudRenderer;
import com.you.render.Renderer;
import com.you.sound.BlockSoundAction;
import com.you.sound.BlockSoundRegistry;
import com.you.sound.FootstepSystem;
import com.you.sound.SoundManager;
import com.you.sound.SoundMaterial;
import com.you.world.Blocks;
import com.you.world.Lighting;
import com.you.world.Mesher;
import com.you.world.RayHit;
import com.you.world.Raycast;
import com.you.world.World;

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
	
	private SoundManager sound;
	private BlockSoundRegistry blockSounds;
	private FootstepSystem footsteps;

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
		Blocks.loadAtlasMap("/atlas.txt");
		Blocks.initDefaults(); // registers block types
		world.generateTestWorld();
		lighting.rebuildAll(world);
		
		// ===== AUDIO INIT + REGISTRATION =====
	    sound = new SoundManager();
	    sound.init();

	    blockSounds = new BlockSoundRegistry(sound);

	    Path base = Paths.get("src/main/resources/sounds/blocks");
	    registerBlockSounds(base);
	    
	    world.setBlockSoundRegistry(blockSounds);
	    footsteps = new FootstepSystem(blockSounds);
	    // ===== END AUDIO =====

		player = new Player(new Vector3f(0f, 3f, 5f));
		physics = new Physics();

		raycast = new Raycast();
		mesher = new Mesher();

		renderer = new Renderer(mesher);
		renderer.init("/shaders/basic.vert", "/shaders/basic.frag", "src/main/resources/atlas.png");

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
				sound.setListener(
					    camera.getPosition().x,
					    camera.getPosition().y,
					    camera.getPosition().z,
					    camera.getForward().x,
					    camera.getForward().y,
					    camera.getForward().z,
					    0f, 1f, 0f // up vector
					);
				
				handleBlockInteraction();
			} else {
				hud.update(input, window);
			}
			physics.update(player, camera, world, input, dt);
			footsteps.update(world, player, player.isOnGround(), false);

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
				short old = world.destroyBlockReturningOld(hit.hitX, hit.hitY, hit.hitZ, Blocks.AIR);
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
		        
		        if (Blocks.isReplaceable(hitBlock)) {
		            tx = hit.hitX; ty = hit.hitY; tz = hit.hitZ;
		        }

		        if (!world.isSolidBlock(tx, ty, tz) &&
		            !physics.aabbIntersectsBlock(player.getPos(), player.getHalf(), tx, ty, tz)) {

		            short place = player.getInventory().getSelected().packed;
		            short below = world.getBlock(tx, ty-1, tz);
		            //check that the selected block is not air (so that plants won't be replaced by it)
		            if(!(place==0)) {
		            	//check if block is (plant and being placed on a valid block) or (not a plant) and therefore all placements are valid
			            //TODO Add a method to blocks that takes selected block + block it's being placed onto and returns whether it's a valid placement
		            	if ((Blocks.isPlant(place) && Blocks.canPlantGrowOn(below)) || !Blocks.isPlant(place)) {
			            	short old = world.placeBlockReturningOld(tx, ty, tz, place);
				            lighting.onBlockChanged(world, tx, ty, tz, old, place);
				            world.markDirtyAtBlock(tx, ty, tz);
			            }
		            } 
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
	
	private void registerBlockSounds(Path base) {
		try {
			blockSounds.register(SoundMaterial.GRASS, BlockSoundAction.PLACE,
			        List.of("dirt_place_1","dirt_place_2"), base);
			blockSounds.register(SoundMaterial.GRASS, BlockSoundAction.BREAK,
			        List.of("dirt_break_1", "dirt_break_2"), base);
			blockSounds.register(SoundMaterial.DIRT, BlockSoundAction.PLACE,
			        List.of("dirt_place_1","dirt_place_2"), base);
			blockSounds.register(SoundMaterial.DIRT, BlockSoundAction.BREAK,
			        List.of("dirt_break_1", "dirt_break_2"), base);
			blockSounds.register(SoundMaterial.STONE, BlockSoundAction.PLACE,
			        List.of("stone_place_1"), base);
			blockSounds.register(SoundMaterial.STONE, BlockSoundAction.BREAK,
			        List.of("stone_break_1","stone_break_2"), base);
			blockSounds.register(SoundMaterial.GLASS, BlockSoundAction.PLACE,
			        List.of("glass_place_1"), base);
			blockSounds.register(SoundMaterial.GLASS, BlockSoundAction.BREAK,
			        List.of("glass_break_1", "glass_break_2"), base);
			blockSounds.register(SoundMaterial.SAND, BlockSoundAction.PLACE,
			        List.of("sand_place_1","sand_place_2"), base);
			blockSounds.register(SoundMaterial.SAND, BlockSoundAction.BREAK,
			        List.of("sand_break_1"), base);
			blockSounds.register(SoundMaterial.GLASS, BlockSoundAction.PLACE,
			        List.of("glass_place_1"), base);
			blockSounds.register(SoundMaterial.GLASS, BlockSoundAction.BREAK,
			        List.of("glass_break_1", "glass_break_2"), base);
			blockSounds.register(SoundMaterial.PLANT, BlockSoundAction.PLACE,
			        List.of("plant_place_1","plant_place_2"), base);
			blockSounds.register(SoundMaterial.PLANT, BlockSoundAction.BREAK,
			        List.of("plant_break_1", "plant_break_2"), base);
			
			blockSounds.register(SoundMaterial.GRASS, BlockSoundAction.STEP,
			        List.of("grass_step_1","grass_step_2","grass_step_3"), base);
			blockSounds.register(SoundMaterial.DIRT, BlockSoundAction.STEP,
			        List.of("dirt_step_1","dirt_step_2"), base);
			blockSounds.register(SoundMaterial.STONE, BlockSoundAction.STEP,
			        List.of("stone_step_1","stone_step_2","stone_step_3"), base);
			blockSounds.register(SoundMaterial.GLASS, BlockSoundAction.STEP,
					List.of("stone_step_1","stone_step_2","stone_step_3"), base);
			blockSounds.register(SoundMaterial.SAND, BlockSoundAction.STEP,
			        List.of("sand_step_1","sand_step_2"), base);
			blockSounds.register(SoundMaterial.PLANT, BlockSoundAction.STEP,
			        List.of("plant_step_1","plant_step_2"), base);
			blockSounds.register(SoundMaterial.AIR, BlockSoundAction.STEP,
			        List.of("silence"), base);
	    }
	    catch (Exception e){
	    	System.out.println(e);
	    }	   
	}
}
