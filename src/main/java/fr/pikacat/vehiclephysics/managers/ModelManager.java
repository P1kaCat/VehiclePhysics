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

    private ModelNode loadModel(String modelName) {
        File file = new File(plugin.getDataFolder(), "models/" + modelName + ".bdengine");
        if (!file.exists()) {
            // Check in local workspace models directory as fallback
            file = new File("models/" + modelName + ".bdengine");
            if (!file.exists()) {
                plugin.getLogger().warning("Model file not found: " + file.getAbsolutePath());
                return null;
            }
        }

        try (FileInputStream fis = new FileInputStream(file);
             GZIPInputStream gis = new GZIPInputStream(fis)) {

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
                        return rootNodes[0];
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error loading vehicle model " + modelName + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static class ModelNode {
        public String name;
        public boolean isCollection;
        public boolean isItemDisplay;
        public boolean isTextDisplay;
        public float[] transforms; // 4x4 matrix
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
