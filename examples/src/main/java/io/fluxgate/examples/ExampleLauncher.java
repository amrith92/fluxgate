package io.fluxgate.examples;

/**
 * Small dispatcher that lets `./gradlew :examples:run --args="..."` pick a sample.
 */
public final class ExampleLauncher {

    private ExampleLauncher() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || "server".equalsIgnoreCase(args[0])) {
            DemoServer.main(args);
            return;
        }
        if ("resilience4j".equalsIgnoreCase(args[0])) {
            Resilience4jAdapterExample.main(args);
            return;
        }
        throw new IllegalArgumentException("Unknown example: " + args[0]);
    }
}
