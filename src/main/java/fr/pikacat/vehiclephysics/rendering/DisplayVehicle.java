package fr.pikacat.vehiclephysics.rendering;

import org.bukkit.entity.Entity;
import org.joml.Matrix4f;

public class DisplayVehicle {

    public static class Part {
        public final Entity entity;
        public final Matrix4f relativeMatrix;

        public Part(Entity entity, Matrix4f relativeMatrix) {
            this.entity = entity;
            this.relativeMatrix = relativeMatrix;
        }
    }
}
