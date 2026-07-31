package cc.spea.selectedblockhighlighter.client;

import cc.spea.selectedblockhighlighter.config.ModConfig;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * KOMPLETT NEU GESCHRIEBEN gegenueber der 1.20.1-Version.
 *
 * Grund: Zwischen 1.20.1 und 26.1.2 wurde die Welt-Rendering-Pipeline
 * mehrfach umgebaut (ab 1.21.6 "RenderType/RenderPipeline/RenderState"-
 * Umbau, ab 1.21.9 kurzzeitig entfernte und neu implementierte
 * WorldRenderEvents). Der alte Ansatz (Tesselator.getBuffer() +
 * RenderSystem.setShader() + manuelles GL-State-Toggling) existiert in
 * dieser Form nicht mehr.
 *
 * Stattdessen: zweiphasiges Rendering (Extraction -> Drawing) mit
 * eigenen RenderPipeline-Objekten. Struktur 1:1 nach dem offiziellen,
 * versionsgenauen Beispiel fuer 26.1.2:
 * https://docs.fabricmc.net/26.1.2/develop/rendering/world
 *
 * UNSICHERHEIT (bitte im IDE mit Autovervollstaendigung pruefen, da ich
 * das hier nicht kompilieren konnte - siehe Chat-Erklaerung):
 *  - Der exakte Name der Basis-"Snippet"-Konstante fuer Linien in
 *    RenderPipelines (hier: RenderPipelines.DEBUG_LINE_STRIP als
 *    Vollpipeline, davon per builder() abgeleitet). Falls das nicht
 *    existiert: in IntelliJ "RenderPipelines." tippen und die Liste
 *    der DEBUG_*-Konstanten durchsehen.
 *  - Eine konfigurierbare Linienbreite (config.getLineWidth()) ist mit
 *    dem neuen Pipeline-System nicht mehr trivial moeglich; DEBUG_LINES
 *    ist laut Doku "always exactly one pixel wide on the screen". Die
 *    Linienbreite aus der Config wird aktuell NICHT mehr angewendet.
 */
@Environment(EnvType.CLIENT)
public class BlockHighlightRenderer {

    private static final Identifier FILLED_PIPELINE_ID =
            Identifier.fromNamespaceAndPath("selected-block-highlighter", "pipeline/highlight_filled_through_walls");
    private static final Identifier LINE_PIPELINE_ID =
            Identifier.fromNamespaceAndPath("selected-block-highlighter", "pipeline/highlight_lines_through_walls");

    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(FILLED_PIPELINE_ID)
                    .withDepthStencilState(java.util.Optional.empty())
                    .build()
    );

    // TODO: Basis-Snippet-Name pruefen, siehe Klassenkommentar oben.
    private static final RenderPipeline LINES_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_LINE_STRIP)
                    .withLocation(LINE_PIPELINE_ID)
                    .withDepthStencilState(java.util.Optional.empty())
                    .build()
    );

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static BufferBuilder filledBuffer;
    private static BufferBuilder lineBuffer;
    private static MappableRingBuffer filledVertexBuffer;
    private static MappableRingBuffer lineVertexBuffer;

    /** Immutable Render-State, in der Extraction-Phase befuellt. */
    private record BoxState(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ) {
    }

    private static List<BoxState> extractedBoxes = List.of();

    /** Extraction-Phase: darf Weltdaten lesen, aber noch nicht zeichnen. */
    public static void extract(LevelExtractionContext context) {
        ModConfig config = ModConfig.getInstance();
        if (!config.isEnabled()) {
            extractedBoxes = List.of();
            return;
        }

        List<BlockPos> blocks = BlockScanner.getMatchingBlocks();
        if (blocks.isEmpty()) {
            extractedBoxes = List.of();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            extractedBoxes = List.of();
            return;
        }

        List<BoxState> boxes = new ArrayList<>(blocks.size());
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

            boxes.add(new BoxState(minX, minY, minZ, maxX, maxY, maxZ));
        }
        extractedBoxes = boxes;
    }

    /** Drawing-Phase: nur noch das extrahierte Render-State zeichnen. */
    public static void render(LevelRenderContext context) {
        if (extractedBoxes.isEmpty()) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        float[] color = config.getHighlightColor();

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f positionMatrix = matrices.last().pose();

        filledBuffer = new BufferBuilder(ALLOCATOR, FILLED_THROUGH_WALLS.getVertexFormatMode(), FILLED_THROUGH_WALLS.getVertexFormat());
        lineBuffer = new BufferBuilder(ALLOCATOR, LINES_THROUGH_WALLS.getVertexFormatMode(), LINES_THROUGH_WALLS.getVertexFormat());

        for (BoxState box : extractedBoxes) {
            addFilledBox(positionMatrix, filledBuffer, box, color[0], color[1], color[2], color[3]);
            addLineBox(positionMatrix, lineBuffer, box, color[0], color[1], color[2], 1.0f);
        }

        matrices.popPose();

        Minecraft client = Minecraft.getInstance();
        filledVertexBuffer = drawBuffer(client, FILLED_THROUGH_WALLS, filledBuffer, filledVertexBuffer);
        lineVertexBuffer = drawBuffer(client, LINES_THROUGH_WALLS, lineBuffer, lineVertexBuffer);
    }

    private static void addFilledBox(Matrix4fc m, BufferBuilder buffer, BoxState b, float r, float g, float bl, float a) {
        float minX = (float) b.minX(), minY = (float) b.minY(), minZ = (float) b.minZ();
        float maxX = (float) b.maxX(), maxY = (float) b.maxY(), maxZ = (float) b.maxZ();

        // Front
        buffer.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
        // Back
        buffer.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
        // Left
        buffer.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
        // Right
        buffer.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
        // Top
        buffer.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
        // Bottom
        buffer.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
        buffer.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
        buffer.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
    }

    /** 12 Kanten als LINES-Paare (2 Vertices pro Kante). */
    private static void addLineBox(Matrix4fc m, BufferBuilder buffer, BoxState b, float r, float g, float bl, float a) {
        float minX = (float) b.minX(), minY = (float) b.minY(), minZ = (float) b.minZ();
        float maxX = (float) b.maxX(), maxY = (float) b.maxY(), maxZ = (float) b.maxZ();

        float[][] edges = {
                {minX, minY, minZ, maxX, minY, minZ}, {maxX, minY, minZ, maxX, minY, maxZ},
                {maxX, minY, maxZ, minX, minY, maxZ}, {minX, minY, maxZ, minX, minY, minZ},
                {minX, maxY, minZ, maxX, maxY, minZ}, {maxX, maxY, minZ, maxX, maxY, maxZ},
                {maxX, maxY, maxZ, minX, maxY, maxZ}, {minX, maxY, maxZ, minX, maxY, minZ},
                {minX, minY, minZ, minX, maxY, minZ}, {maxX, minY, minZ, maxX, maxY, minZ},
                {maxX, minY, maxZ, maxX, maxY, maxZ}, {minX, minY, maxZ, minX, maxY, maxZ},
        };

        for (float[] e : edges) {
            buffer.addVertex(m, e[0], e[1], e[2]).setColor(r, g, bl, a);
            buffer.addVertex(m, e[3], e[4], e[5]).setColor(r, g, bl, a);
        }
    }

    private static MappableRingBuffer drawBuffer(Minecraft client, RenderPipeline pipeline, BufferBuilder buffer, MappableRingBuffer vertexBuffer) {
        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = new MappableRingBuffer(() -> "selected-block-highlighter render pipeline",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        GpuBuffer indices;
        VertexFormat.IndexType indexType;
        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "selected-block-highlighter rendering",
                        client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertexBuffer.currentBuffer());
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
        vertexBuffer.rotate();
        return vertexBuffer;
    }

    /** Wird vom GameRendererCleanupMixin beim Schliessen des Spiels aufgerufen. */
    public static void close() {
        ALLOCATOR.close();
        if (filledVertexBuffer != null) {
            filledVertexBuffer.close();
            filledVertexBuffer = null;
        }
        if (lineVertexBuffer != null) {
            lineVertexBuffer.close();
            lineVertexBuffer = null;
        }
    }
}
