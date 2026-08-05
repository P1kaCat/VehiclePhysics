package fr.pikacat.vehiclephysics.scheduler;

import fr.pikacat.vehiclephysics.managers.VehicleManager;
import fr.pikacat.vehiclephysics.vehicle.Vehicle;


public class PhysicsTask implements Runnable {


    private final VehicleManager manager;


    public PhysicsTask(VehicleManager manager) {

        this.manager = manager;

    }


    @Override
    public void run() {

        for (Vehicle vehicle : manager.getVehicles()) {

            vehicle.update();

        }

    }

}