package fr.pikacat.vehiclephysics.managers;

import java.util.ArrayList;
import java.util.List;

import fr.pikacat.vehiclephysics.VehiclePhysics;
import fr.pikacat.vehiclephysics.vehicle.Vehicle;

public class VehicleManager {

    private final VehiclePhysics plugin;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final ModelManager modelManager;

    public VehicleManager(VehiclePhysics plugin) {
        this.plugin = plugin;
        this.modelManager = new ModelManager(plugin);
    }

    public void addVehicle(Vehicle vehicle) {
        vehicles.add(vehicle);
    }

    public void removeVehicle(Vehicle vehicle) {
        vehicle.getRenderer().remove();
        vehicles.remove(vehicle);
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public ModelManager getModelManager() {
        return modelManager;
    }
}