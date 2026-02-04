package com.you.world;

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
        // register block types by kind. BlockType.id() should return the kind
        register(new BlockType(AIR_KIND, "air", false,
                // faces don't matter for air
                0,0, 0,0, 0,0, 0,0, 0,0, 0,0
        ));

        // Face order: +X,-X,+Y,-Y,+Z,-Z
        // Each face uses a tileX,tileY in your atlas grid
        register(new BlockType(GRASS_KIND, "grass", true,
                0,3,  0,3,  3,3,  1,3,  0,3,  0,3
        ));

        register(new BlockType(DIRT_KIND, "dirt", true,
                1,3,  1,3,  1,3,  1,3,  1,3,  1,3
        ));

        register(new BlockType(STONE_KIND, "stone", true,
                2,3,  2,3,  2,3,  2,3,  2,3,  2,3
        ));

        register(new BlockType(GLASS_KIND, "glass", true,
                2,2,  2,2,  2,2,  2,2,  2,2,  2,2
        ));
        register(new BlockType(STONEBRICK_KIND, "stone brick", true,
                3,2,  3,2,  3,2,  3,2,  3,2,  3,2
        ));
        register(new BlockType(SAND_KIND, "sand", true,
                0,1,  0,1,  0,1,  0,1,  0,1,  0,1
        ));
        register(new BlockType(MOSSYCOBBLE_KIND, "mossy cobblestone", true,
                1,1,  1,1,  1,1,  1,1,  1,1,  1,1
        ));
        register(new BlockType(MOSSYSTONEBRICK_KIND, "mossy stone brick", true,
                2,1,  2,1,  2,1,  2,1,  2,1,  2,1
        ));
        register(new BlockType(PLANT_GRASS_KIND, "grass", true,
                3,1,  3,1,  3,1,  3,1,  3,1,  3,1
        ));
        register(new BlockType(PLANT_TRITELEIA_KIND, "triplet lily", true,
                0,0,  0,0,  0,0,  0,0,  0,0,  0,0
        ));
    }

    private static void register(BlockType t) {
        int k = t.id();
        if (k < 0 || k >= MAX_KINDS) throw new IllegalArgumentException("Block kind out of range: " + k);
        byKind[k] = t;
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
        return 15;
    }

    // emission 0..15
    public static int emission(short packed) {
        int kind = BlockData.kind(packed);
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
}
