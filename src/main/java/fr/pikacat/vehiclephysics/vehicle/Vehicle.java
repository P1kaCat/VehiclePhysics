package fr.pikacat.vehiclephysics.vehicle;

import java.util.UUID;
import org.bukkit.entity.Player;
import fr.pikacat.vehiclephysics.input.PlayerInput;

public class Vehicle {

    private final UUID id;
    private final VehicleData data;
    private final VehicleTransform transform;
    private final VehicleController controller;
    private VehicleRenderer renderer;

    private Player driver;
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

    public Player getDriver() {
        return driver;
    }

    public void setDriver(Player driver) {
        this.driver = driver;
    }

    public PlayerInput getPlayerInput() {
        return playerInput;
    }

    public void update() {
        // 1. Read player input (handled by InputListener → PlayerInput)
        // 2. Apply steering
        // 3. Apply movement
        // 4. Apply gravity
        controller.update(playerInput, this);

        // 5. Update the BDEngine model
        if (renderer != null) {
            renderer.update(transform);
        }
    }
}
