package com.you.world;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.you.sound.SoundMaterial;

public class Blocks {

    // Kinds (not packed). Up to 4096 kinds (12 bits).
    public static final int AIR_KIND   = 0;
    public static final int GRASS_KIND = 1;
    public static final int DIRT_KIND  = 2;
    public static final int STONE_KIND = 3;
    public static final int GLASS_KIND = 4;
    public static final int STONEBRICK_KIND = 5;
    public static final int SAND_KIND = 6;
    public static final int MOSSYCOBBLE_KIND = 7;
    public static final int MOSSYSTONEBRICK_KIND = 8;
    public static final int PLANT_GRASS_KIND = 9;
    public static final int PLANT_TRITELEIA_KIND = 10;
    public static final int MUSHROOM_RED_KIND = 11;
    public static final int MUSHROOM_BROWN_KIND = 12;
    public static final int MUSHROOM_BLUE_KIND = 13;
    public static final int PLANT_GOLD_POPPY_KIND = 14;
    public static final int COBBLESTONE_KIND = 15;
    public static final int TORCH_KIND = 16;
    public static final int PLANKS_OAK_KIND = 17;
    public static final int LOG_OAK_KIND = 18;


    // Packed default values (kind + state 0)
    public static final short AIR   = BlockData.pack(AIR_KIND, 0);
    public static final short GRASS = BlockData.pack(GRASS_KIND, 0);
    public static final short DIRT  = BlockData.pack(DIRT_KIND, 0);
    public static final short STONE = BlockData.pack(STONE_KIND, 0);
    public static final short GLASS = BlockData.pack(GLASS_KIND, 0);
    public static final short STONEBRICK = BlockData.pack(STONEBRICK_KIND, 0);
    public static final short SAND = BlockData.pack(SAND_KIND, 0);
    public static final short MOSSYCOBBLE = BlockData.pack(MOSSYCOBBLE_KIND, 0);
    public static final short MOSSYSTONEBRICK = BlockData.pack(MOSSYSTONEBRICK_KIND, 0);
    public static final short PLANT_GRASS = BlockData.pack(PLANT_GRASS_KIND, 0);
    public static final short PLANT_TRITELEIA = BlockData.pack(PLANT_TRITELEIA_KIND, 0);
    public static final short MUSHROOM_RED = BlockData.pack(MUSHROOM_RED_KIND, 0);
    public static final short MUSHROOM_BROWN = BlockData.pack(MUSHROOM_BROWN_KIND, 0);
    public static final short MUSHROOM_BLUE = BlockData.pack(MUSHROOM_BLUE_KIND, 0);
    public static final short PLANT_GOLD_POPPY = BlockData.pack(PLANT_GOLD_POPPY_KIND, 0);
    public static final short COBBLESTONE = BlockData.pack(COBBLESTONE_KIND, 0);
    public static final short TORCH = BlockData.pack(TORCH_KIND, 0);
    public static final short PLANKS_OAK = BlockData.pack(PLANKS_OAK_KIND, 0);

    private static final int MAX_KINDS = 4096; // matches 12-bit kind
    private static final BlockType[] byKind = new BlockType[MAX_KINDS];
 // NEW: optional per-packed overrides (keyed by full 16-bit packed value)
    private static final BlockType[] byPacked = new BlockType[1 << 16];
    
 // Torch states stored in BlockData.state(packed) (0..15 available)
    public static final int TORCH_FLOOR = 0;
    public static final int TORCH_WEST  = 1; // attached to west wall (wall is -X), torch leans +X
    public static final int TORCH_EAST  = 2; // wall is +X, torch leans -X
    public static final int TORCH_NORTH = 3; // wall is -Z, torch leans +Z
    public static final int TORCH_SOUTH = 4; // wall is +Z, torch leans -Z
    //log axes
    public static final int LOG_UP    = 0; // +Y
    public static final int LOG_DOWN  = 1; // -Y
    public static final int LOG_EAST  = 2; // +X
    public static final int LOG_WEST  = 3; // -X
    public static final int LOG_SOUTH = 4; // +Z  (match your coordinate convention)
    public static final int LOG_NORTH = 5; // -Z
 // 2 bits per face (6 faces) => 12 bits total fits in a short.
 // Layout: rot for face i stored at (i*2)
 private static final short[] uvRotPacked = new short[1 << 16];

    // Return the BlockType for a packed short (or null if unknown)
    public static BlockType get(short packed) {
        BlockType v = byPacked[packed & 0xFFFF];
        if (v != null) return v;

        int k = BlockData.kind(packed);
        if (k < 0 || k >= MAX_KINDS) return null;
        return byKind[k];
    }

    // Return by kind id
    public static BlockType getKind(int kind) {
        if (kind < 0 || kind >= MAX_KINDS) return null;
        return byKind[kind];
    }
    
    private static void register(BlockType t) {
        int k = t.id();
        if (k < 0 || k >= MAX_KINDS) throw new IllegalArgumentException("Block kind out of range: " + k);
        byKind[k] = t;
    }
    
    private static void registerPacked(short packed, BlockType t) {
        byPacked[packed & 0xFFFF] = t;
    }

    public static void initDefaults() {
    	register(new BlockType(AIR_KIND, "air", false,
                0,0, 0,0, 0,0, 0,0, 0,0, 0,0
        ));

        // ---- grass block: side + top + bottom ----
        int[] grassSide = t("grass_side");
        int[] grassTop  = t("grass_top");
        int[] grassBottom      = t("grass_bottom");

        register(new BlockType(GRASS_KIND, "grass", true,
                grassSide[0], grassSide[1],   // +X
                grassSide[0], grassSide[1],   // -X
                grassTop[0],  grassTop[1],    // +Y
                grassBottom[0],  grassBottom[1],        // -Y
                grassSide[0], grassSide[1],   // +Z
                grassSide[0], grassSide[1]    // -Z
        ));

        // ---- dirt ----
        int[] dirt     = t("dirt");
        register(new BlockType(DIRT_KIND, "dirt", true,
                dirt[0], dirt[1],  dirt[0], dirt[1],
                dirt[0], dirt[1],  dirt[0], dirt[1],
                dirt[0], dirt[1],  dirt[0], dirt[1]
        ));

        // ---- stone ----
        int[] stone = t("stone");
        register(new BlockType(STONE_KIND, "stone", true,
                stone[0], stone[1], stone[0], stone[1],
                stone[0], stone[1], stone[0], stone[1],
                stone[0], stone[1], stone[0], stone[1]
        ));

        // ---- glass (cutout) ----
        int[] glass = t("glass");
        register(new BlockType(GLASS_KIND, "glass", true,
                glass[0], glass[1], glass[0], glass[1],
                glass[0], glass[1], glass[0], glass[1],
                glass[0], glass[1], glass[0], glass[1]
        ));

        // ---- stone brick ----
        int[] stonebrick = t("stone_bricks");
        register(new BlockType(STONEBRICK_KIND, "stone brick", true,
                stonebrick[0], stonebrick[1], stonebrick[0], stonebrick[1],
                stonebrick[0], stonebrick[1], stonebrick[0], stonebrick[1],
                stonebrick[0], stonebrick[1], stonebrick[0], stonebrick[1]
        ));

        // ---- sand ----
        int[] sand = t("sand");
        register(new BlockType(SAND_KIND, "sand", true,
                sand[0], sand[1], sand[0], sand[1],
                sand[0], sand[1], sand[0], sand[1],
                sand[0], sand[1], sand[0], sand[1]
        ));

        // ---- mossy cobble ----
        int[] mossyCobble = t("cobblestone_mossy");
        register(new BlockType(MOSSYCOBBLE_KIND, "mossy cobblestone", true,
                mossyCobble[0], mossyCobble[1], mossyCobble[0], mossyCobble[1],
                mossyCobble[0], mossyCobble[1], mossyCobble[0], mossyCobble[1],
                mossyCobble[0], mossyCobble[1], mossyCobble[0], mossyCobble[1]
        ));

        // ---- mossy stone brick ----
        int[] mossyStoneBrick = t("stone_bricks_mossy");
        register(new BlockType(MOSSYSTONEBRICK_KIND, "mossy stone brick", true,
                mossyStoneBrick[0], mossyStoneBrick[1], mossyStoneBrick[0], mossyStoneBrick[1],
                mossyStoneBrick[0], mossyStoneBrick[1], mossyStoneBrick[0], mossyStoneBrick[1],
                mossyStoneBrick[0], mossyStoneBrick[1], mossyStoneBrick[0], mossyStoneBrick[1]
        ));

        // ---- plants (cross-model uses same tile on all faces in your BlockType) ----
        int[] plantGrass = t("grass");
        register(new BlockType(PLANT_GRASS_KIND, "grass", false,
                plantGrass[0], plantGrass[1], plantGrass[0], plantGrass[1],
                plantGrass[0], plantGrass[1], plantGrass[0], plantGrass[1],
                plantGrass[0], plantGrass[1], plantGrass[0], plantGrass[1]
        ));

        int[] triteleia = t("triplet_lily");
        register(new BlockType(PLANT_TRITELEIA_KIND, "triplet lily", false,
                triteleia[0], triteleia[1], triteleia[0], triteleia[1],
                triteleia[0], triteleia[1], triteleia[0], triteleia[1],
                triteleia[0], triteleia[1], triteleia[0], triteleia[1]
        ));

        int[] redMush = t("mushroom_red");
        register(new BlockType(MUSHROOM_RED_KIND, "red mushroom", false,
                redMush[0], redMush[1], redMush[0], redMush[1],
                redMush[0], redMush[1], redMush[0], redMush[1],
                redMush[0], redMush[1], redMush[0], redMush[1]
        ));

        int[] brownMush = t("mushroom_brown");
        register(new BlockType(MUSHROOM_BROWN_KIND, "brown mushroom", false,
                brownMush[0], brownMush[1], brownMush[0], brownMush[1],
                brownMush[0], brownMush[1], brownMush[0], brownMush[1],
                brownMush[0], brownMush[1], brownMush[0], brownMush[1]
        ));
        
        int[] blueMush = t("mushroom_blue");
        register(new BlockType(MUSHROOM_BLUE_KIND, "blue mushroom", false,
                blueMush[0], blueMush[1], blueMush[0], blueMush[1],
                blueMush[0], blueMush[1], blueMush[0], blueMush[1],
                blueMush[0], blueMush[1], blueMush[0], blueMush[1]
        ));
        
        int[] goldPoppy = t("golden_poppy");
        register(new BlockType(PLANT_GOLD_POPPY_KIND, "golden poppy", false,
        		goldPoppy[0], goldPoppy[1], goldPoppy[0], goldPoppy[1],
        		goldPoppy[0], goldPoppy[1], goldPoppy[0], goldPoppy[1],
        		goldPoppy[0], goldPoppy[1], goldPoppy[0], goldPoppy[1]
        ));
        
        int[] cobblestone = t("cobblestone");
        register(new BlockType(COBBLESTONE_KIND, "cobblestone", true,
        		cobblestone[0], cobblestone[1], cobblestone[0], cobblestone[1],
        		cobblestone[0], cobblestone[1], cobblestone[0], cobblestone[1],
        		cobblestone[0], cobblestone[1], cobblestone[0], cobblestone[1]
        ));
        
        int[] torch = t("torch");
        register(new BlockType(TORCH_KIND, "torch", false,
        		torch[0], torch[1], torch[0], torch[1],
        		torch[0], torch[1], torch[0], torch[1],
        		torch[0], torch[1], torch[0], torch[1]
        ));
        
        int[] oak_planks = t("oak_planks");
        register(new BlockType(PLANKS_OAK_KIND, "sand", true,
        		oak_planks[0], oak_planks[1], oak_planks[0], oak_planks[1],
        		oak_planks[0], oak_planks[1], oak_planks[0], oak_planks[1],
        		oak_planks[0], oak_planks[1], oak_planks[0], oak_planks[1]
        ));
        int[] logSide = t("oak_log_side");
        int[] logEnd  = t("oak_log_top");

        // Base kind (fallback): pick Y (vertical)
        register(new BlockType(LOG_OAK_KIND, "log", true,
                logSide[0], logSide[1],   // +X
                logSide[0], logSide[1],   // -X
                logEnd[0],  logEnd[1],    // +Y
                logEnd[0],  logEnd[1],    // -Y
                logSide[0], logSide[1],   // +Z
                logSide[0], logSide[1]    // -Z
        ));

        // Packed base for this kind (state will be overwritten)
        short baseLog = BlockData.pack(LOG_OAK_KIND, 0);

        BlockType logY = new BlockType(LOG_OAK_KIND, "log_y", true,
                logSide[0], logSide[1],  logSide[0], logSide[1],
                logEnd[0],  logEnd[1],   logEnd[0],  logEnd[1],
                logSide[0], logSide[1],  logSide[0], logSide[1]);

        BlockType logX = new BlockType(LOG_OAK_KIND, "log_x", true,
                logEnd[0],  logEnd[1],   logEnd[0],  logEnd[1],
                logSide[0], logSide[1],  logSide[0], logSide[1],
                logSide[0], logSide[1],  logSide[0], logSide[1]);

        BlockType logZ = new BlockType(LOG_OAK_KIND, "log_z", true,
                logSide[0], logSide[1],  logSide[0], logSide[1],
                logSide[0], logSide[1],  logSide[0], logSide[1],
                logEnd[0],  logEnd[1],   logEnd[0],  logEnd[1]);
        
        short pUp    = BlockData.withState(baseLog, LOG_UP);
        short pDown  = BlockData.withState(baseLog, LOG_DOWN);
        short pEast  = BlockData.withState(baseLog, LOG_EAST);
        short pWest  = BlockData.withState(baseLog, LOG_WEST);
        short pSouth = BlockData.withState(baseLog, LOG_SOUTH);
        short pNorth = BlockData.withState(baseLog, LOG_NORTH);

        registerPacked(pUp,    logY);
        registerPacked(pDown,  logY);

        registerPacked(pEast,  logX);
        registerPacked(pWest,  logX);

        registerPacked(pSouth, logZ);
        registerPacked(pNorth, logZ);
        
     // faces: 0=+X,1=-X,2=+Y,3=-Y,4=+Z,5=-Z

     // helper: apply same rot to all faces except the two end faces
     java.util.function.BiConsumer<Short, int[]> apply = (packed, rots) -> {
         for (int f = 0; f < 6; f++) setUvRot(packed, f, rots[f]);
     };

     // LOG_UP (axis Y): sides 0/1/4/5 rot 0
     apply.accept(pUp,   new int[]{0,0,0,0,0,0});

     // LOG_DOWN: flip bark on the sides (180)
     apply.accept(pDown, new int[]{2,2,0,0,2,2});

     // LOG_EAST (+X): ends are faces 0/1, rotate the other four faces so bark points +X
     apply.accept(pEast, new int[]{0,0,1,3,1,3});

     // LOG_WEST (-X): flip from EAST
     apply.accept(pWest, new int[]{0,0,3,1,3,1});

     // LOG_SOUTH (+Z): ends are faces 4/5, rotate the other four faces so bark points +Z
     apply.accept(pSouth,new int[]{1,3,1,3,0,0});

     // LOG_NORTH (-Z): flip from SOUTH
     apply.accept(pNorth,new int[]{3,1,3,1,0,0});


    }

 // -------- Atlas mapping (name -> tileX,tileY) --------

    private static final class Tile {
        final int x, y;
        Tile(int x, int y) { this.x = x; this.y = y; }
    }

    private static final Map<String, Tile> ATLAS = new HashMap<>();
    private static boolean atlasLoaded = false;

    /**
     * Load atlas mapping file generated by AtlasPacker.
     * File format:
     *   # comments...
     *   name tileX tileY
     */
    public static void loadAtlasMap(String resourcePath) {
        ATLAS.clear();

        try (InputStream in = Blocks.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing atlas map resource: " + resourcePath);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int lineNo = 0;
                while ((line = br.readLine()) != null) {
                    lineNo++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] parts = line.split("\\s+");
                    if (parts.length < 3) {
                        throw new IllegalStateException("Bad atlas line " + lineNo + ": " + line);
                    }

                    String name = parts[0];
                    int tx = Integer.parseInt(parts[1]);
                    int ty = Integer.parseInt(parts[2]);

                    ATLAS.put(name, new Tile(tx, ty));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed loading atlas map: " + resourcePath, e);
        }

        atlasLoaded = true;
    }

    /** Returns tile coords for a given atlas tile name. Hard-fails if missing. */
    private static int[] t(String tileName) {
        if (!atlasLoaded) {
            throw new IllegalStateException("Atlas map not loaded. Call Blocks.loadAtlasMap(...) before initDefaults().");
        }
        Tile tile = ATLAS.get(tileName);
        if (tile == null) {
            throw new IllegalArgumentException("Missing tile in atlas.txt: '" + tileName + "'");
        }
        return new int[]{ tile.x, tile.y };
    }

    /** Convenience: returns coords or falls back to another tile name if missing. */
    private static int[] tOr(String tileName, String fallbackTileName) {
        if (!atlasLoaded) {
            throw new IllegalStateException("Atlas map not loaded. Call Blocks.loadAtlasMap(...) before initDefaults().");
        }
        Tile tile = ATLAS.get(tileName);
        if (tile == null) tile = ATLAS.get(fallbackTileName);
        if (tile == null) {
            throw new IllegalArgumentException("Missing tile in atlas.txt: '" + tileName + "' (and fallback '" + fallbackTileName + "')");
        }
        return new int[]{ tile.x, tile.y };
    }
    
    // --- helpers that operate on packed short values ---

    public static boolean isSolid(short packed) {
        BlockType t = get(packed);
        int kind = BlockData.kind(packed);
        if (kind == PLANT_GRASS_KIND) return false;
        if (kind == PLANT_TRITELEIA_KIND) return false;
        if (kind == TORCH_KIND) return false;
        return t != null && t.solid();
    }

    // opacity in 0..15 (15 = fully opaque)
    public static int opacity(short packed) {
        int kind = BlockData.kind(packed);
        if (kind == AIR_KIND) return 0;
        if (kind == GLASS_KIND) return 0; // glass considered non-opaque for lighting/face-culling rules
        if (kind == PLANT_GRASS_KIND) return 0;
        if (kind == PLANT_TRITELEIA_KIND) return 0;
        if (kind == MUSHROOM_RED_KIND) return 0;
        if (kind == MUSHROOM_BROWN_KIND) return 0;
        if (kind == MUSHROOM_BLUE_KIND) return 0;
        if (kind == PLANT_GOLD_POPPY_KIND) return 0;
        if (kind == TORCH_KIND) return 0;
        return 15;
    }

    // emission 0..15
    public static int emission(short packed) {
        int kind = BlockData.kind(packed);
        if (kind == MUSHROOM_BLUE_KIND) return 5;
        if (kind == TORCH_KIND) return 14;
        //if (kind == GLOW_KIND) return 15;
        return 0;
    }

    // helpers about render classification
    public static boolean isTransparent(short packed) {
        return BlockData.kind(packed) != AIR_KIND && !isOpaque(packed);
    }

    public static boolean isTranslucent(short packed) {
        // e.g. water, etc. Add kinds and logic later.
        return false;
    }

    public static boolean isOpaque(short packed) {
        int kind = BlockData.kind(packed);
        if (kind == AIR_KIND) return false;
        if (isCutout(packed)) return false;
        if (isTranslucent(packed)) return false;
        return true;
    }

    public static boolean isRenderable(short packed) {
        return BlockData.kind(packed) != AIR_KIND;
    }
    
    public static boolean isPlant(short packed) {
        int k = BlockData.kind(packed);
        if (k== PLANT_GRASS_KIND) return true;
        if (k== PLANT_TRITELEIA_KIND) return true;
        if (k== MUSHROOM_RED_KIND) return true;
        if (k== MUSHROOM_BROWN_KIND) return true;
        if (k== MUSHROOM_BLUE_KIND) return true;
        if (k== PLANT_GOLD_POPPY_KIND) return true;
        return false;
    }

    public static boolean isCutout(short packed) {
        int k = BlockData.kind(packed);
        return k == GLASS_KIND || k == TORCH_KIND || isPlant(packed);
    }
    
    public static boolean isHittable(short packed) {
        // Anything that isn't air can be targeted by the ray (plants, glass, etc.)
        return BlockData.kind(packed) != AIR_KIND;
    }
    
    public static boolean isReplaceable(short packed) {
        int kind = BlockData.kind(packed);
        return kind == PLANT_GRASS_KIND;
    }
    
    public static boolean canPlantGrowOn(short belowPacked) {
    	int k = BlockData.kind(belowPacked);
    	if (k== GRASS_KIND) return true;
    	if (k== DIRT_KIND) return true;
    	return false;
    }
    
    public static boolean isTorch(short packed) {
        return BlockData.kind(packed) == TORCH_KIND;
    }
    
    public static SoundMaterial getMaterialForId(short id) {
    	int k = BlockData.kind(id);
    	if (k== GRASS_KIND) return SoundMaterial.GRASS;
    	if (k== DIRT_KIND) return SoundMaterial.DIRT;
    	if (k== STONE_KIND) return SoundMaterial.STONE;
    	if (k== GLASS_KIND) return SoundMaterial.GLASS;
    	if (k== SAND_KIND) return SoundMaterial.SAND;
    	if (isPlant(id)) return SoundMaterial.PLANT;
    	if (k== AIR_KIND) return SoundMaterial.AIR;
    	if (k== PLANKS_OAK_KIND) return SoundMaterial.WOOD;
    	if (k== TORCH_KIND) return SoundMaterial.WOOD;
    	if (k== LOG_OAK_KIND) return SoundMaterial.WOOD;
    	return SoundMaterial.STONE;   	
    }
    
    public static int torchState(short packed) {
        return BlockData.state(packed) & 0xF;
    }

    public static short withTorchState(short packed, int state) {
        return BlockData.withState(packed, state);
    }
    
    public static boolean isLog(short packed) {
        // however you classify blocks; placeholder:
    	 return BlockData.kind(packed) == LOG_OAK_KIND;
    }
    
    public static int logAxis(short packed) {
        return BlockData.state(packed) & 0xF;
    }

    public static short withLogAxis(short packed, int axis) {
        return BlockData.withState(packed, axis);
    }
    
    private static void setUvRot(short packed, int face, int rot) {
        int idx = packed & 0xFFFF;
        int shift = (face & 7) * 2;
        int mask = 3 << shift;
        int v = uvRotPacked[idx] & ~mask;
        v |= (rot & 3) << shift;
        uvRotPacked[idx] = (short) v;
    }

    public static int uvRot(short packed, int face) {
        int idx = packed & 0xFFFF;
        int shift = (face & 7) * 2;
        int rot = (uvRotPacked[idx] >>> shift) & 3;

        // --- FIX: top/bottom faces use a different UV basis in emitFace() ---
        // Apply a +90° bias (or -90°) only for the problematic horizontal log states.
        if (isLog(packed) && (face == 2 || face == 3)) {
            int st = BlockData.state(packed) & 0xF;

            boolean xAxis = (st == LOG_EAST || st == LOG_WEST);
            boolean zAxis = (st == LOG_SOUTH || st == LOG_NORTH);

            // Choose which axis is wrong:
            if (zAxis) {
                rot = (rot + 1) & 3;   // try +1 first (fixes 90°)
            }
            // If instead it’s NORTH/SOUTH that’s wrong, swap to:
            // if (zAxis) rot = (rot + 1) & 3;
        }

        return rot;
    }
    
}
