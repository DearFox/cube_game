package com.you.world;

import org.joml.Vector3f;

public class Raycast {

    public RayHit raycast(World world, Vector3f origin, Vector3f dirIn, float maxDist) {
        RayHit res = new RayHit();

        Vector3f dir = new Vector3f(dirIn);
        if (dir.lengthSquared() < 1e-6f) return res;
        dir.normalize();

        // shifted cell space
        float ox = origin.x + 0.5f;
        float oy = origin.y + 0.5f;
        float oz = origin.z + 0.5f;

        float dx = dir.x;
        float dy = dir.y;
        float dz = dir.z;

        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);

        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        float tDeltaX = (stepX != 0) ? Math.abs(1.0f / dx) : Float.POSITIVE_INFINITY;
        float tDeltaY = (stepY != 0) ? Math.abs(1.0f / dy) : Float.POSITIVE_INFINITY;
        float tDeltaZ = (stepZ != 0) ? Math.abs(1.0f / dz) : Float.POSITIVE_INFINITY;

        float tMaxX;
        if (stepX > 0)      tMaxX = ((x + 1) - ox) / dx;
        else if (stepX < 0) tMaxX = (ox - x) / -dx;
        else                tMaxX = Float.POSITIVE_INFINITY;

        float tMaxY;
        if (stepY > 0)      tMaxY = ((y + 1) - oy) / dy;
        else if (stepY < 0) tMaxY = (oy - y) / -dy;
        else                tMaxY = Float.POSITIVE_INFINITY;

        float tMaxZ;
        if (stepZ > 0)      tMaxZ = ((z + 1) - oz) / dz;
        else if (stepZ < 0) tMaxZ = (oz - z) / -dz;
        else                tMaxZ = Float.POSITIVE_INFINITY;

        int lastX = x, lastY = y, lastZ = z;
        float t = 0f;

        for (int i = 0; i < 512 && t <= maxDist; i++) {
            int bx = x, by = y, bz = z;

           // if (world.isSolidBlock(bx, by, bz)) {
           if (Blocks.isHittable(world.getBlock(bx, by, bz))) {
                res.hit = true;
                res.hitX = bx; res.hitY = by; res.hitZ = bz;
                res.placeX = lastX; res.placeY = lastY; res.placeZ = lastZ;
                return res;
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    lastX = x; lastY = y; lastZ = z;
                    x += stepX;
                    t = tMaxX;
                    tMaxX += tDeltaX;
                } else {
                    lastX = x; lastY = y; lastZ = z;
                    z += stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    lastX = x; lastY = y; lastZ = z;
                    y += stepY;
                    t = tMaxY;
                    tMaxY += tDeltaY;
                } else {
                    lastX = x; lastY = y; lastZ = z;
                    z += stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return res;
    }
}

