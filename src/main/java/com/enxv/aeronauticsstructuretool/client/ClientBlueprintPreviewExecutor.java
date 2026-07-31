package com.enxv.aeronauticsstructuretool.client;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClientBlueprintPreviewExecutor {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "AST Blueprint Preview Loader");
        thread.setDaemon(true);
        return thread;
    });

    private ClientBlueprintPreviewExecutor() {
    }

    public static Executor executor() {
        return EXECUTOR;
    }
}
