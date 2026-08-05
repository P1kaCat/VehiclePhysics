package fr.pikacat.vehiclephysics.vehicle;


public class VehicleData {

    private final String id;

    private final String modelFile;


    public VehicleData(String id, String modelFile) {

        this.id = id;
        this.modelFile = modelFile;

    }


    public String getId() {

        return id;

    }


    public String getModelFile() {

        return modelFile;

    }

}