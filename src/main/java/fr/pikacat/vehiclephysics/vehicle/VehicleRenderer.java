package fr.pikacat.vehiclephysics.vehicle;

import fr.pikacat.vehiclephysics.VehiclePhysics;
import fr.pikacat.vehiclephysics.managers.ModelManager;
import fr.pikacat.vehiclephysics.rendering.DisplayVehicle;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class VehicleRenderer {

    private final VehicleData data;
    private final List<DisplayVehicle.Part> parts = new ArrayList<>();
    private Entity seatEntity;

    public VehicleRenderer(VehicleData data) {
        this.data = data;
    }

    public void spawn(Location location) {
        VehiclePhysics plugin = VehiclePhysics.getPlugin(VehiclePhysics.class);
        ModelManager.ModelNode rootNode = plugin.getVehicleManager().getModelManager().getModel(data.getId());

        if (rootNode == null) {
            plugin.getLogger().warning("Failed to spawn vehicle: Model not found for ID " + data.getId());
            return;
        }

        // Spawn seat/base entity (invisible ArmorStand)
        seatEntity = location.getWorld().spawn(location, org.bukkit.entity.ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setGravity(false);
            armorStand.setSmall(true);
            armorStand.setMarker(true);
        });

        // Spawn visual parts recursively
        spawnNode(rootNode, new Matrix4f(), location);
    }

    private void spawnNode(ModelManager.ModelNode node, Matrix4f parentMatrix, Location location) {
        Matrix4f localMatrix = new Matrix4f();
        if (node.transforms != null && node.transforms.length == 16) {
            // Note: BDEngine transforms are row-major, so we load them transposed to be column-major for JOML
            localMatrix.set(node.transforms).transpose();
        }

        Matrix4f combinedMatrix = new Matrix4f(parentMatrix).mul(localMatrix);

        if (node.isItemDisplay && node.getItemStack() != null) {
            ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class, display -> {
                display.setItemStack(node.getItemStack());
                
                // Convert relative combined matrix to Bukkit Transformation
                Vector3f translation = new Vector3f();
                Quaternionf rotation = new Quaternionf();
                Vector3f scale = new Vector3f();
                combinedMatrix.getTranslation(translation);
                combinedMatrix.getUnnormalizedRotation(rotation);
                combinedMatrix.getScale(scale);

                display.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
                display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            });
            parts.add(new DisplayVehicle.Part(itemDisplay, combinedMatrix));
        } else if (node.isTextDisplay && node.options != null && node.options.text != null) {
            TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class, display -> {
                display.setText(node.options.text);

                Vector3f translation = new Vector3f();
                Quaternionf rotation = new Quaternionf();
                Vector3f scale = new Vector3f();
                combinedMatrix.getTranslation(translation);
                combinedMatrix.getUnnormalizedRotation(rotation);
                combinedMatrix.getScale(scale);

                display.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
                display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            });
            parts.add(new DisplayVehicle.Part(textDisplay, combinedMatrix));
        }

        if (node.children != null) {
            for (ModelManager.ModelNode child : node.children) {
                spawnNode(child, combinedMatrix, location);
            }
        }
    }

    public void update(VehicleTransform transform) {
        Location loc = transform.getLocation();
        float yaw = transform.getYaw();

        // Move the driver seat base entity
        if (seatEntity != null && seatEntity.isValid()) {
            Location seatLoc = loc.clone();
            seatLoc.setYaw(yaw);
            seatEntity.teleport(seatLoc);
        }

        // Teleport all parts to the vehicle center and align their yaw
        for (DisplayVehicle.Part part : parts) {
            if (part.entity.isValid()) {
                Location partLoc = loc.clone();
                partLoc.setYaw(yaw);
                part.entity.teleport(partLoc);
            }
        }
    }

    public void remove() {
        if (seatEntity != null) {
            seatEntity.remove();
            seatEntity = null;
        }
        for (DisplayVehicle.Part part : parts) {
            part.entity.remove();
        }
        parts.clear();
    }

    public Entity getSeatEntity() {
        return seatEntity;
    }
}