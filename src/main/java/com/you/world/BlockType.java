package com.you.world;

public record BlockType(
        int id,
        String name,
        boolean solid,
        int px, int py,  // +X
        int nx, int ny,  // -X
        int ux, int uy,  // +Y
        int dx, int dy,  // -Y
        int fzx, int fzy,// +Z
        int bzx, int bzy // -Z
) {
    // face: 0..5 = +X,-X,+Y,-Y,+Z,-Z
    public int tileX(int face) {
        return switch (face) {
            case 0 -> px;
            case 1 -> nx;
            case 2 -> ux;
            case 3 -> dx;
            case 4 -> fzx;
            case 5 -> bzx;
            default -> 0;
        };
    }

    public int tileY(int face) {
        return switch (face) {
            case 0 -> py;
            case 1 -> ny;
            case 2 -> uy;
            case 3 -> dy;
            case 4 -> fzy;
            case 5 -> bzy;
            default -> 0;
        };
    }
}
