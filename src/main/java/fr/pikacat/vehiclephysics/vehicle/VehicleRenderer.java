package fr.pikacat.vehiclephysics.vehicle;

import fr.pikacat.vehiclephysics.VehiclePhysics;
import fr.pikacat.vehiclephysics.managers.ModelManager;
import fr.pikacat.vehiclephysics.rendering.DisplayVehicle;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
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

    // Seat offset from BDEngine model data: ~1.05625 ~-0.075 ~0.7875
    private static final double SEAT_OFFSET_X = 1.05625;
    private static final double SEAT_OFFSET_Y = -0.075;
    private static final double SEAT_OFFSET_Z = 0.7875;
    private static final double SEAT_MOUNT_Y_OFFSET = 0.5;

    private final VehicleData data;
    private final List<DisplayVehicle.Part> parts = new ArrayList<>();
    private final List<SeatEntry> seats = new ArrayList<>();
    private final Set<UUID> knownSeatIds = new HashSet<>();

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

        // Create seat (invisible armor stand) at seat position
        createSeat(location);
    }

    private void createSeat(Location vehicleLocation) {
        float yaw = vehicleLocation.getYaw();
        double[] rotated = rotateOffset(SEAT_OFFSET_X, SEAT_OFFSET_Z, yaw);
        Location seatLoc = vehicleLocation.clone().add(rotated[0], SEAT_OFFSET_Y, rotated[1]);
        Location mountLoc = seatLoc.clone().add(0, SEAT_MOUNT_Y_OFFSET, 0);

        // Invisible armor stand — has a hitbox so PlayerInteractEntityEvent fires
        // NOT a marker (markers have no hitbox), just invisible
        ArmorStand mount = vehicleLocation.getWorld().spawn(mountLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setCanPickupItems(false);
            stand.setRemoveWhenFarAway(false);
        });

        knownSeatIds.add(mount.getUniqueId());
        seats.add(new SeatEntry(mount, SEAT_OFFSET_X, SEAT_OFFSET_Y, SEAT_OFFSET_Z));

        VehiclePhysics plugin = VehiclePhysics.getPlugin(VehiclePhysics.class);
        plugin.getLogger().info("Created seat for vehicle " + data.getId() + " at " + mountLoc);
        plugin.getLogger().info("  Armor stand UUID: " + mount.getUniqueId());
    }

    private double[] rotateOffset(double offsetX, double offsetZ, float yaw) {
        double rad = Math.toRadians(yaw);
        double rotatedX = offsetX * Math.cos(rad) - offsetZ * Math.sin(rad);
        double rotatedZ = offsetX * Math.sin(rad) + offsetZ * Math.cos(rad);
        return new double[]{rotatedX, rotatedZ};
    }

    public void enterVehicle(Player player) {
        VehiclePhysics plugin = VehiclePhysics.getPlugin(VehiclePhysics.class);
        for (SeatEntry seat : seats) {
            if (seat.mountEntity.isValid() && seat.mountEntity.getPassengers().isEmpty()) {
                plugin.getLogger().info("Mounting player " + player.getName() + " on seat " + seat.mountEntity.getUniqueId());
                seat.mountEntity.addPassenger(player);
                return;
            }
        }
        plugin.getLogger().warning("No available seat for player " + player.getName());
    }

    public void exitVehicle(Player player) {
        for (SeatEntry seat : seats) {
            if (seat.mountEntity.isValid() && seat.mountEntity.getPassengers().contains(player)) {
                seat.mountEntity.removePassenger(player);
                return;
            }
        }
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
                display.setPersistent(false);

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
                display.setPersistent(false);

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

        // Teleport seat (armor stand) to rotated seat position
        for (SeatEntry seat : seats) {
            if (seat.mountEntity.isValid()) {
                double[] rotated = rotateOffset(seat.offsetX, seat.offsetZ, yaw);
                Location mountLoc = loc.clone().add(rotated[0], seat.offsetY + SEAT_MOUNT_Y_OFFSET, rotated[1]);
                mountLoc.setYaw(yaw);
                seat.mountEntity.teleport(mountLoc);
            }
        }
    }

    public void remove() {
        // Dismount all passengers and remove seats
        for (SeatEntry seat : seats) {
            if (seat.mountEntity.isValid()) {
                for (Entity passenger : new ArrayList<>(seat.mountEntity.getPassengers())) {
                    seat.mountEntity.removePassenger(passenger);
                }
                seat.mountEntity.remove();
            }
        }
        seats.clear();
        knownSeatIds.clear();

        // Remove visual display parts
        for (DisplayVehicle.Part part : parts) {
            part.entity.remove();
        }
        parts.clear();
    }

    public List<DisplayVehicle.Part> getParts() {
        return parts;
    }

    public boolean isVehicleEntity(Entity entity) {
        for (DisplayVehicle.Part part : parts) {
            if (entity.equals(part.entity)) {
                return true;
            }
        }
        return knownSeatIds.contains(entity.getUniqueId());
    }

    private static class SeatEntry {
        final Entity mountEntity;
        final double offsetX, offsetY, offsetZ;

        SeatEntry(Entity mountEntity, double offsetX, double offsetY, double offsetZ) {
            this.mountEntity = mountEntity;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }
    }
}
