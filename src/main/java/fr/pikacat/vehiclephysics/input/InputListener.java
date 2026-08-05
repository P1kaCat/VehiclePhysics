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

        plugin.getLogger().info("PlayerInteractEntityEvent: " + player.getName() + " clicked " + clicked.getType() + " (UUID: " + clicked.getUniqueId() + ")");

        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (vehicle.getRenderer() == null) continue;

            if (vehicle.getRenderer().isVehicleEntity(clicked)) {
                plugin.getLogger().info("  -> Entity belongs to vehicle " + vehicle.getData().getId());
                if (vehicle.getDriver() == null) {
                    vehicle.setDriver(player);
                    vehicle.getRenderer().enterVehicle(player);
                    player.sendMessage("You are now driving this vehicle.");
                    event.setCancelled(true);
                } else {
                    plugin.getLogger().info("  -> Vehicle already has a driver: " + vehicle.getDriver().getName());
                }
                break;
            }
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (player.equals(vehicle.getDriver())) {
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
                vehicle.getRenderer().exitVehicle(player);
                PlayerInput pi = vehicle.getPlayerInput();
                pi.setForward(false);
                pi.setBackward(false);
                pi.setLeft(false);
                pi.setRight(false);
                break;
            }
        }
    }
}
