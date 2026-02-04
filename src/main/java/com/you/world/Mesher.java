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

		float[] uv = tileUV(tileX, tileY, atlasW, atlasH, tileSizePx);
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

		float[] uv = tileUV(tileX, tileY, atlasW, atlasH, tileSizePx);
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

	private static float[] tileUV(int tileX, int tileY, int atlasW, int atlasH, int tileSizePx) {
		int tilesPerRow = atlasW / tileSizePx;
		int tilesPerCol = atlasH / tileSizePx;

		if (tileX < 0)
			tileX = 0;
		if (tileY < 0)
			tileY = 0;
		if (tileX >= tilesPerRow)
			tileX = tilesPerRow - 1;
		if (tileY >= tilesPerCol)
			tileY = tilesPerCol - 1;

		float uSize = (float) tileSizePx / (float) atlasW;
		float vSize = (float) tileSizePx / (float) atlasH;

		float u0 = tileX * uSize;
		float v0 = tileY * vSize;
		float u1 = u0 + uSize;
		float v1 = v0 + vSize;

		return new float[] { u0, v0, u1, v1 };
	}
}
