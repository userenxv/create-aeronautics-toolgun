package com.enxv.aeronauticsstructuretool.verification;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

/** Test-only adapter: FML seals ILoadedConfig and exposes no in-memory factory. */
public final class InMemoryServerConfig {
    private InMemoryServerConfig() {}

    public static IConfigSpec.ILoadedConfig wrap(CommentedConfig config) throws ReflectiveOperationException {
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructor(CommentedConfig.class, java.nio.file.Path.class, ModConfig.class);
        constructor.setAccessible(true);
        return (IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null);
    }
}
