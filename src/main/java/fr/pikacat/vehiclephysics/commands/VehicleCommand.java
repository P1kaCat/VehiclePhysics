package fr.pikacat.vehiclephysics.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.pikacat.vehiclephysics.managers.VehicleManager;
import fr.pikacat.vehiclephysics.vehicle.Vehicle;
import fr.pikacat.vehiclephysics.vehicle.VehicleData;
import fr.pikacat.vehiclephysics.vehicle.VehicleTransform;

import java.util.ArrayList;

public class VehicleCommand implements CommandExecutor {

    private final VehicleManager vehicleManager;

    public VehicleCommand(VehicleManager vehicleManager) {
        this.vehicleManager = vehicleManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Usage: /vehicle spawn <id> OR /vehicle remove OR /vehicle seat <dx> <dy> <dz>");
            return true;
        }

        if (args[0].equalsIgnoreCase("spawn")) {
            String vehicleId = "supra";
            if (args.length >= 2) {
                vehicleId = args[1];
            }

            // Calculate spawn location: 3 blocks to the player's right
            Location playerLocation = player.getLocation();
            Vector right = playerLocation.getDirection()
                    .setY(0)
                    .normalize()
                    .crossProduct(new Vector(0, 1, 0));
            Location vehicleLocation = playerLocation.clone().add(right.multiply(3));

            // Keep vehicle upright, ignore player pitch
            vehicleLocation.setPitch(0);
            // BDEngine models face -Z at yaw=0, but getDirection() at yaw=0 is +Z.
            // Rotate 180° so the visual front matches the movement direction.
            vehicleLocation.setYaw(0f);

            VehicleData data = new VehicleData(
                    vehicleId,
                    "models/" + vehicleId + ".bdengine",
                    1.5,
                    0.05,
                    5
            );

            VehicleTransform transform = new VehicleTransform(vehicleLocation);
            Vehicle vehicle = new Vehicle(data, transform);

            // Spawn the visual models
            vehicle.getRenderer().spawn(vehicleLocation);

            vehicleManager.addVehicle(vehicle);

            player.sendMessage("Vehicle spawned: " + vehicleId + " (right-click to enter)");
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            int count = vehicleManager.getVehicles().size();
            for (Vehicle vehicle : new ArrayList<>(vehicleManager.getVehicles())) {
                vehicleManager.removeVehicle(vehicle);
            }
            player.sendMessage("Removed " + count + " vehicles.");
            return true;
        }

        if (args[0].equalsIgnoreCase("seat")) {
            if (args.length < 4) {
                player.sendMessage("Usage: /vehicle seat <dx> <dy> <dz>  (nudges the seat offset of the nearest vehicle)");
                return true;
            }

            Vehicle nearest = findNearestVehicle(player);
            if (nearest == null) {
                player.sendMessage("No vehicle found nearby. Spawn one first.");
                return true;
            }

            try {
                double dx = Double.parseDouble(args[1]);
                double dy = Double.parseDouble(args[2]);
                double dz = Double.parseDouble(args[3]);

                nearest.getRenderer().adjustSeatOffset(dx, dy, dz);
                player.sendMessage("Seat offset adjusted. Current offset: "
                        + nearest.getRenderer().getSeatOffsetString());
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid numbers. Usage: /vehicle seat <dx> <dy> <dz>");
            }
            return true;
        }

        player.sendMessage("Unknown subcommand. Usage: /vehicle spawn <id> OR /vehicle remove OR /vehicle seat <dx> <dy> <dz>");
        return true;
    }

    private Vehicle findNearestVehicle(Player player) {
        Vehicle nearest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Vehicle vehicle : vehicleManager.getVehicles()) {
            double distSq = vehicle.getTransform().getLocation().distanceSquared(player.getLocation());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = vehicle;
            }
        }
        return nearest;
    }
}
