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
                        float[] identity = new float[16]; identity[0] = 1; identity[5] = 1; identity[10] = 1; identity[15] = 1; extractSeats(rootNodes[0], identity, extractedSeats);

                        if (!extractedSeats.isEmpty()) {
                            plugin.getLogger().info("Found " + extractedSeats.size() + " seat(s) in model " + modelName);
                            for (SeatData seat : extractedSeats) {
                                plugin.getLogger().info("  Seat '" + seat.name + "': offset=("
                                        + seat.offsetX + ", " + seat.offsetY + ", " + seat.offsetZ + ")");
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
     * Accumulates parent collection transforms to compute seat positions in model space.
     *
     * @param node         Current node in the tree
     * @param parentMatrix 4x4 transform matrix from parent collections (identity if root)
     * @param seats        List to populate with extracted seats
     */
    private void extractSeats(ModelNode node, float[] parentMatrix, List<SeatData> seats) {
        if (node == null) return;

        // Get this node's local transform matrix (collections have transforms)
        float[] localMatrix = new float[16];
        if (node.transforms != null && node.transforms.length == 16) {
            // BDEngine stores matrices in column-major; Java needs row-major
            // The existing spawnNode code does .transpose(), so we do the same
            localMatrix = node.transforms.clone();
            // Transpose to get row-major
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    localMatrix[r * 4 + c] = node.transforms[c * 4 + r];
                }
            }
        } else {
            // Identity matrix
            localMatrix[0] = 1; localMatrix[5] = 1; localMatrix[10] = 1; localMatrix[15] = 1;
        }

        // Compute combined matrix = parentMatrix * localMatrix
        float[] combinedMatrix = multiplyMatrices(parentMatrix, localMatrix);

        // If this is an interaction with seat=true, extract it
        if (node.isInteraction && node.seat) {
            // The interaction's position (x, y, z) is relative to the parent
            // We need to transform it by the combined parent matrix to get model-space position
            float[] worldPos = transformPoint(combinedMatrix, node.x, node.y, node.z);

            SeatData seat = new SeatData();
            seat.name = node.name != null ? node.name : "Seat " + (seats.size() + 1);
            seat.offsetX = worldPos[0];
            seat.offsetY = worldPos[1] - (node.height / 2.0f);
            seat.offsetZ = worldPos[2];
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
     * Multiply two 4x4 matrices (row-major storage).
     */
    private float[] multiplyMatrices(float[] a, float[] b) {
        float[] result = new float[16];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                result[r * 4 + c] = 0;
                for (int k = 0; k < 4; k++) {
                    result[r * 4 + c] += a[r * 4 + k] * b[k * 4 + c];
                }
            }
        }
        return result;
    }

    /**
     * Transform a point by a 4x4 matrix (row-major).
     * Returns {x, y, z} in model space.
     */
    private float[] transformPoint(float[] matrix, float x, float y, float z) {
        float[] result = new float[3];
        // Apply the 4x4 transform (assuming bottom row is [0,0,0,1])
        result[0] = matrix[0] * x + matrix[1] * y + matrix[2] * z + matrix[3];
        result[1] = matrix[4] * x + matrix[5] * y + matrix[6] * z + matrix[7];
        result[2] = matrix[8] * x + matrix[9] * y + matrix[10] * z + matrix[11];
        return result;
    }

    /**
     * Data class representing a seat extracted from a BDEngine model.
     */
    public static class SeatData {
        public String name;
        public float offsetX;  // Relative to model root
        public float offsetY;
        public float offsetZ;
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
