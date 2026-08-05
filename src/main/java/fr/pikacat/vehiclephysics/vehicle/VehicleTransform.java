package fr.pikacat.vehiclephysics.vehicle;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class VehicleTransform {

    private final Location location;

    private float yaw;


    public VehicleTransform(Location location) {
        this.location = location;
        this.yaw = location.getYaw();
    }


    public Location getLocation() {
        return location;
    }


    public void move(Vector velocity) {
        location.add(velocity);
    }


    public float getYaw() {
        return yaw;
    }


    public void setYaw(float yaw) {
        this.yaw = yaw;
        location.setYaw(yaw);
    }
}