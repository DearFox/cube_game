package com.you.world;

import java.util.ArrayDeque;
import java.util.Queue;

public class Lighting {

	private static final int[] DX = { 1, -1, 0, 0, 0, 0 };
	private static final int[] DY = { 0, 0, 1, -1, 0, 0 };
	private static final int[] DZ = { 0, 0, 0, 0, 1, -1 };

	private static final class Cell {
		final int x, y, z;
		final int level; // 0..15

		Cell(int x, int y, int z, int level) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.level = level;
		}
	}

	/**
	 * Full recompute (easy + correct). Call after generateTestWorld(), and after
	 * edits.
	 */
	public void rebuildAll(World world) {
		world.clearAllLighting();

		Bounds b = computeBounds(world);
		if (!b.valid)
			return;

		// 1) Seed skylight from the top of bounds
		Queue<Cell> skyQ = new ArrayDeque<>();
		seedSkyColumns(world, b, skyQ);

		// 2) Propagate skylight sideways/down (BFS)
		propagateSky(world, b, skyQ);

		// 3) Seed block lights from emitting blocks
		Queue<Cell> q = new ArrayDeque<>();
		seedBlockEmitters(world, b, q);

		// 4) Propagate block light (BFS)
		propagateBlock(world, q);
	}

	// ---------------- bounds ----------------

	private static final class Bounds {
		boolean valid = false;
		int minX, maxX, minY, maxY, minZ, maxZ;
	}

	private Bounds computeBounds(World world) {
		Bounds b = new Bounds();
		boolean first = true;

		for (Chunk c : world.getChunks()) {
			int baseX = c.pos.cx() * Chunk.CHUNK_SIZE;
			int baseY = c.pos.cy() * Chunk.CHUNK_SIZE;
			int baseZ = c.pos.cz() * Chunk.CHUNK_SIZE;

			int cMinX = baseX;
			int cMaxX = baseX + Chunk.CHUNK_SIZE - 1;
			int cMinY = baseY;
			int cMaxY = baseY + Chunk.CHUNK_SIZE - 1;
			int cMinZ = baseZ;
			int cMaxZ = baseZ + Chunk.CHUNK_SIZE - 1;

			if (first) {
				first = false;
				b.minX = cMinX;
				b.maxX = cMaxX;
				b.minY = cMinY;
				b.maxY = cMaxY;
				b.minZ = cMinZ;
				b.maxZ = cMaxZ;
				b.valid = true;
			} else {
				b.minX = Math.min(b.minX, cMinX);
				b.maxX = Math.max(b.maxX, cMaxX);
				b.minY = Math.min(b.minY, cMinY);
				b.maxY = Math.max(b.maxY, cMaxY);
				b.minZ = Math.min(b.minZ, cMinZ);
				b.maxZ = Math.max(b.maxZ, cMaxZ);
			}
		}

		// Give a little headroom so open columns get “sun”
		if (b.valid)
			b.maxY += 16;

		return b;
	}

	// ---------------- SKY ----------------

	private void seedSkyColumns(World world, Bounds b, Queue<Cell> q) {
		for (int x = b.minX; x <= b.maxX; x++) {
			for (int z = b.minZ; z <= b.maxZ; z++) {
				for (int y = b.maxY; y >= b.minY; y--) {
					short packed = world.getBlock(x, y, z);
					if (Blocks.opacity(packed) >= 15)
						break; // once solid, below can't be sky=15

					world.setSkyLight(x, y, z, 15);
					q.add(new Cell(x, y, z, 15));
				}
			}
		}
	}

	private void propagateSky(World world, Bounds b, Queue<Cell> q) {
		while (!q.isEmpty()) {
			Cell c = q.remove();
			int cur = world.getSkyLight(c.x, c.y, c.z);
			if (cur <= 0)
				continue;

			for (int i = 0; i < 6; i++) {
				int nx = c.x + DX[i], ny = c.y + DY[i], nz = c.z + DZ[i];
				if (ny < b.minY || ny > b.maxY || nx < b.minX || nx > b.maxX || nz < b.minZ || nz > b.maxZ)
					continue;

				short nid = world.getBlock(nx, ny, nz);
				if (Blocks.opacity(nid) >= 15)
					continue;

				int next = (DY[i] == -1 && cur == 15) ? 15 : (cur - 1);
				if (next <= 0)
					continue;

				if (next > world.getSkyLight(nx, ny, nz)) {
					world.setSkyLight(nx, ny, nz, next);
					q.add(new Cell(nx, ny, nz, next));
				}
			}
		}
	}

	// ---------------- BLOCK LIGHT ----------------

	private void seedBlockEmitters(World world, Bounds b, Queue<Cell> q) {
		for (int x = b.minX; x <= b.maxX; x++) {
			for (int y = b.minY; y <= b.maxY; y++) {
				for (int z = b.minZ; z <= b.maxZ; z++) {
					short packed = world.getBlock(x, y, z);
					int e = Blocks.emission(packed);
					if (e > 0) {
						world.setBlockLight(x, y, z, e);
						q.add(new Cell(x, y, z, e));
					}
				}
			}
		}
	}

	private void propagateBlock(World world, Queue<Cell> q) {
		while (!q.isEmpty()) {
			Cell c = q.remove();
			int cur = world.getBlockLight(c.x, c.y, c.z);
			if (cur <= 0)
				continue;

			for (int i = 0; i < 6; i++) {
				int nx = c.x + DX[i];
				int ny = c.y + DY[i];
				int nz = c.z + DZ[i];

				short nid = world.getBlock(nx, ny, nz);
				if (Blocks.opacity(nid) >= 15)
					continue;

				int next = cur - 1;
				if (next <= 0)
					continue;

				if (next > world.getBlockLight(nx, ny, nz)) {
					world.setBlockLight(nx, ny, nz, next);
					q.add(new Cell(nx, ny, nz, next));
				}
			}
		}
	}

	// Called when a block changes. oldPacked/newPacked are the packed short values.
	public void onBlockChanged(World world, int x, int y, int z, short oldPacked, short newPacked) {
		rebuildLocal(world, x, y, z, 18); // radius in blocks (try 24–48)
	}

	private boolean blocksLight(short packed) {
		return Blocks.opacity(packed) >= 15;
	}

	private static final class LightNode {
		final int x, y, z;
		final int level;

		LightNode(int x, int y, int z, int level) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.level = level;
		}
	}

	// Update block light around a single block change. oldPacked/newPacked are short.
	private void updateBlockLight(World world, int x, int y, int z, short oldPacked, short newPacked) {
		int oldEmit = Blocks.emission(oldPacked);
		int newEmit = Blocks.emission(newPacked);

		ArrayDeque<LightNode> removeQ = new ArrayDeque<>();
		ArrayDeque<LightNode> addQ = new ArrayDeque<>();

		// Start removal at the changed cell and its neighbors (cheap + safe)
		enqueueForRemoval(world, removeQ, x, y, z);
		for (int i = 0; i < 6; i++) {
			int nx = x + DX[i], ny = y + DY[i], nz = z + DZ[i];
			if (world.getBlockLight(nx, ny, nz) > 0)
				enqueueForRemoval(world, removeQ, nx, ny, nz);
		}

		// If the new block emits light, seed it
		if (newEmit > 0) {
			world.setBlockLight(x, y, z, newEmit);
			addQ.add(new LightNode(x, y, z, newEmit));
		}

		// Remove phase
		while (!removeQ.isEmpty()) {
			LightNode n = removeQ.remove();
			int cur = world.getBlockLight(n.x, n.y, n.z);
			if (cur == 0)
				continue;

			world.setBlockLight(n.x, n.y, n.z, 0);
			markChunksDirtyFromLightCell(world, n.x, n.y, n.z);

			for (int i = 0; i < 6; i++) {
				int nx = n.x + DX[i], ny = n.y + DY[i], nz = n.z + DZ[i];

				int nl = world.getBlockLight(nx, ny, nz);
				if (nl == 0)
					continue;

				// If neighbor is strictly dimmer, it probably depended on this node -> remove
				if (nl < cur) {
					removeQ.add(new LightNode(nx, ny, nz, nl));
				} else {
					// Neighbor may still be valid from elsewhere -> re-add it as a source
					addQ.add(new LightNode(nx, ny, nz, nl));
				}
			}
		}

		// Seed addQ from emitters at/nearby (including changed block if it emits)
		if (newEmit > 0) {
			addQ.add(new LightNode(x, y, z, newEmit));
		} else {
			for (int i = 0; i < 6; i++) {
				int nx = x + DX[i], ny = y + DY[i], nz = z + DZ[i];
				short bid = world.getBlock(nx, ny, nz);
				int e = Blocks.emission(bid);
				if (e > 0)
					addQ.add(new LightNode(nx, ny, nz, e));
			}
		}

		// Add phase: BFS spread
		while (!addQ.isEmpty()) {
			LightNode n = addQ.remove();

			// If this node is inside an opaque block, skip
			if (blocksLight(world.getBlock(n.x, n.y, n.z)))
				continue;

			int cur = world.getBlockLight(n.x, n.y, n.z);
			if (n.level <= cur)
				continue;

			world.setBlockLight(n.x, n.y, n.z, n.level);
			markChunksDirtyFromLightCell(world, n.x, n.y, n.z);

			if (n.level <= 1)
				continue;

			int next = n.level - 1;
			for (int i = 0; i < 6; i++) {
				int nx = n.x + DX[i], ny = n.y + DY[i], nz = n.z + DZ[i];
				if (blocksLight(world.getBlock(nx, ny, nz)))
					continue;

				if (next > world.getBlockLight(nx, ny, nz)) {
					addQ.add(new LightNode(nx, ny, nz, next));
				}
			}
		}
	}

	private void enqueueForRemoval(World world, ArrayDeque<LightNode> q, int x, int y, int z) {
		int l = world.getBlockLight(x, y, z);
		if (l > 0)
			q.add(new LightNode(x, y, z, l));
	}

	private void markChunksDirtyFromLightCell(World world, int x, int y, int z) {
		world.markDirtyAtBlock(x, y, z);

		// If it's near a chunk boundary, also dirty neighboring chunks (simple + safe)
		int lx = World.floorMod(x, Chunk.CHUNK_SIZE);
		int ly = World.floorMod(y, Chunk.CHUNK_SIZE);
		int lz = World.floorMod(z, Chunk.CHUNK_SIZE);

		if (lx == 0)
			world.markDirtyAtBlock(x - 1, y, z);
		if (lx == Chunk.CHUNK_SIZE - 1)
			world.markDirtyAtBlock(x + 1, y, z);
		if (ly == 0)
			world.markDirtyAtBlock(x, y - 1, z);
		if (ly == Chunk.CHUNK_SIZE - 1)
			world.markDirtyAtBlock(x, y + 1, z);
		if (lz == 0)
			world.markDirtyAtBlock(x, y, z - 1);
		if (lz == Chunk.CHUNK_SIZE - 1)
			world.markDirtyAtBlock(x, y, z + 1);
	}

	// Update sky light around a single block change. oldPacked/newPacked are short.
	private void updateSkyLight(World world, int x, int y, int z, short oldPacked, short newPacked) {
		boolean oldBlocks = Blocks.opacity(oldPacked) >= 15;
		boolean newBlocks = Blocks.opacity(newPacked) >= 15;

		if (oldBlocks == newBlocks)
			return;

		ArrayDeque<LightNode> removeQ = new ArrayDeque<>();
		ArrayDeque<LightNode> addQ = new ArrayDeque<>();

		// If we placed an opaque block: we may need to REMOVE skylight in affected region
		if (newBlocks) {
			// start removal at this cell and its neighbors
			enqueueSkyRemovalIfLit(world, removeQ, x, y, z);
			for (int i = 0; i < 6; i++) {
				enqueueSkyRemovalIfLit(world, removeQ, x + DX[i], y + DY[i], z + DZ[i]);
			}

			while (!removeQ.isEmpty()) {
				LightNode n = removeQ.remove();
				int cur = world.getSkyLight(n.x, n.y, n.z);
				if (cur == 0)
					continue;

				world.setSkyLight(n.x, n.y, n.z, 0);
				markChunksDirtyFromLightCell(world, n.x, n.y, n.z);

				for (int i = 0; i < 6; i++) {
					int nx = n.x + DX[i], ny = n.y + DY[i], nz = n.z + DZ[i];
					int nl = world.getSkyLight(nx, ny, nz);
					if (nl == 0)
						continue;

					// same dependency rule as block light:
					if (nl < cur) {
						removeQ.add(new LightNode(nx, ny, nz, nl));
					} else {
						addQ.add(new LightNode(nx, ny, nz, nl));
					}
				}
			}
		} else {
			// If we removed an opaque block (made air): we need to ADD skylight into the opening
			// Seed from neighbors that already have skylight
			for (int i = 0; i < 6; i++) {
				int nx = x + DX[i], ny = y + DY[i], nz = z + DZ[i];
				int nl = world.getSkyLight(nx, ny, nz);
				if (nl > 0)
					addQ.add(new LightNode(nx, ny, nz, nl));
			}

			// Also seed from "above" if that column is sky-lit.
			int above = world.getSkyLight(x, y + 1, z);
			if (above > 0)
				addQ.add(new LightNode(x, y + 1, z, above));
		}

		// ADD phase: propagate skylight
		while (!addQ.isEmpty()) {
			LightNode n = addQ.remove();

			// Cannot propagate into opaque blocks
			if (Blocks.opacity(world.getBlock(n.x, n.y, n.z)) >= 15)
				continue;

			int cur = world.getSkyLight(n.x, n.y, n.z);
			if (n.level <= cur)
				continue;

			world.setSkyLight(n.x, n.y, n.z, n.level);
			markChunksDirtyFromLightCell(world, n.x, n.y, n.z);

			if (n.level <= 1)
				continue;

			for (int i = 0; i < 6; i++) {
				int nx = n.x + DX[i], ny = n.y + DY[i], nz = n.z + DZ[i];
				if (Blocks.opacity(world.getBlock(nx, ny, nz)) >= 15)
					continue;

				int next;
				// Minecraft-ish: full sun going straight down stays full
				if (DY[i] == -1 && n.level == 15)
					next = 15;
				else
					next = n.level - 1;

				if (next > world.getSkyLight(nx, ny, nz)) {
					addQ.add(new LightNode(nx, ny, nz, next));
				}
			}
		}
	}

	private void enqueueSkyRemovalIfLit(World world, ArrayDeque<LightNode> q, int x, int y, int z) {
		int l = world.getSkyLight(x, y, z);
		if (l > 0)
			q.add(new LightNode(x, y, z, l));
	}

	// ---------------- region-local incremental recompute ----------------

	public void rebuildLocal(World world, int cx, int cy, int cz, int r) {
		int minX = cx - r, maxX = cx + r;
		int minY = cy - r, maxY = cy + r;
		int minZ = cz - r, maxZ = cz + r;
		maxY += 16;

		clearLightingRegion(world, minX, minY, minZ, maxX, maxY, maxZ);

		ArrayDeque<Cell> skyQ = new ArrayDeque<>();
		ArrayDeque<Cell> blockQ = new ArrayDeque<>();

		// 1) Seed skylight columns in region (top-down) AND enqueue into skyQ
		seedSkyColumnsRegion(world, minX, minY, minZ, maxX, maxY, maxZ, skyQ);

		// 2) Seed block emitters in region AND enqueue into blockQ
		seedBlockEmittersRegion(world, minX, minY, minZ, maxX, maxY, maxZ, blockQ);

		// 3) inject boundary light from *outside* the region
		seedFromOutsideBoundary(world, minX, minY, minZ, maxX, maxY, maxZ, skyQ, blockQ);

		// 4) Propagate (must use the queues you just filled)
		propagateSkyRegion(world, skyQ, minX, minY, minZ, maxX, maxY, maxZ);
		propagateBlockRegion(world, blockQ, minX, minY, minZ, maxX, maxY, maxZ);

		markDirtyChunksInRegion(world, minX, minY, minZ, maxX, maxY, maxZ);
	}

	private void clearLightingRegion(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					world.setSkyLight(x, y, z, 0);
					world.setBlockLight(x, y, z, 0);
				}
			}
		}
	}

	private void seedSkyColumnsRegion(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
			Queue<Cell> skyQ) {

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				int light = 15;

				for (int y = maxY; y >= minY; y--) {
					short packed = world.getBlock(x, y, z);

					if (Blocks.opacity(packed) >= 15) {
						light = 0;
					}

					int v = (light > 0 && Blocks.opacity(packed) < 15) ? 15 : 0;

					// Only write/enqueue if changed (big perf win)
					if (world.getSkyLight(x, y, z) != v) {
						world.setSkyLight(x, y, z, v);
					}

					if (v == 15) {
						skyQ.add(new Cell(x, y, z, 15));
					}
				}
			}
		}
	}

	private void propagateSkyRegion(World world, Queue<Cell> q, int minX, int minY, int minZ, int maxX, int maxY,
			int maxZ) {
		while (!q.isEmpty()) {
			Cell c = q.remove();
			// stale-node skip
			if (c.level != world.getSkyLight(c.x, c.y, c.z))
				continue;
			int cur = c.level;
			if (cur <= 0)
				continue;

			for (int i = 0; i < 6; i++) {
				int nx = c.x + DX[i], ny = c.y + DY[i], nz = c.z + DZ[i];
				if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ)
					continue;

				if (Blocks.opacity(world.getBlock(nx, ny, nz)) >= 15)
					continue;

				int next = (DY[i] == -1 && cur == 15) ? 15 : (cur - 1);
				if (next <= 0)
					continue;

				if (next > world.getSkyLight(nx, ny, nz)) {
					world.setSkyLight(nx, ny, nz, next);
					q.add(new Cell(nx, ny, nz, next));
				}
			}
		}
	}

	private void seedBlockEmittersRegion(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
			Queue<Cell> q) {

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					short packed = world.getBlock(x, y, z);
					int e = Blocks.emission(packed);
					if (e <= 0)
						continue;

					// Only write/enqueue if it actually increases
					int cur = world.getBlockLight(x, y, z);
					if (e > cur) {
						world.setBlockLight(x, y, z, e);
						q.add(new Cell(x, y, z, e));
					}
				}
			}
		}
	}

	private void propagateBlockRegion(World world, Queue<Cell> q, int minX, int minY, int minZ, int maxX, int maxY,
			int maxZ) {

		while (!q.isEmpty()) {
			Cell c = q.remove();

			// stale-node skip: if something stronger already set this cell, ignore this entry
			if (c.level != world.getBlockLight(c.x, c.y, c.z))
				continue;

			int cur = c.level;
			if (cur <= 1)
				continue;

			int next = cur - 1;

			for (int i = 0; i < 6; i++) {
				int nx = c.x + DX[i], ny = c.y + DY[i], nz = c.z + DZ[i];

				if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ)
					continue;

				if (Blocks.opacity(world.getBlock(nx, ny, nz)) >= 15)
					continue;

				int ncur = world.getBlockLight(nx, ny, nz);
				if (next > ncur) {
					world.setBlockLight(nx, ny, nz, next);
					q.add(new Cell(nx, ny, nz, next));
				}
			}
		}
	}

	private void markDirtyChunksInRegion(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int cminX = World.floorDiv(minX, Chunk.CHUNK_SIZE);
		int cmaxX = World.floorDiv(maxX, Chunk.CHUNK_SIZE);
		int cminY = World.floorDiv(minY, Chunk.CHUNK_SIZE);
		int cmaxY = World.floorDiv(maxY, Chunk.CHUNK_SIZE);
		int cminZ = World.floorDiv(minZ, Chunk.CHUNK_SIZE);
		int cmaxZ = World.floorDiv(maxZ, Chunk.CHUNK_SIZE);

		for (int cx = cminX; cx <= cmaxX; cx++) {
			for (int cy = cminY; cy <= cmaxY; cy++) {
				for (int cz = cminZ; cz <= cmaxZ; cz++) {
					Chunk c = world.getChunk(cx, cy, cz);
					if (c != null)
						c.dirty = true;
				}
			}
		}
	}

	private void seedFromOutsideBoundary(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
			ArrayDeque<Cell> skyQ, ArrayDeque<Cell> blockQ) {

		// helper: tries to seed (x,y,z) from outside cell (ox,oy,oz)
		java.util.function.BiConsumer<int[], int[]> trySeed = (in, out) -> {
			int x = in[0], y = in[1], z = in[2];
			int ox = out[0], oy = out[1], oz = out[2];

			// If the inside cell is opaque, it can't be lit
			if (Blocks.opacity(world.getBlock(x, y, z)) >= 15)
				return;

			int outsideSky = world.getSkyLight(ox, oy, oz);
			int outsideBlock = world.getBlockLight(ox, oy, oz);

			// Sky rule: if coming from above and outside is full sun, keep 15
			// Otherwise attenuate by 1 like normal propagation
			if (outsideSky > 0) {
				int nextSky = outsideSky - 1;
				if (oy > y && outsideSky == 15)
					nextSky = 15; // from above
				if (nextSky > world.getSkyLight(x, y, z)) {
					world.setSkyLight(x, y, z, nextSky);
					skyQ.add(new Cell(x, y, z, nextSky));
				}
			}

			if (outsideBlock > 0) {
				int nextBlock = outsideBlock - 1;
				if (nextBlock > world.getBlockLight(x, y, z)) {
					world.setBlockLight(x, y, z, nextBlock);
					blockQ.add(new Cell(x, y, z, nextBlock));
				}
			}
		};

		// Iterate boundary cells and seed from their outside neighbor for each face.
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				// minZ face, outside at z-1
				trySeed.accept(new int[] { x, y, minZ }, new int[] { x, y, minZ - 1 });
				// maxZ face, outside at z+1
				trySeed.accept(new int[] { x, y, maxZ }, new int[] { x, y, maxZ + 1 });
			}
		}
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				// minY face, outside at y-1
				trySeed.accept(new int[] { x, minY, z }, new int[] { x, minY - 1, z });
				// maxY face, outside at y+1
				trySeed.accept(new int[] { x, maxY, z }, new int[] { x, maxY + 1, z });
			}
		}
		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				// minX face, outside at x-1
				trySeed.accept(new int[] { minX, y, z }, new int[] { minX - 1, y, z });
				// maxX face, outside at x+1
				trySeed.accept(new int[] { maxX, y, z }, new int[] { maxX + 1, y, z });
			}
		}
	}
}
