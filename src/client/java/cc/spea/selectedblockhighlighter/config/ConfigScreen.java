package cc.spea.selectedblockhighlighter.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Nur Text -> Component und class_437 -> Screen umbenannt, Cloth-Config-
 * eigene Klassen (ConfigBuilder etc.) sind unveraendert.
 * Getestet gegen Cloth Config 26.1.154+fabric (unterstuetzt 26.1-26.1.2).
 */
@Environment(EnvType.CLIENT)
public class ConfigScreen {
    public static Screen createConfigScreen(Screen parent) {
        ModConfig config = ModConfig.getInstance();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Selected Block Highlighter Config"));
        builder.setSavingRunnable(config::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        general.addEntry((AbstractConfigListEntry) entryBuilder.startBooleanToggle(Component.literal("Enabled"), config.enabled)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Toggle the block highlighter on/off"))
                .setSaveConsumer(newValue -> config.enabled = newValue)
                .build());

        general.addEntry((AbstractConfigListEntry) entryBuilder.startIntSlider(Component.literal("Scan Range"), config.scanRange, 1, 128)
                .setDefaultValue(32)
                .setTooltip(Component.literal("Maximum distance to scan for matching blocks"))
                .setSaveConsumer(newValue -> config.scanRange = newValue)
                .build());

        ConfigCategory appearance = builder.getOrCreateCategory(Component.literal("Appearance"));
        appearance.addEntry((AbstractConfigListEntry) entryBuilder.startFloatField(Component.literal("Red"), config.highlightRed)
                .setDefaultValue(1.0f).setMin(0.0f).setMax(1.0f)
                .setTooltip(Component.literal("Red color component (0.0 - 1.0)"))
                .setSaveConsumer(newValue -> config.highlightRed = newValue)
                .build());

        appearance.addEntry((AbstractConfigListEntry) entryBuilder.startFloatField(Component.literal("Green"), config.highlightGreen)
                .setDefaultValue(1.0f).setMin(0.0f).setMax(1.0f)
                .setTooltip(Component.literal("Green color component (0.0 - 1.0)"))
                .setSaveConsumer(newValue -> config.highlightGreen = newValue)
                .build());

        appearance.addEntry((AbstractConfigListEntry) entryBuilder.startFloatField(Component.literal("Blue"), config.highlightBlue)
                .setDefaultValue(0.0f).setMin(0.0f).setMax(1.0f)
                .setTooltip(Component.literal("Blue color component (0.0 - 1.0)"))
                .setSaveConsumer(newValue -> config.highlightBlue = newValue)
                .build());

        appearance.addEntry((AbstractConfigListEntry) entryBuilder.startFloatField(Component.literal("Alpha (Opacity)"), config.highlightAlpha)
                .setDefaultValue(0.4f).setMin(0.0f).setMax(1.0f)
                .setTooltip(Component.literal("Transparency (0.0 = invisible, 1.0 = opaque)"))
                .setSaveConsumer(newValue -> config.highlightAlpha = newValue)
                .build());

        appearance.addEntry((AbstractConfigListEntry) entryBuilder.startFloatField(Component.literal("Line Width"), config.lineWidth)
                .setDefaultValue(2.0f).setMin(0.5f).setMax(10.0f)
                .setTooltip(Component.literal("Thickness of the highlight outline"))
                .setSaveConsumer(newValue -> config.lineWidth = newValue)
                .build());

        appearance.addEntry((AbstractConfigListEntry) entryBuilder.startBooleanToggle(Component.literal("See Through Blocks"), config.seeThroughBlocks)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Show the outline even when hidden behind other blocks (X-ray style)"))
                .setSaveConsumer(newValue -> config.seeThroughBlocks = newValue)
                .build());

        return builder.build();
    }
}
