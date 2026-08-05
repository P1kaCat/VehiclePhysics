package fr.pikacat.vehiclephysics.vehicle;

import fr.pikacat.vehiclephysics.input.PlayerInput;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

public class VehicleController {

    private static final double GRAVITY = 0.08;

    public void update(PlayerInput input, Vehicle vehicle) {
        VehicleData data = vehicle.getData();
        VehicleTransform transform = vehicle.getTransform();
        double speed = transform.getSpeed();

        // No driver - decelerate
        if (input == null || vehicle.getDriver() == null) {
            speed = decelerate(speed, data.getAcceleration());
            transform.setSpeed(speed);
            applyGravity(transform);
            applyMovement(transform);
            return;
        }

        // 1. Read player input -> adjust speed
        // NOTE: mapping swapped to match standard Minecraft movement keys
        // (W = forward, S = backward, A = left, D = right)
        if (input.isBackward()) {
            speed = Math.min(speed + data.getAcceleration(), data.getMaxSpeed());
        } else if (input.isForward()) {
            speed = Math.max(speed - data.getAcceleration(), -data.getMaxSpeed() * 0.5);
        } else {
            speed = decelerate(speed, data.getAcceleration());
        }
        transform.setSpeed(speed);

        // 2. Apply steering - vehicle controls its own rotation (swapped to match A=left, D=right)
        if (input.isRight()) {
            transform.rotate((float) -data.getRotationSpeed());
        }
        if (input.isLeft()) {
            transform.rotate((float) data.getRotationSpeed());
        }

        // 3. Apply movement - direction comes from vehicle yaw, NOT player camera
        applyMovement(transform);

        // 4. Apply gravity
        applyGravity(transform);
    }

    private void applyMovement(VehicleTransform transform) {
        double speed = transform.getSpeed();
        if (Math.abs(speed) < 0.001) {
            return;
        }

        // Direction from vehicle transform yaw
        Vector forward = transform.getLocation().getDirection();
        transform.move(forward.multiply(speed));
    }

    private void applyGravity(VehicleTransform transform) {
        Location loc = transform.getLocation();
        double verticalVelocity = transform.getVerticalVelocity();

        // Check block directly below
        Block below = loc.getWorld().getBlockAt(
                loc.getBlockX(),
                loc.getBlockY() - 1,
                loc.getBlockZ()
        );

        if (!below.getType().isSolid()) {
            // In air - apply gravity
            verticalVelocity -= GRAVITY;
            transform.setVerticalVelocity(verticalVelocity);
            loc.add(0, verticalVelocity, 0);

            // Check if we landed after moving
            Block newBelow = loc.getWorld().getBlockAt(
                    loc.getBlockX(),
                    loc.getBlockY() - 1,
                    loc.getBlockZ()
            );
            if (newBelow.getType().isSolid()) {
                loc.setY(loc.getBlockY());
                transform.setVerticalVelocity(0);
            }
        } else {
            // On ground
            transform.setVerticalVelocity(0);
        }
    }

    private double decelerate(double speed, double acceleration) {
        if (speed > 0) {
            return Math.max(0, speed - acceleration);
        } else if (speed < 0) {
            return Math.min(0, speed + acceleration);
        }
        return 0;
    }
}
