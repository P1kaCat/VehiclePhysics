package fr.pikacat.vehiclephysics.vehicle;

import java.util.UUID;
import fr.pikacat.vehiclephysics.input.PlayerInput;

public class Vehicle {

    private final UUID id;
    private final VehicleData data;
    private final VehicleTransform transform;
    private final VehicleController controller;
    private VehicleRenderer renderer;

    private UUID driverId;
    private double speed;
    private final PlayerInput playerInput = new PlayerInput();

    public Vehicle(VehicleData data, VehicleTransform transform) {
        this.id = UUID.randomUUID();
        this.data = data;
        this.transform = transform;
        this.controller = new VehicleController();
        this.renderer = new VehicleRenderer(data);
    }

    public UUID getId() {
        return id;
    }

    public VehicleData getData() {
        return data;
    }

    public VehicleTransform getTransform() {
        return transform;
    }

    public VehicleController getController() {
        return controller;
    }

    public VehicleRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(VehicleRenderer renderer) {
        this.renderer = renderer;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public PlayerInput getPlayerInput() {
        return playerInput;
    }

    public void update() {
        controller.update(playerInput, this);
        if (renderer != null) {
            renderer.update(transform);
        }
    }
}
