package fr.pikacat.vehiclephysics.input;

import fr.pikacat.vehiclephysics.VehiclePhysics;
import fr.pikacat.vehiclephysics.vehicle.Vehicle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDismountEvent;

public class InputListener implements Listener {

    private final VehiclePhysics plugin;

    public InputListener(VehiclePhysics plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();

        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (player.equals(vehicle.getDriver())) {
                org.bukkit.Input input = event.getInput();
                PlayerInput pi = vehicle.getPlayerInput();
                pi.setForward(input.isForward());
                pi.setBackward(input.isBackward());
                pi.setLeft(input.isLeft());
                pi.setRight(input.isRight());
                break;
            }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        org.bukkit.entity.Entity clicked = event.getRightClicked();

        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (vehicle.getRenderer() == null) continue;

            // Check if clicked entity is part of this vehicle
            boolean isVehicleEntity = false;

            // Check seat entity
            if (clicked.equals(vehicle.getRenderer().getSeatEntity())) {
                isVehicleEntity = true;
            }

            // Check display parts
            if (!isVehicleEntity) {
                for (var part : vehicle.getRenderer().getParts()) {
                    if (clicked.equals(part.entity)) {
                        isVehicleEntity = true;
                        break;
                    }
                }
            }

            if (isVehicleEntity) {
                if (vehicle.getDriver() == null) {
                    // Mount player on seat
                    if (vehicle.getRenderer().getSeatEntity() != null) {
                        vehicle.getRenderer().getSeatEntity().addPassenger(player);
                        vehicle.setDriver(player);
                        player.sendMessage("You are now driving this vehicle.");
                    }
                    event.setCancelled(true);
                }
                break;
            }
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        org.bukkit.entity.Entity vehicleEntity = event.getDismounted();

        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (vehicle.getRenderer() != null && vehicleEntity.equals(vehicle.getRenderer().getSeatEntity())) {
                vehicle.setDriver(null);
                PlayerInput pi = vehicle.getPlayerInput();
                pi.setForward(false);
                pi.setBackward(false);
                pi.setLeft(false);
                pi.setRight(false);
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (player.equals(vehicle.getDriver())) {
                vehicle.setDriver(null);
                PlayerInput pi = vehicle.getPlayerInput();
                pi.setForward(false);
                pi.setBackward(false);
                pi.setLeft(false);
                pi.setRight(false);
                if (vehicle.getRenderer() != null && vehicle.getRenderer().getSeatEntity() != null) {
                    vehicle.getRenderer().getSeatEntity().removePassenger(player);
                }
                break;
            }
        }
    }
}
