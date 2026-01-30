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
        init();
        loop();
        cleanup();
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
        //glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
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
        renderer.init(
                "/shaders/basic.vert",
                "/shaders/basic.frag",
                "src/main/resources/textures/blocks.png"
        );

        atlas = renderer.getAtlas();
        hud = new HudRenderer(atlas, 16, player);
    }

    private void loop() {
        time.reset();

        while (!window.shouldClose()) {
            window.pollEvents();

            float dt = time.update();

            input.update();
            camera.updateLook(input);

            // player update (movement + collisions) and camera follows player
            physics.update(player, camera, world, input, dt);

            // interactions
            handleBlockInteraction();

            // render
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.render(window, camera, world);

            // HUD last
            hud.render(window);
            //System.out.println("caps: " + org.lwjgl.opengl.GL.getCapabilities());

            window.swapBuffers();
        }
    }

    private void handleBlockInteraction() {
        float maxDist = 6.0f;

        // hotbar selection via mouse wheel
        int wheel = input.consumeScroll();
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

                // mesh: mark block's chunk + neighbors dirty (optional if setBlock already does this)
                world.markDirtyAtBlock(hit.hitX, hit.hitY, hit.hitZ);
            }
        }

        if (input.mouseJustPressedRight()) {
            RayHit hit = raycast.raycast(world, camera.getPosition(), camera.getForward(), maxDist);
            if (hit.hit) {
                if (!world.isSolidBlock(hit.placeX, hit.placeY, hit.placeZ) &&
                    !physics.aabbIntersectsBlock(player.getPos(), player.getHalf(), hit.placeX, hit.placeY, hit.placeZ)) {

                    // place block from selected hotbar
                    ItemStack sel = player.getInventory().getSelected();
                    short toPlace = sel == null ? Blocks.AIR : sel.packed;
                    if (toPlace != Blocks.AIR) {
                        short old = world.setBlockReturningOld(hit.placeX, hit.placeY, hit.placeZ, toPlace);
                        lighting.onBlockChanged(world, hit.placeX, hit.placeY, hit.placeZ, old, toPlace);

                        // decrease stack if not creative
                        player.getInventory().placeOneFromSelected();

                        world.markDirtyAtBlock(hit.placeX, hit.placeY, hit.placeZ);
                    }
                }
            }
        }
    }


    private void cleanup() {
        if (renderer != null) renderer.cleanup();
        if (window != null) window.destroy();
    }
}
