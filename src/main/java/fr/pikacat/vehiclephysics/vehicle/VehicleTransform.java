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

    public void setLocation(Location location) {
        this.location.setX(location.getX());
        this.location.setY(location.getY());
        this.location.setZ(location.getZ());
        this.location.setYaw(location.getYaw());
        this.location.setPitch(location.getPitch());
        this.yaw = location.getYaw();
    }

    public void move(Vector direction) {
        location.add(direction);
    }

    public void rotate(float yawDelta) {
        this.yaw += yawDelta;
        location.setYaw(this.yaw);
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        location.setYaw(yaw);
    }
}
