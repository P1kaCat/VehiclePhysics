package fr.pikacat.vehiclephysics.managers;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import fr.pikacat.vehiclephysics.VehiclePhysics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class ModelManager {

    private final VehiclePhysics plugin;
    private final Map<String, ModelNode> modelCache = new HashMap<>();
    private final Map<String, List<SeatData>> seatCache = new HashMap<>();
    private final Gson gson = new Gson();

    public ModelManager(VehiclePhysics plugin) {
        this.plugin = plugin;
    }

    public ModelNode getModel(String modelName) {
        if (modelCache.containsKey(modelName)) {
            return modelCache.get(modelName);
        }
        ModelNode model = loadModel(modelName);
        if (model != null) {
            modelCache.put(modelName, model);
        }
        return model;
    }

    /**
     * Get all seats defined in the BDEngine model.
     * Returns an empty list if the model has no seats.
     */
    public List<SeatData> getSeats(String modelName) {
        if (seatCache.containsKey(modelName)) {
            return seatCache.get(modelName);
        }
        // Ensure model is loaded
        getModel(modelName);
        // loadModel populates seatCache
        return seatCache.getOrDefault(modelName, Collections.emptyList());
    }

    private ModelNode loadModel(String modelName) {
        String resourcePath = "models/" + modelName + ".bdengine";

        // 1. Try bundled in JAR (src/main/resources/models/)
        InputStream resourceStream = plugin.getResource(resourcePath);
        if (resourceStream != null) {
            plugin.getLogger().info("Loading model from bundled resource: " + resourcePath);
            ModelNode node = parseBdEngine(resourceStream, modelName);
            if (node != null) {
                return node;
            }
        }

        // 2. Fallback: filesystem (plugins/vehiclephysics/models/)
        File file = new File(plugin.getDataFolder(), "models/" + modelName + ".bdengine");
        if (file.exists()) {
            plugin.getLogger().info("Loading model from filesystem: " + file.getAbsolutePath());
            try (FileInputStream fis = new FileInputStream(file)) {
                ModelNode node = parseBdEngine(fis, modelName);
                if (node != null) {
                    return node;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading model from filesystem: " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().warning("Model file not found: " + modelName + " (checked JAR and " + file.getAbsolutePath() + ")");
        return null;
    }

    private ModelNode parseBdEngine(InputStream inputStream, String modelName) {
        List<SeatData> extractedSeats = new ArrayList<>();

        try (GZIPInputStream gis = new GZIPInputStream(inputStream)) {

            byte[] header = new byte[9];
            int read = gis.read(header);
            if (read < 9) {
                throw new IOException("Invalid header: file too short");
            }

            String magic = new String(header, 0, 4, StandardCharsets.US_ASCII);
            if (!"PRJ2".equals(magic)) {
                throw new IOException("Invalid file magic: " + magic);
            }

            int count = ByteBuffer.wrap(header, 5, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

            for (int i = 0; i < count; i++) {
                byte[] nameLenBytes = new byte[2];
                if (gis.read(nameLenBytes) < 2) {
                    throw new IOException("Failed to read entry filename length");
                }
                int nameLen = ByteBuffer.wrap(nameLenBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

                byte[] nameBytes = new byte[nameLen];
                if (gis.read(nameBytes) < nameLen) {
                    throw new IOException("Failed to read entry filename");
                }
                String entryName = new String(nameBytes, StandardCharsets.UTF_8);

                byte[] contentLenBytes = new byte[4];
                if (gis.read(contentLenBytes) < 4) {
                    throw new IOException("Failed to read entry content length");
                }
                int contentLen = ByteBuffer.wrap(contentLenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

                byte[] contentBytes = new byte[contentLen];
                int offset = 0;
                while (offset < contentLen) {
                    int chunk = gis.read(contentBytes, offset, contentLen - offset);
                    if (chunk == -1) {
                        throw new IOException("Unexpected EOF while reading entry contents");
                    }
                    offset += chunk;
                }

                if ("scene.json".equals(entryName)) {
                    String jsonText = new String(contentBytes, StandardCharsets.UTF_8);
                    ModelNode[] rootNodes = gson.fromJson(jsonText, ModelNode[].class);
                    if (rootNodes != null && rootNodes.length > 0) {
                        // Extract seats from the model tree
                        extractSeats(rootNodes[0], new Matrix4f(), extractedSeats);

                        if (!extractedSeats.isEmpty()) {
                            plugin.getLogger().info("Found " + extractedSeats.size() + " seat(s) in model " + modelName);
                            for (SeatData seat : extractedSeats) {
                                plugin.getLogger().info("  Seat '" + seat.name + "': offset=("
                                        + seat.offsetX + ", " + seat.offsetY + ", " + seat.offsetZ + "), relativeYaw=" + seat.relativeYaw);
                            }
                        }

                        seatCache.put(modelName, extractedSeats);
                        return rootNodes[0];
                    }
                }
            }

            // No scene.json found or no seats - cache empty list
            seatCache.put(modelName, extractedSeats);

        } catch (Exception e) {
            plugin.getLogger().severe("Error parsing model " + modelName + ": " + e.getMessage());
            e.printStackTrace();
            seatCache.put(modelName, extractedSeats);
        }
        return null;
    }

    /**
     * Recursively walk the model tree and extract seat positions.
     * Uses the SAME JOML Matrix4f transform pipeline as spawnNode(), so seat
     * positions land in the exact same coordinate space as the visible model
     * parts (which are known to render correctly). This avoids a whole class
     * of row-major/column-major mismatch bugs from maintaining two separate
     * hand-rolled matrix implementations.
     *
     * @param node         Current node in the tree
     * @param parentMatrix Accumulated transform from root to this node's parent (identity at root)
     * @param seats        List to populate with extracted seats
     */
    private void extractSeats(ModelNode node, Matrix4f parentMatrix, List<SeatData> seats) {
        if (node == null) return;

        Matrix4f localMatrix = new Matrix4f();
        if (node.transforms != null && node.transforms.length == 16) {
            localMatrix.set(node.transforms).transpose();
        }

        Matrix4f combinedMatrix = new Matrix4f(parentMatrix).mul(localMatrix);

        // If this is an interaction with seat=true, extract it
        if (node.isInteraction && node.seat) {
            // Transform the interaction's local anchor point into vehicle-root space
            // using the exact same matrix pipeline spawnNode() uses for visual parts.
            Vector3f worldPos = new Vector3f();
            combinedMatrix.transformPosition(new Vector3f(node.x, node.y, node.z), worldPos);

            // Derive the seat's own facing direction from its accumulated rotation,
            // relative to the vehicle root. BDEngine models face -Z at identity
            // rotation (established convention used elsewhere in this plugin).
            Vector3f worldForward = new Vector3f();
            combinedMatrix.transformDirection(new Vector3f(0, 0, -1), worldForward);
            float relativeYaw = (float) Math.toDegrees(Math.atan2(-worldForward.x, -worldForward.z));

            SeatData seat = new SeatData();
            seat.name = node.name != null ? node.name : "Seat " + (seats.size() + 1);
            seat.offsetX = worldPos.x;
            seat.offsetY = worldPos.y - (node.height / 2.0f);
            seat.offsetZ = worldPos.z;
            seat.relativeYaw = relativeYaw;
            seat.width = node.width;
            seat.height = node.height;
            seat.oneTime = node.oneTime;
            seat.commandsBefore = node.commandsBefore;
            seat.commandsAfter = node.commandsAfter;
            seats.add(seat);
        }

        // Recurse into children
        if (node.children != null) {
            for (ModelNode child : node.children) {
                extractSeats(child, combinedMatrix, seats);
            }
        }
    }

    /**
     * Data class representing a seat extracted from a BDEngine model.
     */
    public static class SeatData {
        public String name;
        public float offsetX;  // Relative to model root
        public float offsetY;
        public float offsetZ;
        public float relativeYaw; // Seat's own facing offset relative to the vehicle's forward direction, in degrees
        public float width;
        public float height;
        public boolean oneTime;
        public String commandsBefore;
        public String commandsAfter;
    }

    public static class ModelNode {
        public String name;
        public boolean isCollection;
        public boolean isItemDisplay;
        public boolean isTextDisplay;
        public boolean isInteraction;
        public boolean seat;
        public float[] transforms; // 4x4 matrix (for collections and displays)
        // Interaction-specific fields (used when isInteraction = true)
        public float x;
        public float y;
        public float z;
        public float width;
        public float height;
        public boolean oneTime;
        public String commandsBefore;
        public String commandsAfter;
        public String commands;

        public List<ModelNode> children;
        public TagHead tagHead;
        public TextOptions options;

        private transient ItemStack cachedItemStack;

        public ItemStack getItemStack() {
            if (cachedItemStack != null) {
                return cachedItemStack;
            }
            if (isItemDisplay && tagHead != null && tagHead.value != null) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "vehicle_part");
                    profile.setProperty(new ProfileProperty("textures", tagHead.value));
                    meta.setPlayerProfile(profile);
                    head.setItemMeta(meta);
                }
                cachedItemStack = head;
                return cachedItemStack;
            }
            return null;
        }
    }

    public static class TagHead {
        @SerializedName("Value")
        public String value;
    }

    public static class TextOptions {
        public String text;
    }
}
