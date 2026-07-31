package cc.spea.selectedblockhighlighter;

import cc.spea.selectedblockhighlighter.client.BlockHighlightRenderer;
import cc.spea.selectedblockhighlighter.client.BlockScanner;
import cc.spea.selectedblockhighlighter.client.KeyBindings;
import cc.spea.selectedblockhighlighter.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-Einstiegspunkt.
 *
 * WICHTIGSTE AENDERUNG gegenueber 1.20.1:
 * WorldRenderEvents (fabric-rendering-v1) wurde fuer die neue, zweiphasige
 * Rendering-Pipeline (Extraction -> Drawing) durch LevelExtractionEvents /
 * LevelRenderEvents ersetzt. Siehe BlockHighlightRenderer fuer Details.
 */
@Environment(EnvType.CLIENT)
public class SelectedBlockHighlighterClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("selected-block-highlighter");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Selected Block Highlighter Client");

        ModConfig.getInstance();
        KeyBindings.register();

        // Extraction-Phase: Weltdaten lesen (BlockScanner) und in ein
        // unveraenderliches Render-State-Objekt packen.
        LevelExtractionEvents.END_EXTRACTION.register(BlockHighlightRenderer::extract);
        // Drawing-Phase: nur noch das extrahierte Render-State zeichnen.
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(BlockHighlightRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && client.player != null) {
                BlockScanner.scanForBlocks();
            }
        });

        LOGGER.info("Selected Block Highlighter Client initialized");
    }
}
