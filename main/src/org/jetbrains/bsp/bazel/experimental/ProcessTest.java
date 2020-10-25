package org.jetbrains.bsp.bazel.experimental;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class ProcessTest {
  public static CompletableFuture<Process> test() throws IOException {
    int rand = ThreadLocalRandom.current().nextInt(2, 10);
    ProcessBuilder builder = new ProcessBuilder("bash", "-c", "printf " + String.valueOf(rand) + "; sleep " + String.valueOf(rand) + "; printf elko");
    Process process = builder.start();
    return process.onExit();
  }
}
