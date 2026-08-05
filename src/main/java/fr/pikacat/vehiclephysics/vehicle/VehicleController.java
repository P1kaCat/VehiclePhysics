package fr.pikacat.vehiclephysics.vehicle;

import fr.pikacat.vehiclephysics.input.PlayerInput;

import org.bukkit.util.Vector;

public class VehicleController {

    public void update(PlayerInput input, Vehicle vehicle) {
        VehicleData data = vehicle.getData();
        double speed = vehicle.getSpeed();

        // No driver - decelerate gradually
        if (input == null || vehicle.getDriverId() == null) {
            speed = decelerate(speed, data.getAcceleration());
            vehicle.setSpeed(speed);
            applyMovement(vehicle);
            return;
        }

        // Accelerate / decelerate based on input
        if (input.isForward()) {
            speed = Math.min(speed + data.getAcceleration(), data.getMaxSpeed());
        } else if (input.isBackward()) {
            speed = Math.max(speed - data.getAcceleration(), -data.getMaxSpeed() * 0.5);
        } else {
            speed = decelerate(speed, data.getAcceleration());
        }

        vehicle.setSpeed(speed);

        // Rotation - arcade style, turn even at low speed
        if (input.isLeft()) {
            vehicle.getTransform().rotate((float) -data.getRotationSpeed());
        }
        if (input.isRight()) {
            vehicle.getTransform().rotate((float) data.getRotationSpeed());
        }

        applyMovement(vehicle);
    }

    private void applyMovement(Vehicle vehicle) {
        double speed = vehicle.getSpeed();
        if (Math.abs(speed) < 0.001) {
            return;
        }

        Vector direction = vehicle.getTransform().getLocation().getDirection();
        vehicle.getTransform().move(direction.multiply(speed));
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
