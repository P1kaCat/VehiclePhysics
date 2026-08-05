package fr.pikacat.vehiclephysics.vehicle;

import fr.pikacat.vehiclephysics.VehiclePhysics;
import fr.pikacat.vehiclephysics.managers.ModelManager;
import fr.pikacat.vehiclephysics.rendering.DisplayVehicle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VehicleRenderer {

    private static final int INTERPOLATION_DURATION = 2;
    private static final int TELEPORT_DURATION = 2;
    private static final double INTERACTION_SCAN_RADIUS = 10.0;

    private final VehicleData data;
    private final List<DisplayVehicle.Part> parts = new ArrayList<>();
    private final List<Entity> interactionEntities = new ArrayList<>();
    private final Set<UUID> knownInteractionIds = new HashSet<>();

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

        // Spawn visual parts recursively
        spawnNode(rootNode, new Matrix4f(), location);

        // Run BDEngine interactive function (creates seat/interaction entities)
        runInteractiveFunction("create");

        // After the function runs (next tick), find and track the new Interaction entities
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            trackInteractionEntities(location);
        }, 2L); // 2 ticks delay to let the function execute
    }

    private void runInteractiveFunction(String action) {
        VehiclePhysics plugin = VehiclePhysics.getPlugin(VehiclePhysics.class);
        // Function name format: <model>full:_/<action>_interactive
        String functionName = data.getId() + "full:_/" + action + "_interactive";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "function " + functionName);
        plugin.getLogger().info("Ran function: " + functionName);
    }

    private void trackInteractionEntities(Location vehicleLocation) {
        // Find all Interaction entities near the vehicle that we don't already know about
        for (Entity entity : vehicleLocation.getWorld().getNearbyEntities(vehicleLocation, INTERACTION_SCAN_RADIUS, INTERACTION_SCAN_RADIUS, INTERACTION_SCAN_RADIUS)) {
            if (entity instanceof Interaction && !knownInteractionIds.contains(entity.getUniqueId())) {
                interactionEntities.add(entity);
                knownInteractionIds.add(entity.getUniqueId());
            }
        }

        VehiclePhysics plugin = VehiclePhysics.getPlugin(VehiclePhysics.class);
        plugin.getLogger().info("Found " + interactionEntities.size() + " interaction entities for vehicle " + data.getId());
    }

    private void spawnNode(ModelManager.ModelNode node, Matrix4f parentMatrix, Location location) {
        Matrix4f localMatrix = new Matrix4f();
        if (node.transforms != null && node.transforms.length == 16) {
            localMatrix.set(node.transforms).transpose();
        }

        Matrix4f combinedMatrix = new Matrix4f(parentMatrix).mul(localMatrix);

        if (node.isItemDisplay && node.getItemStack() != null) {
            ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class, display -> {
                display.setItemStack(node.getItemStack());

                Vector3f translation = new Vector3f();
                Quaternionf rotation = new Quaternionf();
                Vector3f scale = new Vector3f();
                combinedMatrix.getTranslation(translation);
                combinedMatrix.getUnnormalizedRotation(rotation);
                combinedMatrix.getScale(scale);

                display.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
                display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                display.setInterpolationDuration(INTERPOLATION_DURATION);
                display.setInterpolationDelay(0);
                display.setTeleportDuration(TELEPORT_DURATION);
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
                display.setInterpolationDuration(INTERPOLATION_DURATION);
                display.setInterpolationDelay(0);
                display.setTeleportDuration(TELEPORT_DURATION);
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

        // Teleport all display parts to vehicle position
        for (DisplayVehicle.Part part : parts) {
            if (part.entity.isValid()) {
                Location partLoc = loc.clone();
                partLoc.setYaw(yaw);
                part.entity.teleport(partLoc);
            }
        }

        // Teleport interaction entities (BDEngine seat) with the vehicle
        for (Entity interaction : interactionEntities) {
            if (interaction.isValid()) {
                Location intLoc = loc.clone();
                intLoc.setYaw(yaw);
                interaction.teleport(intLoc);
            }
        }
    }

    public void remove() {
        // Run BDEngine delete function to remove interaction/seat entities
        runInteractiveFunction("delete");

        // Remove interaction entities we tracked
        for (Entity interaction : interactionEntities) {
            if (interaction.isValid()) {
                interaction.remove();
            }
        }
        interactionEntities.clear();
        knownInteractionIds.clear();

        // Remove visual display parts
        for (DisplayVehicle.Part part : parts) {
            part.entity.remove();
        }
        parts.clear();
    }

    public List<DisplayVehicle.Part> getParts() {
        return parts;
    }

    public List<Entity> getInteractionEntities() {
        return interactionEntities;
    }

    public boolean isVehicleEntity(Entity entity) {
        // Check if entity is a display part
        for (DisplayVehicle.Part part : parts) {
            if (entity.equals(part.entity)) {
                return true;
            }
        }
        // Check if entity is a BDEngine interaction entity
        return knownInteractionIds.contains(entity.getUniqueId());
    }
}
