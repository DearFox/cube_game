package com.you.world;

import java.util.ArrayList;

public class Mesher {

	private static final int[] FACE_X = { 1, -1, 0, 0, 0, 0 };
	private static final int[] FACE_Y = { 0, 0, 1, -1, 0, 0 };
	private static final int[] FACE_Z = { 0, 0, 0, 0, 1, -1 };

	public static final class MeshTriple {
		public final float[] opaque;
		public final float[] cutout;
		public final float[] transparent;

		public MeshTriple(float[] opaque, float[] cutout, float[] transparent) {
			this.opaque = opaque;
			this.cutout = cutout;
			this.transparent = transparent;
		}
	}

	private static final float[][] NORMALS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
			{ 0, 0, -1 } };

	public MeshTriple buildChunkMeshes(World world, Chunk c, int atlasW, int atlasH, int tileSizePx) {
		ArrayList<Float> opaque = new ArrayList<>();
		ArrayList<Float> cutout = new ArrayList<>();
		ArrayList<Float> transp = new ArrayList<>();

		int baseX = c.pos.cx() * Chunk.CHUNK_SIZE;
		int baseY = c.pos.cy() * Chunk.CHUNK_SIZE;
		int baseZ = c.pos.cz() * Chunk.CHUNK_SIZE;

		for (int ly = 0; ly < Chunk.CHUNK_SIZE; ly++) {
			for (int lz = 0; lz < Chunk.CHUNK_SIZE; lz++) {
				for (int lx = 0; lx < Chunk.CHUNK_SIZE; lx++) {

					short packed = c.get(lx, ly, lz);
					int kind = BlockData.kind(packed);
					if (kind == Blocks.AIR_KIND)
						continue;

					int wx = baseX + lx;
					int wy = baseY + ly;
					int wz = baseZ + lz;

					// --- CROSS-PLANT special mesh (two quads) ---
					// You provide this helper in Blocks (see notes below).
					if (Blocks.isPlant(packed)) {
						// Plants are alpha-cutout, not translucent blending.
						emitCrossPlant(world, cutout, wx, wy, wz, packed, atlasW, atlasH, tileSizePx);
						continue;
					}
					
					// --- TORCH special mesh (small cuboid) ---
					if (Blocks.isTorch(packed)) {
					    // Torches are alpha-cutout (like plants), not translucent blending.
					    emitTorch(world, cutout, wx, wy, wz, packed, atlasW, atlasH, tileSizePx);
					    continue;
					}

					// --- Normal cube block ---
					boolean inTranslucentPass = Blocks.isTranslucent(packed);

					// Put GLASS (and other alpha-cutout cubes) in cutout bucket, not opaque.
					boolean isCutout = Blocks.isCutout(packed);
					ArrayList<Float> out = inTranslucentPass ? transp : isCutout ? cutout : opaque;

					for (int f = 0; f < 6; f++) {
						int nx = wx + FACE_X[f];
						int ny = wy + FACE_Y[f];
						int nz = wz + FACE_Z[f];

						short nidPacked = world.getBlock(nx, ny, nz);

						if (!inTranslucentPass) {
							// SOLID/CUTOUT cube pass:
							// cull if neighbor is fully opaque (opaque neighbors hide faces)
							if (Blocks.isOpaque(nidPacked))
								continue;

							// prevent internal glass faces
							if (BlockData.kind(packed) == Blocks.GLASS_KIND
									&& BlockData.kind(nidPacked) == Blocks.GLASS_KIND) {
								continue;
							}
						} else {
							// TRANSLUCENT pass:
							if (Blocks.isOpaque(nidPacked))
								continue;
						}

						emitFace(world, out, wx, wy, wz, f, packed, atlasW, atlasH, tileSizePx);
					}
				}
			}
		}

		return new MeshTriple(toArray(opaque), toArray(cutout), toArray(transp));
	}

	private static float[] toArray(ArrayList<Float> list) {
		float[] arr = new float[list.size()];
		for (int i = 0; i < list.size(); i++)
			arr[i] = list.get(i);
		return arr;
	}

	// ---------- Cube face emitter (unchanged except signature types) ----------

	private void emitFace(World world, ArrayList<Float> out, int bx, int by, int bz, int face, short blockPacked,
			int atlasW, int atlasH, int tileSizePx) {

		float x0 = bx - 0.5f, x1 = bx + 0.5f;
		float y0 = by - 0.5f, y1 = by + 0.5f;
		float z0 = bz - 0.5f, z1 = bz + 0.5f;

		float nx = NORMALS[face][0];
		float ny = NORMALS[face][1];
		float nz = NORMALS[face][2];

		// sample light from the block just outside this face
		int sx = bx + (int) nx;
		int sy = by + (int) ny;
		int sz = bz + (int) nz;

		int L = world.getCombinedLight(sx, sy, sz); // 0..15
		float light01 = (L / 15.0f);
		float minLight = 0.20f;
		light01 = minLight + (1.0f - minLight) * light01;

		float[] p0, p1, p2, p3;
		switch (face) {
		case 0 -> {
			p0 = new float[] { x1, y0, z0 };
			p1 = new float[] { x1, y0, z1 };
			p2 = new float[] { x1, y1, z1 };
			p3 = new float[] { x1, y1, z0 };
		} // +X
		case 1 -> {
			p0 = new float[] { x0, y0, z1 };
			p1 = new float[] { x0, y0, z0 };
			p2 = new float[] { x0, y1, z0 };
			p3 = new float[] { x0, y1, z1 };
		} // -X
		case 2 -> {
			p0 = new float[] { x0, y1, z0 };
			p1 = new float[] { x1, y1, z0 };
			p2 = new float[] { x1, y1, z1 };
			p3 = new float[] { x0, y1, z1 };
		} // +Y
		case 3 -> {
			p0 = new float[] { x0, y0, z1 };
			p1 = new float[] { x1, y0, z1 };
			p2 = new float[] { x1, y0, z0 };
			p3 = new float[] { x0, y0, z0 };
		} // -Y
		case 4 -> {
			p0 = new float[] { x0, y0, z1 };
			p1 = new float[] { x0, y1, z1 };
			p2 = new float[] { x1, y1, z1 };
			p3 = new float[] { x1, y0, z1 };
		} // +Z
		case 5 -> {
			p0 = new float[] { x1, y0, z0 };
			p1 = new float[] { x1, y1, z0 };
			p2 = new float[] { x0, y1, z0 };
			p3 = new float[] { x0, y0, z0 };
		} // -Z
		default -> throw new IllegalArgumentException("face " + face);
		}

		BlockType bt = Blocks.get(blockPacked);
		if (bt == null)
			return;

		int tileX = bt.tileX(face);
		int tileY = bt.tileY(face);

		float[] uv = tileUV_Padded(tileX, tileY, atlasW, atlasH, tileSizePx, 8);
		float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

		float p0u = 0, p0v = 0, p1u = 0, p1v = 0, p2u = 0, p2v = 0, p3u = 0, p3v = 0;

		switch (face) {
		case 0 -> {
			p0u = u0;
			p0v = v0;
			p1u = u1;
			p1v = v0;
			p2u = u1;
			p2v = v1;
			p3u = u0;
			p3v = v1;
		}
		case 1 -> {
			p1u = u0;
			p1v = v0;
			p0u = u1;
			p0v = v0;
			p3u = u1;
			p3v = v1;
			p2u = u0;
			p2v = v1;
		}
		case 4 -> {
			p0u = u0;
			p0v = v0;
			p3u = u1;
			p3v = v0;
			p2u = u1;
			p2v = v1;
			p1u = u0;
			p1v = v1;
		}
		case 5 -> {
			p0u = u0;
			p0v = v0;
			p3u = u1;
			p3v = v0;
			p2u = u1;
			p2v = v1;
			p1u = u0;
			p1v = v1;
		}
		case 2 -> {
			p0u = u0;
			p0v = v1;
			p1u = u1;
			p1v = v1;
			p2u = u1;
			p2v = v0;
			p3u = u0;
			p3v = v0;
		}
		case 3 -> {
			p3u = u0;
			p3v = v1;
			p2u = u1;
			p2v = v1;
			p1u = u1;
			p1v = v0;
			p0u = u0;
			p0v = v0;
		}
		}

		push(out, p0, nx, ny, nz, p0u, p0v, light01);
		push(out, p2, nx, ny, nz, p2u, p2v, light01);
		push(out, p1, nx, ny, nz, p1u, p1v, light01);

		push(out, p0, nx, ny, nz, p0u, p0v, light01);
		push(out, p3, nx, ny, nz, p3u, p3v, light01);
		push(out, p2, nx, ny, nz, p2u, p2v, light01);
	}

	// ---------- Cross-plant emitter (NEW) ----------

	private void emitCrossPlant(World world, ArrayList<Float> out, int bx, int by, int bz, short blockPacked,
			int atlasW, int atlasH, int tileSizePx) {

		// Block bounds
		float x0 = bx - 0.5f, x1 = bx + 0.5f;
		float y0 = by - 0.5f, y1 = by + 0.5f;
		float z0 = bz - 0.5f, z1 = bz + 0.5f;

		// Light: sample from block center above-ish (simple; you can tweak)
		int L = world.getCombinedLight(bx, by, bz);
		float light01 = (L / 15.0f);
		float minLight = 0.20f;
		light01 = minLight + (1.0f - minLight) * light01;

		BlockType bt = Blocks.get(blockPacked);
		if (bt == null)
			return;

		// Use the top-face tile by convention (same as your hotbar icons)
		int tileX = bt.tileX(2);
		int tileY = bt.tileY(2);

		float[] uv = tileUV_Padded(tileX, tileY, atlasW, atlasH, tileSizePx, 8);
		float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

		// Quad A: diagonal from (x0,z0) to (x1,z1)
		float[] a0 = new float[] { x0, y0, z0 };
		float[] a1 = new float[] { x1, y0, z1 };
		float[] a2 = new float[] { x1, y1, z1 };
		float[] a3 = new float[] { x0, y1, z0 };

		// Quad B: diagonal from (x0,z1) to (x1,z0)
		float[] b0 = new float[] { x0, y0, z1 };
		float[] b1 = new float[] { x1, y0, z0 };
		float[] b2 = new float[] { x1, y1, z0 };
		float[] b3 = new float[] { x0, y1, z1 };

		// Normals for the planes (approx). Normalize (1,0,1) and (1,0,-1).
		float invLen = 0.70710677f; // 1/sqrt(2)

		// Plane A normal ~ (1,0,1)
		emitQuadTwoSidedSameNormal(out, a0,a1,a2,a3,  1f*invLen, 0.6f,  1f*invLen,  u0,v0,u1,v1, light01);

		// Plane B normal ~ (1,0,-1)
		emitQuadTwoSidedSameNormal(out, b0,b1,b2,b3,  1f*invLen, 0.6f, -1f*invLen,  u0,v0,u1,v1, light01);
	}

	// Emits a quad as two triangles, and then emits the reverse winding with
	// flipped normal.
	// This makes it render “double-sided” while GL_CULL_FACE is enabled.
	private void emitQuadDoubleSided(ArrayList<Float> out, float[] p0, float[] p1, float[] p2, float[] p3, float nx,
			float ny, float nz, float u0, float v0, float u1, float v1, float light01) {

		// UV mapping (consistent/simple): bottom-left -> top-right
		float p0u = u0, p0v = v0;
		float p1u = u1, p1v = v0;
		float p2u = u1, p2v = v1;
		float p3u = u0, p3v = v1;

		// Front (CCW) triangles: (p0,p2,p1) and (p0,p3,p2)
		push(out, p0, nx, ny, nz, p0u, p0v, light01);
		push(out, p2, nx, ny, nz, p2u, p2v, light01);
		push(out, p1, nx, ny, nz, p1u, p1v, light01);

		push(out, p0, nx, ny, nz, p0u, p0v, light01);
		push(out, p3, nx, ny, nz, p3u, p3v, light01);
		push(out, p2, nx, ny, nz, p2u, p2v, light01);

		// Back (reverse winding) with flipped normal
		float bnx = -nx, bny = -ny, bnz = -nz;

		push(out, p0, bnx, bny, bnz, p0u, p0v, light01);
		push(out, p1, bnx, bny, bnz, p1u, p1v, light01);
		push(out, p2, bnx, bny, bnz, p2u, p2v, light01);

		push(out, p0, bnx, bny, bnz, p0u, p0v, light01);
		push(out, p2, bnx, bny, bnz, p2u, p2v, light01);
		push(out, p3, bnx, bny, bnz, p3u, p3v, light01);
	}

	// Two-sided quad, but KEEP THE SAME NORMAL for both sides.
	// (Reverse winding only.) This avoids dark backs on plants.
	private void emitQuadTwoSidedSameNormal(ArrayList<Float> out, float[] p0, float[] p1, float[] p2, float[] p3,
			float nx, float ny, float nz, float u0, float v0, float u1, float v1, float light01) {

		float p0u = u0, p0v = v0;
		float p1u = u1, p1v = v0;
		float p2u = u1, p2v = v1;
		float p3u = u0, p3v = v1;

		// Front (CCW)
		push(out, p0, nx, ny, nz, p0u, p0v, light01);
		push(out, p2, nx, ny, nz, p2u, p2v, light01);
		push(out, p1, nx, ny, nz, p1u, p1v, light01);

		push(out, p0, nx, ny, nz, p0u, p0v, light01);
		push(out, p3, nx, ny, nz, p3u, p3v, light01);
		push(out, p2, nx, ny, nz, p2u, p2v, light01);

		// Back (reverse winding) BUT SAME NORMAL
		push(out, p0, nx, ny, nz, p0u, p0v, light01);
		push(out, p1, nx, ny, nz, p1u, p1v, light01);
		push(out, p2, nx, ny, nz, p2u, p2v, light01);

		push(out, p0, nx, ny, nz, p0u, p0v, light01);
		push(out, p2, nx, ny, nz, p2u, p2v, light01);
		push(out, p3, nx, ny, nz, p3u, p3v, light01);
	}

	private void emitQuad(ArrayList<Float> out, float[] p0, float[] p1, float[] p2, float[] p3, float nx, float ny,
			float nz, float u0, float v0, float u1, float v1, float light01) {

		// tri 1: p0, p2, p1
		push(out, p0, nx, ny, nz, u0, v0, light01);
		push(out, p2, nx, ny, nz, u1, v1, light01);
		push(out, p1, nx, ny, nz, u1, v0, light01);

		// tri 2: p0, p3, p2
		push(out, p0, nx, ny, nz, u0, v0, light01);
		push(out, p3, nx, ny, nz, u0, v1, light01);
		push(out, p2, nx, ny, nz, u1, v1, light01);
	}

	private void push(ArrayList<Float> out, float[] p, float nx, float ny, float nz, float u, float v, float light01) {
		out.add(p[0]);
		out.add(p[1]);
		out.add(p[2]);
		out.add(nx);
		out.add(ny);
		out.add(nz);
		out.add(u);
		out.add(v);
		out.add(light01);
	}

	// tileX,tileY are tile indices from the atlas map (not pixels)
	// tileSizePx = actual tile image size (e.g. 16)
	// padPx = padding per tile side (e.g. 2)
	private static float[] tileUV(int tileX, int tileY, int atlasW, int atlasH, int tileSizePx, int padPx) {
	    int cell = tileSizePx + padPx * 2;

	    int tilesPerRow = atlasW / cell;
	    int tilesPerCol = atlasH / cell;

	    if (tileX < 0) tileX = 0;
	    if (tileY < 0) tileY = 0;
	    if (tileX >= tilesPerRow) tileX = tilesPerRow - 1;
	    if (tileY >= tilesPerCol) tileY = tilesPerCol - 1;

	    float uCell = (float) cell / (float) atlasW;
	    float vCell = (float) cell / (float) atlasH;

	    // Start of this cell
	    float u0 = tileX * uCell;
	    float v0 = tileY * vCell;

	    // Offset into cell by padding
	    float duPad = (float) padPx / (float) atlasW;
	    float dvPad = (float) padPx / (float) atlasH;

	    float uTile0 = u0 + duPad;
	    float vTile0 = v0 + dvPad;

	    float duTile = (float) tileSizePx / (float) atlasW;
	    float dvTile = (float) tileSizePx / (float) atlasH;

	    float uTile1 = uTile0 + duTile;
	    float vTile1 = vTile0 + dvTile;

	    // Optional: half-texel inset inside the tile region for extra safety
	    /*float duInset = 0.5f / atlasW;
	    float dvInset = 0.5f / atlasH;

	    uTile0 += duInset; vTile0 += dvInset;
	    uTile1 -= duInset; vTile1 -= dvInset;*/

	    return new float[]{ uTile0, vTile0, uTile1, vTile1 };
	}
	
	private static float[] tileUV_Padded(
	        int tileX, int tileY,
	        int atlasW, int atlasH,
	        int tileSizePx, int padPx
	) {
	    int stride = tileSizePx + padPx * 2;

	    // pixel rect of the *inner* tile content (excluding padding)
	    int px0 = tileX * stride + padPx;
	    int py0 = tileY * stride + padPx;
	    int px1 = px0 + tileSizePx;
	    int py1 = py0 + tileSizePx;

	    // Half-texel inset to avoid sampling exactly on borders
	    float epsU = 0;
	    float epsV = 0;

	    float u0 = (px0 / (float) atlasW) + epsU;
	    float v0 = (py0 / (float) atlasH) + epsV;
	    float u1 = (px1 / (float) atlasW) - epsU;
	    float v1 = (py1 / (float) atlasH) - epsV;

	    return new float[]{ u0, v0, u1, v1 };
	}
	
	private void emitTorch(World world, ArrayList<Float> out, int bx, int by, int bz, short blockPacked,
	        int atlasW, int atlasH, int tileSizePx) {

	    int state = BlockData.state(blockPacked) & 0xF;

	    // Light (same as before)
	    int L = world.getCombinedLight(bx, by, bz);
	    float light01 = (L / 15.0f);
	    float minLight = 0.20f;
	    light01 = minLight + (1.0f - minLight) * light01;

	    BlockType bt = Blocks.get(blockPacked);
	    if (bt == null) return;

	    // Use top-face tile by convention
	    int tileX = bt.tileX(2);
	    int tileY = bt.tileY(2);

	    // Full tile UV (inner padded)
	    float[] uv = tileUV_Padded(tileX, tileY, atlasW, atlasH, tileSizePx, 8);
	    float U0 = uv[0], V0 = uv[1], U1 = uv[2], V1 = uv[3];

	    // --- USE YOUR EXISTING "correct" torch sub-UV computation here ---
	    // You said it's correct now, so keep it.
	    // These must represent the right 2x10 strip for the sides, and small caps for top/bottom:
	    float sideU0, sideV0, sideU1, sideV1;
	    float topU0,  topV0,  topU1,  topV1;
	    float botU0,  botV0,  botU1,  botV1;

	    {
	        float du = (U1 - U0) / 16.0f;
	        float dv = (V1 - V0) / 16.0f;

	        // IMPORTANT: keep the version that made your torch correct (including any V flipping you applied).
	        // Example (edit if your "correct" version differs):
	        sideU0 = U0 + 7 * du;
	        sideU1 = U0 + 9 * du;

	        // If your pipeline required flipped V, you'd have had something like:
	        // sideV0 = V1 - 16 * dv; sideV1 = V1 - 6 * dv;
	        // Otherwise:
	        sideV0 = V0 + 0 * dv;
	        sideV1 = V0 + 10 * dv;

	        // Caps (2px wide, 2px tall — choose rows that look decent)
	        topU0 = sideU0; topU1 = sideU1;
	        botU0 = sideU0; botU1 = sideU1;

	        topV0 = V0 + 8 * dv;
	        topV1 = V0 + 10 * dv;

	        botV0 = V0 + 0 * dv;
	        botV1 = V0 + 2 * dv;
	    }

	    float halfW = 1f / 16f;     // thickness (2/16 total)
	    float h     = 10f / 16f;

	    float baseY = by - 0.5f;

	    if (state == Blocks.TORCH_FLOOR) {
	        // Upright centered torch (your previous working geometry)
	        float cx = bx;
	        float cz = bz;

	        float x0 = cx - halfW, x1 = cx + halfW;
	        float z0 = cz - halfW, z1 = cz + halfW;
	        float y0 = baseY;
	        float y1 = y0 + h;

	        for (int face = 0; face < 6; face++) {
	            float fu0, fv0, fu1, fv1;
	            if (face == 2) { fu0 = topU0; fv0 = topV0; fu1 = topU1; fv1 = topV1; }
	            else if (face == 3) { fu0 = botU0; fv0 = botV0; fu1 = botU1; fv1 = botV1; }
	            else { fu0 = sideU0; fv0 = sideV0; fu1 = sideU1; fv1 = sideV1; }

	            emitFaceBounds(out, x0, x1, y0, y1, z0, z1, face, fu0, fv0, fu1, fv1, light01);
	        }
	        return;
	    }

	 // --- Wall torch: rigid rotation (no shear) ---
	    float dirX = 0f, dirZ = 0f;
	    switch (state) {
	        case Blocks.TORCH_WEST  -> dirX = +1f;
	        case Blocks.TORCH_EAST  -> dirX = -1f;
	        case Blocks.TORCH_NORTH -> dirZ = +1f;
	        case Blocks.TORCH_SOUTH -> dirZ = -1f;
	        default -> {}
	    }

	    // Tuning knobs
	    float lift = 3f / 16f;          // raise off ground
	    float wallInset = 8f / 16f;     // how close to the wall (pivot point offset)
	    float tiltDeg = 22.5f;          // minecraft-ish tilt

	    baseY = by - 0.5f;

	    // Pivot (where torch is "attached"); near the wall, lifted up
	    float px = bx - dirX * wallInset;
	    float py = baseY + lift;
	    float pz = bz - dirZ * wallInset;

	    // Build an upright torch prism in local space around pivot
	    halfW = 1f / 16f;         // 2/16 total width
	    h     = 10f / 16f;        // height

	    // bottom and top centers (upright, before rotation)
	    float c0x = px, c0y = py,     c0z = pz;
	    float c1x = px, c1y = py + h, c1z = pz;

	    // Build 8 corners for upright prism
	    float[] b00 = new float[]{ c0x - halfW, c0y, c0z - halfW };
	    float[] b10 = new float[]{ c0x + halfW, c0y, c0z - halfW };
	    float[] b11 = new float[]{ c0x + halfW, c0y, c0z + halfW };
	    float[] b01 = new float[]{ c0x - halfW, c0y, c0z + halfW };

	    float[] t00 = new float[]{ c1x - halfW, c1y, c1z - halfW };
	    float[] t10 = new float[]{ c1x + halfW, c1y, c1z - halfW };
	    float[] t11 = new float[]{ c1x + halfW, c1y, c1z + halfW };
	    float[] t01 = new float[]{ c1x - halfW, c1y, c1z + halfW };

	    // Rotate EVERYTHING around pivot so the torch leans away from the wall
	    float[] axis = new float[]{ dirZ, 0f, -dirX }; // axis = cross(outward, up)
	    normalize3(axis);

	    float tiltRad = (float) Math.toRadians(tiltDeg);

	    // rotate around pivot point (px,py,pz)
	    rotateAroundAxis(b00, px, py, pz, axis[0], axis[1], axis[2], tiltRad);
	    rotateAroundAxis(b10, px, py, pz, axis[0], axis[1], axis[2], tiltRad);
	    rotateAroundAxis(b11, px, py, pz, axis[0], axis[1], axis[2], tiltRad);
	    rotateAroundAxis(b01, px, py, pz, axis[0], axis[1], axis[2], tiltRad);

	    rotateAroundAxis(t00, px, py, pz, axis[0], axis[1], axis[2], tiltRad);
	    rotateAroundAxis(t10, px, py, pz, axis[0], axis[1], axis[2], tiltRad);
	    rotateAroundAxis(t11, px, py, pz, axis[0], axis[1], axis[2], tiltRad);
	    rotateAroundAxis(t01, px, py, pz, axis[0], axis[1], axis[2], tiltRad);

	    // Emit prism from rotated corners (caps are now tilted too)
	    emitPrismFromCorners(out,
	            b00,b10,b11,b01,
	            t00,t10,t11,t01,
	            sideU0, sideV0, sideU1, sideV1,
	            topU0,  topV0,  topU1,  topV1,
	            botU0,  botV0,  botU1,  botV1,
	            light01);
	}

	
	private void emitFaceBounds(ArrayList<Float> out,
	        float x0, float x1, float y0, float y1, float z0, float z1,
	        int face,
	        float u0, float v0, float u1, float v1,
	        float light01) {

	    float nx = NORMALS[face][0];
	    float ny = NORMALS[face][1];
	    float nz = NORMALS[face][2];

	    float[] p0, p1, p2, p3;
	    switch (face) {
	    case 0 -> { // +X
	        p0 = new float[] { x1, y0, z0 };
	        p1 = new float[] { x1, y0, z1 };
	        p2 = new float[] { x1, y1, z1 };
	        p3 = new float[] { x1, y1, z0 };
	    }
	    case 1 -> { // -X
	        p0 = new float[] { x0, y0, z1 };
	        p1 = new float[] { x0, y0, z0 };
	        p2 = new float[] { x0, y1, z0 };
	        p3 = new float[] { x0, y1, z1 };
	    }
	    case 2 -> { // +Y
	        p0 = new float[] { x0, y1, z0 };
	        p1 = new float[] { x1, y1, z0 };
	        p2 = new float[] { x1, y1, z1 };
	        p3 = new float[] { x0, y1, z1 };
	    }
	    case 3 -> { // -Y
	        p0 = new float[] { x0, y0, z1 };
	        p1 = new float[] { x1, y0, z1 };
	        p2 = new float[] { x1, y0, z0 };
	        p3 = new float[] { x0, y0, z0 };
	    }
	    case 4 -> { // +Z
	        p0 = new float[] { x0, y0, z1 };
	        p1 = new float[] { x0, y1, z1 };
	        p2 = new float[] { x1, y1, z1 };
	        p3 = new float[] { x1, y0, z1 };
	    }
	    case 5 -> { // -Z
	        p0 = new float[] { x1, y0, z0 };
	        p1 = new float[] { x1, y1, z0 };
	        p2 = new float[] { x0, y1, z0 };
	        p3 = new float[] { x0, y0, z0 };
	    }
	    default -> throw new IllegalArgumentException("face " + face);
	    }

	    // Same UV orientation rules as your cube emitter
	    float p0u=0,p0v=0,p1u=0,p1v=0,p2u=0,p2v=0,p3u=0,p3v=0;

	    switch (face) {
	    case 0 -> { p0u=u0; p0v=v0; p1u=u1; p1v=v0; p2u=u1; p2v=v1; p3u=u0; p3v=v1; }
	    case 1 -> { p1u=u0; p1v=v0; p0u=u1; p0v=v0; p3u=u1; p3v=v1; p2u=u0; p2v=v1; }
	    case 4 -> { p0u=u0; p0v=v0; p3u=u1; p3v=v0; p2u=u1; p2v=v1; p1u=u0; p1v=v1; }
	    case 5 -> { p0u=u0; p0v=v0; p3u=u1; p3v=v0; p2u=u1; p2v=v1; p1u=u0; p1v=v1; }
	    case 2 -> { p0u=u0; p0v=v1; p1u=u1; p1v=v1; p2u=u1; p2v=v0; p3u=u0; p3v=v0; }
	    case 3 -> { p3u=u0; p3v=v1; p2u=u1; p2v=v1; p1u=u1; p1v=v0; p0u=u0; p0v=v0; }
	    }

	    // same triangle order as emitFace()
	    push(out, p0, nx, ny, nz, p0u, p0v, light01);
	    push(out, p2, nx, ny, nz, p2u, p2v, light01);
	    push(out, p1, nx, ny, nz, p1u, p1v, light01);

	    push(out, p0, nx, ny, nz, p0u, p0v, light01);
	    push(out, p3, nx, ny, nz, p3u, p3v, light01);
	    push(out, p2, nx, ny, nz, p2u, p2v, light01);
	}
	
	private void emitSideQuadAuto(ArrayList<Float> out,
	        float[] b0, float[] b1, float[] t1, float[] t0,   // bottom edge then top edge
	        float u0, float v0, float u1, float v1,
	        float light01,
	        float centerX, float centerY, float centerZ) {

	    // Corner convention for UVs:
	    // p0=b0 (bottom-left), p1=b1 (bottom-right), p2=t1 (top-right), p3=t0 (top-left)
	    float[] p0 = b0, p1 = b1, p2 = t1, p3 = t0;

	    // Compute normal for the SAME winding your cube emitter uses: (p0,p2,p1)
	    float ax = p2[0] - p0[0], ay = p2[1] - p0[1], az = p2[2] - p0[2];
	    float bx = p1[0] - p0[0], by = p1[1] - p0[1], bz = p1[2] - p0[2];

	    float nx = ay * bz - az * by;
	    float ny = az * bx - ax * bz;
	    float nz = ax * by - ay * bx;

	    float nLen = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
	    if (nLen > 1e-8f) { nx /= nLen; ny /= nLen; nz /= nLen; }
	    else { nx = 0; ny = 1; nz = 0; }

	    // Outward direction = from prism center to quad center
	    float mx = 0.25f * (p0[0] + p1[0] + p2[0] + p3[0]);
	    float my = 0.25f * (p0[1] + p1[1] + p2[1] + p3[1]);
	    float mz = 0.25f * (p0[2] + p1[2] + p2[2] + p3[2]);

	    float ox = mx - centerX;
	    float oy = my - centerY;
	    float oz = mz - centerZ;

	    float oLen = (float)Math.sqrt(ox*ox + oy*oy + oz*oz);
	    if (oLen > 1e-8f) { ox /= oLen; oy /= oLen; oz /= oLen; }

	    // If normal points inward, flip winding by flipping TRIANGLE INDICES (not vertices)
	    boolean flip = (nx*ox + ny*oy + nz*oz) < 0.0f;

	    // UVs tied to corners (won't rotate if we flip indices)
	    float p0u = u0, p0v = v0;
	    float p1u = u1, p1v = v0;
	    float p2u = u1, p2v = v1;
	    float p3u = u0, p3v = v1;

	    if (!flip) {
	        // Cube-style winding
	        push(out, p0, nx, ny, nz, p0u, p0v, light01);
	        push(out, p2, nx, ny, nz, p2u, p2v, light01);
	        push(out, p1, nx, ny, nz, p1u, p1v, light01);

	        push(out, p0, nx, ny, nz, p0u, p0v, light01);
	        push(out, p3, nx, ny, nz, p3u, p3v, light01);
	        push(out, p2, nx, ny, nz, p2u, p2v, light01);
	    } else {
	        // Flipped winding (swap indices only) — UV stays correct
	        push(out, p0, -nx, -ny, -nz, p0u, p0v, light01);
	        push(out, p1, -nx, -ny, -nz, p1u, p1v, light01);
	        push(out, p2, -nx, -ny, -nz, p2u, p2v, light01);

	        push(out, p0, -nx, -ny, -nz, p0u, p0v, light01);
	        push(out, p2, -nx, -ny, -nz, p2u, p2v, light01);
	        push(out, p3, -nx, -ny, -nz, p3u, p3v, light01);
	    }
	}
	
	private static void rotateAroundAxis(float[] p,
	        float ox, float oy, float oz,
	        float ax, float ay, float az,
	        float angleRad) {

	    // translate to origin
	    float x = p[0] - ox;
	    float y = p[1] - oy;
	    float z = p[2] - oz;

	    float c = (float)Math.cos(angleRad);
	    float s = (float)Math.sin(angleRad);

	    // Rodrigues' rotation formula
	    float dot = ax*x + ay*y + az*z;

	    float rx = x*c + (ay*z - az*y)*s + ax*dot*(1f - c);
	    float ry = y*c + (az*x - ax*z)*s + ay*dot*(1f - c);
	    float rz = z*c + (ax*y - ay*x)*s + az*dot*(1f - c);

	    // translate back
	    p[0] = rx + ox;
	    p[1] = ry + oy;
	    p[2] = rz + oz;
	}

	private static void normalize3(float[] v) {
	    float len = (float)Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
	    if (len > 1e-8f) {
	        v[0] /= len; v[1] /= len; v[2] /= len;
	    }
	}

	private void emitPrismFromCorners(ArrayList<Float> out,
	        float[] b00, float[] b10, float[] b11, float[] b01,
	        float[] t00, float[] t10, float[] t11, float[] t01,
	        float sideU0, float sideV0, float sideU1, float sideV1,
	        float topU0,  float topV0,  float topU1,  float topV1,
	        float botU0,  float botV0,  float botU1,  float botV1,
	        float light01) {

	    // center for outward check (average of all 8 corners)
	    float cx = (b00[0]+b10[0]+b11[0]+b01[0]+t00[0]+t10[0]+t11[0]+t01[0]) / 8f;
	    float cy = (b00[1]+b10[1]+b11[1]+b01[1]+t00[1]+t10[1]+t11[1]+t01[1]) / 8f;
	    float cz = (b00[2]+b10[2]+b11[2]+b01[2]+t00[2]+t10[2]+t11[2]+t01[2]) / 8f;

	    // sides (bottom edge then matching top edge)
	    emitSideQuadAuto(out, b10, b00, t00, t10, sideU0, sideV0, sideU1, sideV1, light01, cx, cy, cz);
	    emitSideQuadAuto(out, b00, b01, t01, t00, sideU0, sideV0, sideU1, sideV1, light01, cx, cy, cz);
	    emitSideQuadAuto(out, b01, b11, t11, t01, sideU0, sideV0, sideU1, sideV1, light01, cx, cy, cz);
	    emitSideQuadAuto(out, b11, b10, t10, t11, sideU0, sideV0, sideU1, sideV1, light01, cx, cy, cz);

	    // caps — use the same auto-winding logic so culling stays correct.
	    emitQuadAuto(out, t00, t01, t11, t10, topU0, topV0, topU1, topV1, light01, cx, cy, cz);
	    emitQuadAuto(out, b00, b10, b11, b01, botU0, botV0, botU1, botV1, light01, cx, cy, cz);
	}

	private void emitQuadAuto(ArrayList<Float> out,
	        float[] p0, float[] p1, float[] p2, float[] p3,
	        float u0, float v0, float u1, float v1,
	        float light01,
	        float centerX, float centerY, float centerZ) {

	    // Compute normal matching your cube winding: (p0, p2, p1)
	    float ax = p2[0] - p0[0], ay = p2[1] - p0[1], az = p2[2] - p0[2];
	    float bx = p1[0] - p0[0], by = p1[1] - p0[1], bz = p1[2] - p0[2];

	    float nx = ay * bz - az * by;
	    float ny = az * bx - ax * bz;
	    float nz = ax * by - ay * bx;

	    float nLen = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
	    if (nLen > 1e-8f) { nx /= nLen; ny /= nLen; nz /= nLen; }
	    else { nx = 0; ny = 1; nz = 0; }

	    // Quad center and outward test vector (from prism center to quad center)
	    float mx = 0.25f * (p0[0] + p1[0] + p2[0] + p3[0]);
	    float my = 0.25f * (p0[1] + p1[1] + p2[1] + p3[1]);
	    float mz = 0.25f * (p0[2] + p1[2] + p2[2] + p3[2]);

	    float ox = mx - centerX;
	    float oy = my - centerY;
	    float oz = mz - centerZ;

	    float oLen = (float) Math.sqrt(ox*ox + oy*oy + oz*oz);
	    if (oLen > 1e-8f) { ox /= oLen; oy /= oLen; oz /= oLen; }

	    boolean flip = (nx*ox + ny*oy + nz*oz) < 0.0f;

	    // UVs bound to corners (p0 bottom-left, p1 bottom-right, p2 top-right, p3 top-left)
	    float p0u = u0, p0v = v0;
	    float p1u = u1, p1v = v0;
	    float p2u = u1, p2v = v1;
	    float p3u = u0, p3v = v1;

	    if (!flip) {
	        // canonical winding (matches your cube emitter)
	        push(out, p0, nx, ny, nz, p0u, p0v, light01);
	        push(out, p2, nx, ny, nz, p2u, p2v, light01);
	        push(out, p1, nx, ny, nz, p1u, p1v, light01);

	        push(out, p0, nx, ny, nz, p0u, p0v, light01);
	        push(out, p3, nx, ny, nz, p3u, p3v, light01);
	        push(out, p2, nx, ny, nz, p2u, p2v, light01);
	    } else {
	        // flipped indices (negate normal so lighting stays outward)
	        push(out, p0, -nx, -ny, -nz, p0u, p0v, light01);
	        push(out, p1, -nx, -ny, -nz, p1u, p1v, light01);
	        push(out, p2, -nx, -ny, -nz, p2u, p2v, light01);

	        push(out, p0, -nx, -ny, -nz, p0u, p0v, light01);
	        push(out, p2, -nx, -ny, -nz, p2u, p2v, light01);
	        push(out, p3, -nx, -ny, -nz, p3u, p3v, light01);
	    }
	}




}
