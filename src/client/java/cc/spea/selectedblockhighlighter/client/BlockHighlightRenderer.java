package cc.spea.selectedblockhighlighter.client;

import cc.spea.selectedblockhighlighter.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * KOMPLETT NEU GESCHRIEBEN gegenueber der 1.20.1-Version (zweites Mal, siehe
 * Chat-Verlauf) - diesmal auf Basis des neuen Gizmo-Systems.
 *
 * WICHTIGER ARCHITEKTURWECHSEL: Zwischen 1.21.10 und 1.21.11 hat Minecraft
 * das komplette manuelle "Debug-artige" Welt-Rendering (RenderPipelines.DEBUG_*
 * + eigene BufferBuilder/RenderPass-Verwaltung) durch ein neues, deutlich
 * einfacheres Gizmo-System ersetzt (net.minecraft.gizmos). Ein Gizmo ist ein
 * Objekt, das primitive Formen (Linien, Punkte, Quads, Text) "emittiert" und
 * per Gizmos.addGizmo(...) eingereicht wird - Minecraft kuemmert sich danach
 * selbststaendig um Buffer, Pipeline und Zeichnen. Quelle (verifiziert):
 * NeoForged 1.21.10->1.21.11 Migration Primer, Abschnitt "Gizmos":
 * https://docs.neoforged.net/primer/docs/1.21.11/
 *
 * Dadurch entfaellt die komplette vorherige Drawing-Phase (AFTER_TRANSLUCENT_TERRAIN
 * + eigene RenderPipeline-Objekte + BufferBuilder + MappableRingBuffer) ersatzlos.
 * Es bleibt nur noch die Extraction-Phase, die die Gizmos einreicht.
 *
 * UNSICHERHEIT (bitte im IDE mit Autovervollstaendigung/Javadoc gegenpruefen,
 * da ich dies nicht kompilieren konnte):
 *  - GizmoPrimitives#addLine(Vec3, Vec3, int argbColor, float lineWidth) ist
 *    aus der offiziellen Fabric-Doku 1:1 belegt (siehe world.md, ExampleGizmo).
 *  - Eine gefuellte (halbtransparente) Box zusaetzlich zum Umriss ist NICHT
 *    mehr umgesetzt, da die genaue Signatur fuer Flaechen/Quads auf
 *    GizmoPrimitives nicht zweifelsfrei verifiziert werden konnte. Der Umriss
 *    (Linien) allein entspricht optisch der vanilla-typischen Block-Outline.
 *    Falls eine gefuellte Flaeche gewuenscht ist: in IntelliJ auf
 *    "GizmoPrimitives." tippen und nach einer add*Quad/addRect-Methode suchen.
 *  - Konfigurierbare Linienbreite (config.getLineWidth()) wird ueber den
 *    lineWidth-Parameter von addLine wieder unterstuetzt (im Unterschied zur
 *    vorherigen RenderPipelines-Version, wo das nicht mehr moeglich war).
 */
@Environment(EnvType.CLIENT)
public class BlockHighlightRenderer {

    /** Eine einzelne Boxkontur, die als 12 Linien-Gizmos emittiert wird. */
    private record BlockOutlineGizmo(double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ,
                                      int argbColor, float lineWidth) implements Gizmo {
        @Override
        public void emit(GizmoPrimitives gizmos, float alphaMultiplier) {
            int color = ARGB.multiplyAlpha(argbColor, alphaMultiplier);

            Vec3 v000 = new Vec3(minX, minY, minZ);
            Vec3 v001 = new Vec3(minX, minY, maxZ);
            Vec3 v010 = new Vec3(minX, maxY, minZ);
            Vec3 v011 = new Vec3(minX, maxY, maxZ);
            Vec3 v100 = new Vec3(maxX, minY, minZ);
            Vec3 v101 = new Vec3(maxX, minY, maxZ);
            Vec3 v110 = new Vec3(maxX, maxY, minZ);
            Vec3 v111 = new Vec3(maxX, maxY, maxZ);

            // Untere 4 Kanten
            gizmos.addLine(v000, v100, color, lineWidth);
            gizmos.addLine(v100, v101, color, lineWidth);
            gizmos.addLine(v101, v001, color, lineWidth);
            gizmos.addLine(v001, v000, color, lineWidth);
            // Obere 4 Kanten
            gizmos.addLine(v010, v110, color, lineWidth);
            gizmos.addLine(v110, v111, color, lineWidth);
            gizmos.addLine(v111, v011, color, lineWidth);
            gizmos.addLine(v011, v010, color, lineWidth);
            // 4 senkrechte Kanten
            gizmos.addLine(v000, v010, color, lineWidth);
            gizmos.addLine(v100, v110, color, lineWidth);
            gizmos.addLine(v101, v111, color, lineWidth);
            gizmos.addLine(v001, v011, color, lineWidth);
        }
    }

    /**
     * Extraction-Phase: Weltdaten lesen und direkt als Gizmos einreichen.
     * Es gibt bewusst keine separate Drawing-Phase / render()-Methode mehr,
     * das Gizmo-System uebernimmt das Zeichnen selbststaendig (siehe
     * Klassenkommentar oben).
     */
    public static void extract(LevelExtractionContext context) {
        ModConfig config = ModConfig.getInstance();
        if (!config.isEnabled()) {
            return;
        }

        List<BlockPos> blocks = BlockScanner.getMatchingBlocks();
        if (blocks.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        float[] color = config.getHighlightColor();
        // Manuelles ARGB-Packing statt einer evtl. nicht existierenden
        // ARGB-Hilfsmethode, um hier kein weiteres Rateelement einzubauen.
        int a = Math.round(color[3] * 255f) & 0xFF;
        int r = Math.round(color[0] * 255f) & 0xFF;
        int g = Math.round(color[1] * 255f) & 0xFF;
        int b = Math.round(color[2] * 255f) & 0xFF;
        int argbColor = (a << 24) | (r << 16) | (g << 8) | b;
        float lineWidth = config.getLineWidth();

        for (BlockPos pos : blocks) {
            BlockState state = client.level.getBlockState(pos);
            VoxelShape shape = state.getShape(client.level, pos);

            if (shape.isEmpty()) {
                if (!client.level.getFluidState(pos).isEmpty()) {
                    shape = Shapes.block();
                } else {
                    continue;
                }
            }

            double minX = pos.getX() + shape.min(Direction.Axis.X);
            double minY = pos.getY() + shape.min(Direction.Axis.Y);
            double minZ = pos.getZ() + shape.min(Direction.Axis.Z);
            double maxX = pos.getX() + shape.max(Direction.Axis.X);
            double maxY = pos.getY() + shape.max(Direction.Axis.Y);
            double maxZ = pos.getZ() + shape.max(Direction.Axis.Z);

            Gizmos.addGizmo(new BlockOutlineGizmo(minX, minY, minZ, maxX, maxY, maxZ, argbColor, lineWidth));
        }
    }
}
