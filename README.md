# Create Aeronautics: Toolgun

A NeoForge 1.21.1 utility addon for building and handling Sable/Create Aeronautics vehicles.

## Features

- Save and print multi-sublevel vehicles.
- Move, rotate, weld, delete, and change collision between physical structures.
- Survival and creative magnetic guns.
- Portable printers and reusable or single-use vehicle containers.
- Preservation of supported constraints, contraptions, wires, ropes, and block-entity data.

## Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.228 or newer
- Create 6.0.10
- Sable 1.1.3–2.x
- Create Aeronautics 1.1.3–1.x

The current development target is Sable 2.0.3 and Create Aeronautics 1.3.0.

## Building

```powershell
.\gradlew.bat build
```

Gradle downloads the pinned dependencies and runs the primary and legacy compatibility checks automatically. The mod JAR is written to `build/libs/`.

## Contributing

Issues and pull requests are welcome. Include the Minecraft/mod versions, a minimal reproduction, and the relevant log when reporting a bug. Run the full build before submitting code changes.

## License

Source code and assets are licensed under CC BY-NC 4.0. Commercial use is not permitted. See [LICENSE](LICENSE).
