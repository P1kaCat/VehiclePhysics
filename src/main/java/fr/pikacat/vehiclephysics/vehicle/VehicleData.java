package fr.pikacat.vehiclephysics.vehicle;

public class VehicleData {

    private final String id;
    private final String modelPath;
    private final double maxSpeed;
    private final double acceleration;
    private final double rotationSpeed;

    public VehicleData(String id, String modelPath, double maxSpeed, double acceleration, double rotationSpeed) {
        this.id = id;
        this.modelPath = modelPath;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.rotationSpeed = rotationSpeed;
    }

    public String getId() {
        return id;
    }

    public String getModelPath() {
        return modelPath;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public double getRotationSpeed() {
        return rotationSpeed;
    }
}
