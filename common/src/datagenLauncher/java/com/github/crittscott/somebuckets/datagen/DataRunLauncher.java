package com.github.crittscott.somebuckets.datagen;

import java.lang.reflect.InvocationTargetException;

/** Runs the configured data launch chain and exits after it completes, releasing runtime workers. */
public final class DataRunLauncher {
    private DataRunLauncher() {}

    /** Reports launch failures with a nonzero exit status; successful generation exits with zero. */
    public static void main(String[] args) {
        try {
            String mainClass = System.getProperty("somebuckets.datagen.mainClass");
            Class.forName(mainClass).getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException failure) {
            failure.getCause().printStackTrace();
            System.exit(1);
            return;
        } catch (ReflectiveOperationException | RuntimeException | Error failure) {
            // Every launch failure, errors included, must exit nonzero: stray non-daemon threads
            // would otherwise keep the run from terminating.
            failure.printStackTrace();
            System.exit(1);
            return;
        }
        System.exit(0);
    }
}
