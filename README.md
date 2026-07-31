# Selected Block Highlighter — Port auf Minecraft 26.1.2

Inoffizieller Port von "Selected Block Highlighter" by spea (Original:
https://modrinth.com/mod/selected-block-highlighter, CC0-1.0) von
Minecraft 1.20.1 auf 26.1.2 (Fabric).

## Was funktioniert, was ist unsicher

Sicher (kaum geändert, nur Mojang-Mapping-Umbenennung):
- `SelectedBlockHighlighter` / `SelectedBlockHighlighterClient`
- `BlockScanner` (Scan-Logik unverändert)
- `KeyBindings` (neue KeyMapping/KeyMapping.Category-API, nach
  https://docs.fabricmc.net/26.1.2/develop/key-mappings)
- `ModConfig`, `ConfigScreen`, `ModMenuIntegration`

Komplett neu geschrieben, NICHT kompiliert/getestet:
- `BlockHighlightRenderer` — wegen des Rendering-Pipeline-Umbaus
  (RenderPipeline/RenderState-System) musste die komplette
  Zeichenlogik neu aufgebaut werden, nach dem offiziellen
  Beispiel für 26.1.2:
  https://docs.fabricmc.net/26.1.2/develop/rendering/world
- `GameRendererCleanupMixin` — neu, weil die neue Pipeline-API eigene
  GPU-Puffer verwaltet, die beim Schließen des Spiels freigegeben
  werden müssen.

## Bekannte offene Punkte (bitte vor dem ersten Build prüfen)

1. **`gradle.properties`**: `loader_version` und `fabric_version` sind
   als `REPLACE_ME` markiert. Auf https://fabricmc.net/develop/ die
   Minecraft-Version 26.1.2 auswählen und die dort angezeigten
   aktuellen Werte eintragen.
2. **`BlockHighlightRenderer.java`**: die Konstante
   `RenderPipelines.DEBUG_LINE_STRIP` als Basis für die Umriss-Linien
   ist nicht verifiziert. Im IDE `RenderPipelines.` tippen und mit
   Autovervollständigung die passende DEBUG_*-Konstante für Linien
   suchen (Vertex-Format-Modus sollte `LINES` oder `DEBUG_LINES`
   liefern), ggf. Namen anpassen.
3. Die konfigurierbare Linienbreite (`config.getLineWidth()`) wird
   aktuell **nicht mehr angewendet** — das alte `RenderSystem.lineWidth()`
   existiert so nicht mehr, und DEBUG_LINES ist laut Doku fest auf
   1 Pixel. Kann bei Bedarf später mit einer eigenen Custom-Pipeline
   nachgerüstet werden.
4. `BlockState#getShape(Level, BlockPos)` und `FluidState#isSource()`
   sind nach bestem Wissen benannt, aber nicht gegen die echte 26.1.2-
   API geprüft.

## Bauen

```
./gradlew build
```

Am einfachsten: Projekt mit dem Fabric Template Generator
(https://fabricmc.net/develop/template/) für 26.1.2 neu generieren
und dann nur die Java-Dateien aus `src/` sowie `fabric.mod.json`
und die lang-Datei aus diesem Ordner in das generierte Projekt
kopieren — dann stimmen `gradle.properties` und `build.gradle`
garantiert.

Bei Build-Fehlern: Fehlermeldung (Klassenname + Methode) hier posten,
dann korrigiere ich die betroffene Stelle gezielt.
