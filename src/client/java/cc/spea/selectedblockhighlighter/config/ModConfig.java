package cc.spea.selectedblockhighlighter.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Unveraendert gegenueber 1.20.1 - FabricLoader#getConfigDir() und Gson
 * haben sich seitdem nicht geaendert.
 */
@Environment(EnvType.CLIENT)
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("selected-block-highlighter");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "selected-block-highlighter.json");

    public boolean enabled = false;
    public int scanRange = 32;
    public float highlightRed = 1.0f;
    public float highlightGreen = 1.0f;
    public float highlightBlue = 0.0f;
    public float highlightAlpha = 0.4f;
    public float lineWidth = 2.0f;
    public boolean seeThroughBlocks = false;

    private static ModConfig instance;

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                LOGGER.info("Config loaded from {}", CONFIG_FILE);
                return config != null ? config : new ModConfig();
            } catch (IOException e) {
                LOGGER.error("Failed to load config, using defaults", e);
                return new ModConfig();
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
            LOGGER.info("Config saved to {}", CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public void toggleEnabled() {
        this.enabled = !this.enabled;
        this.save();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public int getScanRange() {
        return this.scanRange;
    }

    public void setScanRange(int range) {
        this.scanRange = Math.max(1, Math.min(128, range));
        this.save();
    }

    public float[] getHighlightColor() {
        return new float[]{highlightRed, highlightGreen, highlightBlue, highlightAlpha};
    }

    public void setHighlightColor(float r, float g, float b, float a) {
        this.highlightRed = Math.max(0.0f, Math.min(1.0f, r));
        this.highlightGreen = Math.max(0.0f, Math.min(1.0f, g));
        this.highlightBlue = Math.max(0.0f, Math.min(1.0f, b));
        this.highlightAlpha = Math.max(0.0f, Math.min(1.0f, a));
        this.save();
    }

    public float getLineWidth() {
        return this.lineWidth;
    }

    public void setLineWidth(float width) {
        this.lineWidth = Math.max(0.5f, Math.min(10.0f, width));
        this.save();
    }

    public boolean isSeeThroughBlocks() {
        return this.seeThroughBlocks;
    }

    public void setSeeThroughBlocks(boolean seeThroughBlocks) {
        this.seeThroughBlocks = seeThroughBlocks;
        this.save();
    }
}
