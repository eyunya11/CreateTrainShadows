# Create: Train Shadows

English | [日本語](README.ja.md)

A client-side NeoForge addon that draws a shadow under every Create train carriage (`CarriageContraptionEntity`), in the shape of the carriage's footprint instead of vanilla's round blob. Create itself is not modified.

Create's trains have no shadow of their own; this addon adds one.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x (`[21,)`) |
| Create | 6.0.8 or later 6.x (`[6.0.8,7)`) |
| Flywheel | 1.x (the one bundled with Create works) |

Client side only. It does not need to be installed on a server.

## Features

- Draws the shadow on the ground in the shape of the carriage's footprint, following its heading and its tilt on slopes.
- The edge of the shadow fades out gradually.
- A block that hangs under the train also gets the shadow, and so does the floor under that block.
- The sides of walls next to a train (tunnel walls, platform edges, steps in the ground) are darkened too, more the closer they are.
- Shadows lie on Create's track as well, slopes and curves included. The faces of the track's own model are drawn again, black and translucent, so the rails and sleepers take the shadow.
- Nothing is drawn when "Entity Shadows" is off in the video settings.
- Nothing is drawn while a shader pack is in use: the pack shades the world itself, trains included.

## Configuration

Open Mods → Create: Train Shadows → Config. Changes apply at once. The file is `config/create_train_shadows-client.toml`.

| Option | What it does |
|---|---|
| `enabled` | Draw the shadows or not |
| `opacity` | How dark the shadow is where it is darkest (vanilla uses 0.5) |
| `fadeDistance` | Camera distance at which the shadow has faded out completely |
| `fadeHeight` | How many blocks it takes for the shadow to fade out when the train is high above the ground |
| `graceHeight` | Height that still counts as touching the ground (Create hangs a train one block above its track) |
| `margin` | How far the shadow reaches past the outline of the train |
| `softness` | Width of the soft band along the edge of the shadow |
| `sideReach` / `sideOpacity` | How far wall shadows reach and how dark they are (`sideReach = 0` draws none) |
| `trackShadowMode` | `MODEL` (shade the track's own model), `PLATE` (a translucent plate at rail height, cheaper) or `OFF` |

## Building

Java 21 is required.

```
./gradlew build
```

The jar is written to `build/libs/` as `create_train_shadows-neoforge-1.21.1-<version>.jar`. `./gradlew runClient` starts a development client; the Create version it runs against can be chosen, for example with `-Pcreate_version_runtime=6.0.8-169` (compilation is always against 6.0.8).

Tests run with `./gradlew test`. The shadow maths lives in the `core` package, which uses no Minecraft types, so it is covered by unit tests.

## Branches

There is one branch per Minecraft version and loader, named `mc<version>-<loader>`.

- `mc1.21.1-neoforge`: this branch
- `mc1.20.1-forge`: planned

## License

[MIT License](LICENSE.md)
