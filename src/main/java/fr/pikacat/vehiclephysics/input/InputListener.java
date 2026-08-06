package fr.pikacat.vehiclephysics.input;

import fr.pikacat.vehiclephysics.VehiclePhysics;
import fr.pikacat.vehiclephysics.vehicle.Vehicle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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

                // Dismount on sneak (Shift)
                if (input.isSneak()) {
                    vehicle.setDriver(null);
                    vehicle.getRenderer().exitVehicle(player);
                    resetInputs(pi);
                    // Don't cancel — let the sneak through so the player can crouch
                    break;
                }

                pi.setForward(input.isForward());
                pi.setBackward(input.isBackward());
                pi.setLeft(input.isLeft());
                pi.setRight(input.isRight());
                pi.setSneak(input.isSneak());

                // Cancel to prevent the player from walking — the vehicle controls movement
                event.setCancelled(true);
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

            if (vehicle.getRenderer().isVehicleEntity(clicked)) {
                if (vehicle.getDriver() == null) {
                    vehicle.setDriver(player);
                    vehicle.getRenderer().enterVehicle(player);
                    player.sendMessage("You are now driving this vehicle. Press Shift to exit.");
                    event.setCancelled(true);
                }
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
                vehicle.getRenderer().exitVehicle(player);
                resetInputs(vehicle.getPlayerInput());
                break;
            }
        }
    }

    private void resetInputs(PlayerInput pi) {
        pi.setForward(false);
        pi.setBackward(false);
        pi.setLeft(false);
        pi.setRight(false);
        pi.setSneak(false);
    }
}
