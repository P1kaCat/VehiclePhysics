package fr.pikacat.vehiclephysics.vehicle;

import fr.pikacat.vehiclephysics.input.PlayerInput;

import org.bukkit.util.Vector;

public class VehicleController {


    private final double speed = 0.3;


    public void update(PlayerInput input, Vehicle vehicle) {


        if(input == null) {
            return;
        }


        Vector direction =
                vehicle.getTransform()
                .getLocation()
                .getDirection();


        if(input.isForward()) {

            vehicle.getTransform()
                    .move(direction.multiply(speed));

        }


        if(input.isBackward()) {

            vehicle.getTransform()
                    .move(direction.multiply(-speed));

        }


        float yaw =
                vehicle.getTransform()
                .getYaw();


        if(input.isLeft()) {

            vehicle.getTransform()
                    .setYaw(yaw - 5);

        }


        if(input.isRight()) {

            vehicle.getTransform()
                    .setYaw(yaw + 5);

        }

    }
}