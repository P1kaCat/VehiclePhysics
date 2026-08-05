package fr.pikacat.vehiclephysics.input;

import fr.pikacat.vehiclephysics.VehiclePhysics;
import fr.pikacat.vehiclephysics.vehicle.Vehicle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
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
        org.bukkit.entity.Entity vehicleEntity = player.getVehicle();
        if (vehicleEntity == null) return;

        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (vehicle.getRenderer() != null && vehicleEntity.equals(vehicle.getRenderer().getSeatEntity())) {
                org.bukkit.Input input = event.getInput();
                PlayerInput pi = vehicle.getPlayerInput();
                pi.setForward(input.isForward());
                pi.setBackward(input.isBackward());
                pi.setLeft(input.isLeft());
                pi.setRight(input.isRight());

                // Keep driver UUID synchronized
                if (vehicle.getDriverId() == null) {
                    vehicle.setDriverId(player.getUniqueId());
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
                vehicle.setDriverId(null);
                // Reset inputs
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
            if (player.getUniqueId().equals(vehicle.getDriverId())) {
                vehicle.setDriverId(null);
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
