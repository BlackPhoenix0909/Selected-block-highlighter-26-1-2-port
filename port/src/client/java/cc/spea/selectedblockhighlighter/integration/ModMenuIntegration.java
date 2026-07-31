package cc.spea.selectedblockhighlighter.integration;

import cc.spea.selectedblockhighlighter.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Unveraendert - Mod Menu's eigene API-Klassen bleiben stabil.
 * Getestet gegen Mod Menu 18.0.0-alpha.8 (unterstuetzt 26.1-26.1.2).
 */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::createConfigScreen;
    }
}
