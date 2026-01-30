package com.you.physics;

import com.you.engine.Camera;
import com.you.engine.Input;
import com.you.player.Player;
import com.you.world.World;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Physics {

	private static final float SKIN = 0.001f;

	public void update(Player player, Camera camera, World world, Input input, float dt) {
		// movement in camera plane
		Vector3f camF = camera.getForward();
		Vector3f forward = new Vector3f(camF.x, 0f, camF.z);
		if (forward.lengthSquared() > 0f)
			forward.normalize();
		Vector3f right = new Vector3f(forward).cross(0f, 1f, 0f).normalize();

		Vector3f wishDir = new Vector3f();
		if (input.keyDown(GLFW_KEY_W))
			wishDir.add(forward);
		if (input.keyDown(GLFW_KEY_S))
			wishDir.sub(forward);
		if (input.keyDown(GLFW_KEY_D))
			wishDir.add(right);
		if (input.keyDown(GLFW_KEY_A))
			wishDir.sub(right);
		if (wishDir.lengthSquared() > 0f)
			wishDir.normalize();

		Vector3f vel = player.getVel();
		Vector3f pos = player.getPos();

		Vector3f horizVel = new Vector3f(vel.x, 0f, vel.z);
		Vector3f target = new Vector3f(wishDir).mul(player.maxSpeed);
		Vector3f delta = target.sub(horizVel);

		float maxDelta = player.accel * dt;
		if (delta.length() > maxDelta)
			delta.normalize(maxDelta);

		vel.x += delta.x;
		vel.z += delta.z;

		// friction
		if (player.isOnGround() && wishDir.lengthSquared() == 0f) {
			float speed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
			float drop = player.friction * dt;
			float newSpeed = Math.max(0f, speed - drop);
			if (speed > 1e-6f) {
				float k = newSpeed / speed;
				vel.x *= k;
				vel.z *= k;
			}
		}

		// jump
		if (player.isOnGround() && input.keyDown(GLFW_KEY_SPACE)) {
			vel.y = player.jumpSpeed;
			player.setOnGround(false);
		}

		// gravity
		vel.y -= player.gravity * dt;

		player.setOnGround(false);

		// X
		resolveAxis(world, pos, vel, player.getHalf(), vel.x * dt, 0f, 0f);

		// Z
		resolveAxis(world, pos, vel, player.getHalf(), 0f, 0f, vel.z * dt);

		// Y (do last so landing is stable)
		float oldY = vel.y;
		resolveAxis(world, pos, vel, player.getHalf(), 0f, vel.y * dt, 0f);
		if (oldY < 0f && vel.y == 0f)
			player.setOnGround(true);

		// camera follows player (eye offset)
		camera.setPosition(new Vector3f(pos).add(0f, 0.6f, 0f));
	}

	public boolean aabbIntersectsBlock(Vector3f center, Vector3f half, int bx, int by, int bz) {
		float minX = center.x - half.x, maxX = center.x + half.x;
		float minY = center.y - half.y, maxY = center.y + half.y;
		float minZ = center.z - half.z, maxZ = center.z + half.z;

		float bMinX = bx - 0.5f, bMaxX = bx + 0.5f;
		float bMinY = by - 0.5f, bMaxY = by + 0.5f;
		float bMinZ = bz - 0.5f, bMaxZ = bz + 0.5f;

		return (maxX > bMinX && minX < bMaxX) && (maxY > bMinY && minY < bMaxY) && (maxZ > bMinZ && minZ < bMaxZ);
	}

	private void resolveAxis(World world, Vector3f pos, Vector3f vel, Vector3f half, float dx, float dy, float dz) {
		pos.add(dx, dy, dz);

		int minBX = (int) Math.floor((pos.x - half.x) - 0.5f);
		int maxBX = (int) Math.floor((pos.x + half.x) + 0.5f);
		int minBY = (int) Math.floor((pos.y - half.y) - 0.5f);
		int maxBY = (int) Math.floor((pos.y + half.y) + 0.5f);
		int minBZ = (int) Math.floor((pos.z - half.z) - 0.5f);
		int maxBZ = (int) Math.floor((pos.z + half.z) + 0.5f);

// Resolve ONLY along the axis we moved on this call.
		if (dx != 0f) {
			float bestX = pos.x;
			boolean hit = false;

			for (int bx = minBX; bx <= maxBX; bx++) {
				for (int by = minBY; by <= maxBY; by++) {
					for (int bz = minBZ; bz <= maxBZ; bz++) {
						if (!world.isSolidBlock(bx, by, bz))
							continue;
						if (!aabbIntersectsBlock(pos, half, bx, by, bz))
							continue;

						float bMinX = bx - 0.5f;
						float bMaxX = bx + 0.5f;

						if (dx > 0f)
							bestX = Math.min(bestX, bMinX - half.x - SKIN);
						else
							bestX = Math.max(bestX, bMaxX + half.x + SKIN);

						hit = true;
					}
				}
			}

			if (hit) {
				pos.x = bestX;
				vel.x = 0f;
			}
			return;
		}

		if (dy != 0f) {
			float bestY = pos.y;
			boolean hit = false;

			for (int bx = minBX; bx <= maxBX; bx++) {
				for (int by = minBY; by <= maxBY; by++) {
					for (int bz = minBZ; bz <= maxBZ; bz++) {
						if (!world.isSolidBlock(bx, by, bz))
							continue;
						if (!aabbIntersectsBlock(pos, half, bx, by, bz))
							continue;

						float bMinY = by - 0.5f;
						float bMaxY = by + 0.5f;

						if (dy > 0f)
							bestY = Math.min(bestY, bMinY - half.y - SKIN); // ceiling
						else
							bestY = Math.max(bestY, bMaxY + half.y + SKIN); // floor

						hit = true;
					}
				}
			}

			if (hit) {
				pos.y = bestY;
				vel.y = 0f;
			}
			return;
		}

		if (dz != 0f) {
			float bestZ = pos.z;
			boolean hit = false;

			for (int bx = minBX; bx <= maxBX; bx++) {
				for (int by = minBY; by <= maxBY; by++) {
					for (int bz = minBZ; bz <= maxBZ; bz++) {
						if (!world.isSolidBlock(bx, by, bz))
							continue;
						if (!aabbIntersectsBlock(pos, half, bx, by, bz))
							continue;

						float bMinZ = bz - 0.5f;
						float bMaxZ = bz + 0.5f;

						if (dz > 0f)
							bestZ = Math.min(bestZ, bMinZ - half.z - SKIN);
						else
							bestZ = Math.max(bestZ, bMaxZ + half.z + SKIN);

						hit = true;
					}
				}
			}

			if (hit) {
				pos.z = bestZ;
				vel.z = 0f;
			}
		}
	}

}
