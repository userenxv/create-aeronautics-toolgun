package com.enxv.aeronauticsstructuretool.vehicle.container;

import java.util.List;

/** Keeps the archive inaccessible until every original structure has been removed. */
public final class ContainerCaptureCommit {
    private ContainerCaptureCommit() {
    }

    public static <T> void execute(List<T> originals, Store<T> store) throws Exception {
        store.prepare(List.copyOf(originals));
        for (T original : originals) {
            store.removeOriginal(original);
            store.confirmRemoved(original);
        }
        store.finish();
    }

    public interface Store<T> {
        void prepare(List<T> originals) throws Exception;
        void removeOriginal(T original) throws Exception;
        void confirmRemoved(T original) throws Exception;
        void finish() throws Exception;
    }
}
