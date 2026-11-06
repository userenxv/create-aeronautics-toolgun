# Create Aeronautics: Toolgun

A NeoForge 1.21.1 utility addon for building and handling Sable/Create Aeronautics vehicles.

## Features

A. Save and print multi-sublevel vehicles.  
B. Move, rotate, weld, delete, and change collision between physical structures.  
C. Survival and creative magnetic guns.  
D. Portable printers and reusable or single-use vehicle containers.  
E. Preservation of supported constraints, contraptions, wires, ropes, and block-entity data.  

## Requirements

A. Java 21  
B. Minecraft 1.21.1  
C. NeoForge 21.1.228 or newer  
D. Create 6.0.10  
E. Sable 1.1.3–2.x  
F. Create Aeronautics 1.1.3–1.x  

The current development target is Sable 2.0.3 and Create Aeronautics 1.3.0.

## Building

```powershell
.\gradlew.bat build
```

Gradle downloads the pinned dependencies and runs the primary and legacy compatibility checks automatically. The mod JAR is written to `build/libs/`.

## Server configuration

In `config/create_aeronautics_toolgun-server.toml`, `[survivalMagneticGun]` exposes `linearMaxForce` (default `5500.0`) and `angularMaxForce` (default `24000.0`). Lower values weaken the survival magnetic gun's grabbing and turning motors. These settings do not affect the creative magnetic gun or launch impulses.

In `config/create_aeronautics_toolgun-container.toml`, `[containerCapture]` includes `limitCaptureBlocks = false` and `maxCaptureBlocks = 4096`. Enable the limit to restrict reusable and single-use containers, including when used in creative mode. The count includes connected physical bodies and supported moving contraptions, excludes inventory contents, and accepts vehicles exactly at the limit. Vehicles already stored can still be released. Both singleplayer and dedicated servers enforce their own server configuration.

## Contributing

Issues and pull requests are welcome. Include the Minecraft/mod versions, a minimal reproduction, and the relevant log when reporting a bug. Run the full build before submitting code changes.

## Credits

- [Arthur](https://github.com/ArthurSimin) — mod icon.
- [Okroshka (vety010)](https://github.com/vety010) — fix for saving oversized item stacks ([#7](https://github.com/userenxv/create-aeronautics-toolgun/pull/7)).

## License

Source code and assets are licensed under CC BY-NC 4.0. Commercial use is not permitted. See [LICENSE](LICENSE).
