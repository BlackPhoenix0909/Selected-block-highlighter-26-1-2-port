package cc.spea.selectedblockhighlighter.mixin;

import cc.spea.selectedblockhighlighter.client.BlockHighlightRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neu hinzugekommen: die neue Custom-Render-Pipeline-API verwaltet eigene
 * GPU-Puffer (MappableRingBuffer / ByteBufferBuilder), die beim Beenden
 * des Spiels explizit geschlossen werden muessen. Siehe:
 * https://docs.fabricmc.net/26.1.2/develop/rendering/world#cleaning-up
 */
@Mixin(GameRenderer.class)
public class GameRendererCleanupMixin {
    @Inject(method = "close", at = @At("RETURN"))
    private void selectedBlockHighlighter$onClose(CallbackInfo ci) {
        BlockHighlightRenderer.close();
    }
}
