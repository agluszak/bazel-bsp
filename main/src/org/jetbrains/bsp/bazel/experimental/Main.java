package org.jetbrains.bsp.bazel.experimental;

import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Main {
  public static void consume(Process p) {
    int ev = p.exitValue();
    try {
      String output = new String(p.getInputStream().readAllBytes());
      System.out.println("Process exit with + " + ev + "\n" + output);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args)
      throws InterruptedException, IOException, ExecutionException {
//    ArrayList<CompletableFuture<Void>> list = new ArrayList<>();
//    for (int i = 0; i < 10; i++) {
//      list.add(ProcessTest.test().thenAcceptAsync(Main::consume));
//    }
//    CompletableFuture.allOf(list.toArray(new CompletableFuture[0]))
//        .thenRun(() -> System.out.println("all done")).get();

        io.grpc.Server bepServer =
            ServerBuilder.forPort(8888).addService(new BepListener()).build().start();
        System.out.println("Server started");
        bepServer.awaitTermination();
  }
}
