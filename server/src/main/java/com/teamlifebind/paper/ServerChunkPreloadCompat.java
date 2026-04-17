package com.teamlifebind.paper;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.World;

final class ServerChunkPreloadCompat {

    private final Logger logger;
    private boolean resolved;
    private boolean available;
    private Method getChunkAtAsyncMethod;

    ServerChunkPreloadCompat(Logger logger) {
        this.logger = logger;
    }

    boolean isAsyncAvailable() {
        return resolveAsyncSupport();
    }

    CompletableFuture<Void> preloadChunk(World world, int chunkX, int chunkZ, boolean generate) {
        if (world == null || !resolveAsyncSupport()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            Object result = getChunkAtAsyncMethod.invoke(world, chunkX, chunkZ, generate);
            if (result instanceof CompletableFuture<?> future) {
                return future.thenApply(ignored -> null);
            }
            return CompletableFuture.completedFuture(null);
        } catch (ReflectiveOperationException ex) {
            disableAfterFailure("Paper async chunk preload is unavailable", ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    private boolean resolveAsyncSupport() {
        if (resolved) {
            return available;
        }
        resolved = true;
        try {
            getChunkAtAsyncMethod = World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            available = true;
        } catch (ReflectiveOperationException ex) {
            available = false;
            logger.info("Async chunk preload API is unavailable on this server; falling back to throttled sync chunk loading.");
        }
        return available;
    }

    private void disableAfterFailure(String message, ReflectiveOperationException ex) {
        if (!available) {
            return;
        }
        available = false;
        logger.warning(message + ": " + ex.getMessage());
    }
}
