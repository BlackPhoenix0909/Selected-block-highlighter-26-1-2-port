package cc.spea.selectedblockhighlighter.client;

import cc.spea.selectedblockhighlighter.config.ModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

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
                    if (client.player != null) {
                        // Reine client-seitige Statusmeldung: direkt an die Chat-HUD
                        // statt ueber LocalPlayer#displayClientMessage, dessen exakte
                        // Signatur/Existenz fuer 26.1.2 nicht zweifelsfrei verifiziert
                        // werden konnte (im letzten Build-Log als Fehler aufgetaucht).
                        // Falls client.gui hier ebenfalls nicht passt: in IntelliJ auf
                        // "client." tippen und nach "gui"/"getChat"/"Chat" suchen.
                        String status = config.isEnabled() ? "enabled" : "disabled";
                        client.gui.getChat().addMessage(Component.literal("Block Highlighter " + status));
                    }
                }
            } else {
                wasPressed = false;
            }
        });
    }
}
