package com.you.world;

public class Blocks {

    // Kinds (not packed). Up to 4096 kinds (12 bits).
    public static final int AIR_KIND   = 0;
    public static final int GRASS_KIND = 1;
    public static final int DIRT_KIND  = 2;
    public static final int STONE_KIND = 3;
    public static final int GLASS_KIND = 4;
    public static final int GLOW_KIND  = 5; // example glow block

    // Packed default values (kind + state 0)
    public static final short AIR   = BlockData.pack(AIR_KIND, 0);
    public static final short GRASS = BlockData.pack(GRASS_KIND, 0);
    public static final short DIRT  = BlockData.pack(DIRT_KIND, 0);
    public static final short STONE = BlockData.pack(STONE_KIND, 0);
    public static final short GLASS = BlockData.pack(GLASS_KIND, 0);
    public static final short GLOW  = BlockData.pack(GLOW_KIND, 0);

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

        register(new BlockType(GLOW_KIND, "glow", true,
                3,2,  3,2,  3,2,  3,2,  3,2,  3,2
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
        return t != null && t.solid();
    }

    // opacity in 0..15 (15 = fully opaque)
    public static int opacity(short packed) {
        int kind = BlockData.kind(packed);
        if (kind == AIR_KIND) return 0;
        if (kind == GLASS_KIND) return 0; // glass considered non-opaque for lighting/face-culling rules
        return 15;
    }

    // emission 0..15
    public static int emission(short packed) {
        int kind = BlockData.kind(packed);
        if (kind == GLOW_KIND) return 15;
        return 0;
    }

    // helpers about render classification
    public static boolean isTransparent(short packed) {
        return BlockData.kind(packed) != AIR_KIND && !isOpaque(packed);
    }

    public static boolean isCutout(short packed) {
        return BlockData.kind(packed) == GLASS_KIND; // binary alpha
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
}
