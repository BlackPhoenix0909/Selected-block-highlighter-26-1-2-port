package cc.spea.selectedblockhighlighter;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectedBlockHighlighter implements ModInitializer {
    public static final String MOD_ID = "selected-block-highlighter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Hello Fabric world!");
    }
}
