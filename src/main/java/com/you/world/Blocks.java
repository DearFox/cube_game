package com.you.world;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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

    private static final int MAX_KINDS = 4096; // matches 12-bit kind
    private static final BlockType[] byKind = new BlockType[MAX_KINDS];

    // Return the BlockType for a packed short (or null if unknown)
    public static BlockType get(short packed) {
        int kind = BlockData.kind(packed);
        if (kind < 0 || kind >= MAX_KINDS) return null;
        return byKind[kind];
    }

    // Return by kind id
    public static BlockType getKind(int kind) {
        if (kind < 0 || kind >= MAX_KINDS) return null;
        return byKind[kind];
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
        int[] stone = t("cobblestone");
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
    }

    private static void register(BlockType t) {
        int k = t.id();
        if (k < 0 || k >= MAX_KINDS) throw new IllegalArgumentException("Block kind out of range: " + k);
        byKind[k] = t;
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
        return 15;
    }

    // emission 0..15
    public static int emission(short packed) {
        int kind = BlockData.kind(packed);
        if (kind == MUSHROOM_BLUE_KIND) return 5;
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
        return false;
    }

    public static boolean isCutout(short packed) {
        int k = BlockData.kind(packed);
        return k == GLASS_KIND || isPlant(packed);
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
}
