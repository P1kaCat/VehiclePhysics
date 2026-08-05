package fr.pikacat.vehiclephysics.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            player.sendMessage("Usage: /vehicle spawn <id> OR /vehicle remove");
            return true;
        }

        if (args[0].equalsIgnoreCase("spawn")) {
            String vehicleId = "supra";
            if (args.length >= 2) {
                vehicleId = args[1];
            }

            Location location = player.getLocation();
            VehicleData data = new VehicleData(
                    vehicleId,
                    "models/" + vehicleId + ".bdengine",
                    1.5,
                    0.05,
                    5
            );

            VehicleTransform transform = new VehicleTransform(location.clone());
            Vehicle vehicle = new Vehicle(data, transform);

            // Spawn the visual models
            vehicle.getRenderer().spawn(location);

            // Place driver on the vehicle seat
            if (vehicle.getRenderer().getSeatEntity() != null) {
                vehicle.getRenderer().getSeatEntity().addPassenger(player);
                vehicle.setDriverId(player.getUniqueId());
            }

            vehicleManager.addVehicle(vehicle);

            player.sendMessage("Vehicle spawned: " + vehicleId);
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

        player.sendMessage("Unknown subcommand. Usage: /vehicle spawn <id> OR /vehicle remove");
        return true;
    }
}
