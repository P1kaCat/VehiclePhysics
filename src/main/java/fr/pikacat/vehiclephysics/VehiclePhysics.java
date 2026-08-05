package fr.pikacat.vehiclephysics;

import fr.pikacat.vehiclephysics.input.InputListener;
import fr.pikacat.vehiclephysics.scheduler.PhysicsTask;
import org.bukkit.plugin.java.JavaPlugin;

import fr.pikacat.vehiclephysics.commands.VehicleCommand;
import fr.pikacat.vehiclephysics.managers.VehicleManager;
import fr.pikacat.vehiclephysics.vehicle.Vehicle;

import java.util.ArrayList;

public class VehiclePhysics extends JavaPlugin {

    private VehicleManager vehicleManager;

    @Override
    public void onEnable() {
        getLogger().info("VehiclePhysics enabled");

        vehicleManager = new VehicleManager(this);

        // Register command
        getCommand("vehicle")
                .setExecutor(
                        new VehicleCommand(vehicleManager)
                );

        // Register input listener
        getServer().getPluginManager().registerEvents(new InputListener(this), this);

        // Schedule physics and update task to run every tick (20 ticks per second)
        getServer().getScheduler().runTaskTimer(this, new PhysicsTask(vehicleManager), 0L, 1L);
    }

    @Override
    public void onDisable() {
        getLogger().info("VehiclePhysics disabling - cleaning up vehicles...");
        if (vehicleManager != null) {
            for (Vehicle vehicle : new ArrayList<>(vehicleManager.getVehicles())) {
                vehicleManager.removeVehicle(vehicle);
            }
        }
        getLogger().info("VehiclePhysics disabled");
    }

    public VehicleManager getVehicleManager() {
        return vehicleManager;
    }
}