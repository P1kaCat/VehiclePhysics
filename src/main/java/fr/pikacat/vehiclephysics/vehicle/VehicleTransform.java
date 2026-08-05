package fr.pikacat.vehiclephysics.vehicle;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class VehicleTransform {

    private final Location location;
    private Vector velocity;
    private double speed;
    private float yaw;
    private double verticalVelocity;

    public VehicleTransform(Location location) {
        this.location = location;
        this.yaw = location.getYaw();
        this.velocity = new Vector(0, 0, 0);
        this.speed = 0;
        this.verticalVelocity = 0;
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

    public Vector getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector velocity) {
        this.velocity = velocity;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        location.setYaw(yaw);
    }

    public double getVerticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(double verticalVelocity) {
        this.verticalVelocity = verticalVelocity;
    }

    public void move(Vector direction) {
        location.add(direction);
    }

    public void rotate(float yawDelta) {
        this.yaw += yawDelta;
        location.setYaw(this.yaw);
    }
}
