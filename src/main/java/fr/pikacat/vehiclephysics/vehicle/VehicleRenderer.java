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
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VehicleRenderer {

    private static final int INTERPOLATION_DURATION = 2;
    private static final int TELEPORT_DURATION = 2;
    private static final float SEAT_YAW_OFFSET = 180.0f;
    private static final double SMALL_ARMOR_STAND_MOUNT_HEIGHT = 1.0;
    private static final double SEAT_BACKWARD_SHIFT = 0.15;

    // Default player walk/fly speeds (restored on exit)
    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    private final VehicleData data;
    private final List<DisplayVehicle.Part> parts = new ArrayList<>();
    private final List<SeatEntry> seats = new ArrayList<>();
    private final Set<UUID> knownSeatIds = new HashSet<>();
    private final Map<Player, SeatEntry> seatedPlayers = new HashMap<>();
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
        spawnNode(rootNode, new Matrix4f(), location);
        List<ModelManager.SeatData> modelSeats = plugin.getVehicleManager().getModelManager().getSeats(data.getId());
        if (modelSeats != null && !modelSeats.isEmpty()) {
            plugin.getLogger().info("Spawning " + modelSeats.size() + " seat(s) from BDEngine model for " + data.getId());
            for (ModelManager.SeatData seatData : modelSeats) {
                createSeat(location, seatData.offsetX, seatData.offsetY, seatData.offsetZ, seatData.name);
            }
        } else {
            plugin.getLogger().warning("No seats found in BDEngine model '" + data.getId()
                    + "'. Add seats in BDEngine before exporting the model.");
        }
        lastVehicleLocation = location.clone();
    }

    private void createSeat(Location vehicleLocation, double offsetX, double offsetY, double offsetZ, String seatName) {
        Location mountLoc = computeSeatLocation(vehicleLocation, offsetX, offsetY, offsetZ);
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
        seats.add(new SeatEntry(mount, offsetX, offsetY, offsetZ, seatName));
        VehiclePhysics plugin = VehiclePhysics.getPlugin(VehiclePhysics.class);
        plugin.getLogger().info("Created seat '" + seatName + "' for vehicle " + data.getId() + " at " + mountLoc
                + " (offset " + offsetX + "," + offsetY + "," + offsetZ + ")");
    }

    private Location computeSeatLocation(Location vehicleLocation, double offsetX, double offsetY, double offsetZ) {
        float yaw = vehicleLocation.getYaw();
        double rad = Math.toRadians(yaw);
        double adjustedOffsetZ = offsetZ + SEAT_BACKWARD_SHIFT;
        double rotatedX = offsetX * Math.cos(rad) - adjustedOffsetZ * Math.sin(rad);
        double rotatedZ = offsetX * Math.sin(rad) + adjustedOffsetZ * Math.cos(rad);
        double adjustedY = offsetY - SMALL_ARMOR_STAND_MOUNT_HEIGHT;
        Location loc = vehicleLocation.clone().add(rotatedX, adjustedY, rotatedZ);
        loc.setYaw(yaw + SEAT_YAW_OFFSET);
        return loc;
    }

    public void enterVehicle(Player player) {
        VehiclePhysics plugin = VehiclePhysics.getPlugin(VehiclePhysics.class);
        for (SeatEntry seat : seats) {
            if (!seatedPlayers.containsValue(seat)) {
                plugin.getLogger().info("Seating player " + player.getName() + " on seat '" + seat.name + "'");
                seatedPlayers.put(player, seat);
                // Prevent the player from walking — the vehicle controls movement
                player.setWalkSpeed(0);
                player.setFlySpeed(0);
                return;
            }
        }
        plugin.getLogger().warning("No available seat for player " + player.getName());
    }

    public void exitVehicle(Player player) {
        if (seatedPlayers.containsKey(player)) {
            seatedPlayers.remove(player);
            // Restore player movement
            player.setWalkSpeed(DEFAULT_WALK_SPEED);
            player.setFlySpeed(DEFAULT_FLY_SPEED);
            // Teleport player next to the vehicle so they don't get stuck in the model
            SeatEntry seat = seatedPlayers.get(player);
            if (seat != null && seat.mountEntity.isValid()) {
                Location exitLoc = seat.mountEntity.getLocation().clone().add(1, 0, 0);
                exitLoc.setYaw(player.getLocation().getYaw());
                exitLoc.setPitch(player.getLocation().getPitch());
                player.teleport(exitLoc);
            }
        }
    }

    private void spawnNode(ModelManager.ModelNode node, Matrix4f parentMatrix, Location location) {
        Matrix4f localMatrix = new Matrix4f();
        if (node.transforms != null && node.transforms.length == 16) {
            localMatrix.set(node.transforms).transpose();
        }
        Matrix4f combinedMatrix = new Matrix4f(parentMatrix).mul(localMatrix);
        if (node.isInteraction) {
            return;
        }
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

        // Teleport seat ArmorStands (for click detection)
        for (SeatEntry seat : seats) {
            if (seat.mountEntity.isValid()) {
                Location mountLoc = computeSeatLocation(loc, seat.offsetX, seat.offsetY, seat.offsetZ);
                seat.mountEntity.teleport(mountLoc);
            }
        }

        // Move seated players using velocity for smooth movement without camera reset.
        // If the player drifts too far from the seat, teleport to correct.
        for (Map.Entry<Player, SeatEntry> entry : new HashMap<>(seatedPlayers).entrySet()) {
            Player player = entry.getKey();
            SeatEntry seat = entry.getValue();
            if (!player.isOnline()) {
                seatedPlayers.remove(player);
                continue;
            }

            Location targetLoc = computeSeatLocation(loc, seat.offsetX, seat.offsetY, seat.offsetZ);
            Location playerLoc = player.getLocation();
            double distance = Math.sqrt(
                Math.pow(targetLoc.getX() - playerLoc.getX(), 2) +
                Math.pow(targetLoc.getY() - playerLoc.getY(), 2) +
                Math.pow(targetLoc.getZ() - playerLoc.getZ(), 2)
            );

            if (distance > 2.0) {
                // Drifted too far — teleport to correct (rare, shouldn't happen normally)
                Location corrected = targetLoc.clone();
                corrected.setYaw(playerLoc.getYaw());
                corrected.setPitch(playerLoc.getPitch());
                player.teleport(corrected);
            } else {
                // Smooth movement via velocity — no camera reset
                Vector velocity = new Vector(
                    (targetLoc.getX() - playerLoc.getX()),
                    (targetLoc.getY() - playerLoc.getY()),
                    (targetLoc.getZ() - playerLoc.getZ())
                );
                player.setVelocity(velocity);
            }
        }
    }

    public void remove() {
        for (Player player : new ArrayList<>(seatedPlayers.keySet())) {
            exitVehicle(player);
        }
        seatedPlayers.clear();
        for (SeatEntry seat : seats) {
            if (seat.mountEntity.isValid()) {
                seat.mountEntity.remove();
            }
        }
        seats.clear();
        knownSeatIds.clear();
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

    public int getSeatCount() {
        return seats.size();
    }

    private static class SeatEntry {
        final Entity mountEntity;
        final double offsetX;
        final double offsetY;
        final double offsetZ;
        final String name;

        SeatEntry(Entity mountEntity, double offsetX, double offsetY, double offsetZ, String name) {
            this.mountEntity = mountEntity;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.name = name;
        }
    }
}
