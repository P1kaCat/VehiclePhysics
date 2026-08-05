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

    // Default seat offset (relative to vehicle root, before yaw rotation).
    // Tune live in-game with: /vehicle seat <dx> <dy> <dz>
    private double seatOffsetX = 1.05625;
    private double seatOffsetY = -0.075;
    private double seatOffsetZ = 0.7875;

    private final VehicleData data;
    private final List<DisplayVehicle.Part> parts = new ArrayList<>();
    private final List<SeatEntry> seats = new ArrayList<>();
    private final Set<UUID> knownSeatIds = new HashSet<>();
    private Location lastVehicleLocation;

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

        // Create seat (invisible small armor stand) at seat position
        createSeat(location);
        lastVehicleLocation = location.clone();
    }

    private void createSeat(Location vehicleLocation) {
        Location mountLoc = computeSeatLocation(vehicleLocation);

        // Invisible SMALL armor stand — has a hitbox so PlayerInteractEntityEvent fires,
        // small size keeps the riding player from floating above the model.
        ArmorStand mount = vehicleLocation.getWorld().spawn(mountLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setCanPickupItems(false);
            stand.setRemoveWhenFarAway(false);
        });

        knownSeatIds.add(mount.getUniqueId());
        seats.add(new SeatEntry(mount));

        VehiclePhysics plugin = VehiclePhysics.getPlugin(VehiclePhysics.class);
        plugin.getLogger().info("Created seat for vehicle " + data.getId() + " at " + mountLoc
                + " (offset " + seatOffsetX + "," + seatOffsetY + "," + seatOffsetZ + ")");
    }

    private Location computeSeatLocation(Location vehicleLocation) {
        float yaw = vehicleLocation.getYaw();
        double[] rotated = rotateOffset(seatOffsetX, seatOffsetZ, yaw);
        return vehicleLocation.clone().add(rotated[0], seatOffsetY, rotated[1]);
    }

    private double[] rotateOffset(double offsetX, double offsetZ, float yaw) {
        double rad = Math.toRadians(yaw);
        double rotatedX = offsetX * Math.cos(rad) - offsetZ * Math.sin(rad);
        double rotatedZ = offsetX * Math.sin(rad) + offsetZ * Math.cos(rad);
        return new double[]{rotatedX, rotatedZ};
    }

    /**
     * Nudge the seat offset live (for in-game calibration) and immediately
     * reposition the seat entity so the change is visible without a restart.
     */
    public void adjustSeatOffset(double dx, double dy, double dz) {
        this.seatOffsetX += dx;
        this.seatOffsetY += dy;
        this.seatOffsetZ += dz;

        if (lastVehicleLocation != null) {
            Location newMountLoc = computeSeatLocation(lastVehicleLocation);
            for (SeatEntry seat : seats) {
                if (seat.mountEntity.isValid()) {
                    seat.mountEntity.teleport(newMountLoc);
                }
            }
        }
    }

    public String getSeatOffsetString() {
        return String.format("%.4f, %.4f, %.4f", seatOffsetX, seatOffsetY, seatOffsetZ);
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
        lastVehicleLocation = loc.clone();

        // Teleport all display parts to vehicle position
        for (DisplayVehicle.Part part : parts) {
            if (part.entity.isValid()) {
                Location partLoc = loc.clone();
                partLoc.setYaw(yaw);
                part.entity.teleport(partLoc);
            }
        }

        // Teleport seat (armor stand) to rotated seat position
        Location mountLoc = computeSeatLocation(loc);
        mountLoc.setYaw(yaw);
        for (SeatEntry seat : seats) {
            if (seat.mountEntity.isValid()) {
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

        SeatEntry(Entity mountEntity) {
            this.mountEntity = mountEntity;
        }
    }
}
