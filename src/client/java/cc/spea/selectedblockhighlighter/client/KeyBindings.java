package cc.spea.selectedblockhighlighter.client;

import cc.spea.selectedblockhighlighter.config.ModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Portiert von der alten KeyBinding/KeyBindingHelper-API (1.20.1) auf die
 * neue KeyMapping/KeyMappingHelper-API. Seit einigen Versionen brauchen
 * Tastenbelegungen zusaetzlich eine registrierte KeyMapping.Category
 * (statt eines reinen String-Kategorienamens).
 *
 * Quelle (versionsgenau 26.1.2): https://docs.fabricmc.net/26.1.2/develop/key-mappings
 */
@Environment(EnvType.CLIENT)
public class KeyBindings {
    private static final Logger LOGGER = LoggerFactory.getLogger("selected-block-highlighter");

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("selected-block-highlighter", "main")
    );

    private static KeyMapping toggleKey;
    private static boolean wasPressed = false;

    public static void register() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.selected-block-highlighter.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey.isDown()) {
                if (!wasPressed) {
                    wasPressed = true;
                    ModConfig config = ModConfig.getInstance();
                    config.toggleEnabled();
                    // ABSICHTLICH nur ins Log geschrieben, nicht als Chat-/HUD-Nachricht:
                    // Fuer 26.1.2 (unobfuskiert) gibt es KEINE Mapping-Datenbank mehr, die
                    // sich verifizieren liesse (siehe Chat-Erklaerung) - zwei Versuche mit
                    // der Chat-HUD-API sind bereits an falsch geratenen Signaturen
                    // gescheitert. Damit der Build sicher durchlaeuft, bleibt es vorerst
                    // beim Logeintrag. Eine sichtbare Ingame-Meldung laesst sich danach
                    // bequem per IDE-Autovervollstaendigung (gegen das echte, lokal von
                    // Loom heruntergeladene 26.1.2-Jar) ergaenzen, z. B. ueber
                    // "client.gui." tippen und die Overlay-/Chat-Methoden durchsehen.
                    if (client.player != null) {
                        String status = config.isEnabled() ? "enabled" : "disabled";
                        LOGGER.info("Block Highlighter {}", status);
                    }
                }
            } else {
                wasPressed = false;
            }
        });
    }
}
