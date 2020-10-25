package org.jetbrains.bsp.bazel.experimental;

import ch.epfl.scala.bsp4j.TaskFinishParams;
import ch.epfl.scala.bsp4j.TaskId;
import ch.epfl.scala.bsp4j.TaskStartParams;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.v1.BuildEvent;
import com.google.devtools.build.v1.PublishBuildEventGrpc;
import com.google.devtools.build.v1.PublishBuildToolEventStreamRequest;
import com.google.devtools.build.v1.PublishBuildToolEventStreamResponse;
import com.google.devtools.build.v1.PublishLifecycleEventRequest;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Stack;

public class BepListener extends PublishBuildEventGrpc.PublishBuildEventImplBase {
  private final Stack<TaskId> taskParkingLot = new Stack<>();

  @Override
  public void publishLifecycleEvent(
      PublishLifecycleEventRequest request, StreamObserver<Empty> responseObserver) {
    System.out.println(request);
    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public StreamObserver<PublishBuildToolEventStreamRequest> publishBuildToolEventStream(
      StreamObserver<PublishBuildToolEventStreamResponse> responseObserver) {
    return new StreamObserver<PublishBuildToolEventStreamRequest>() {

      @Override
      public void onNext(PublishBuildToolEventStreamRequest request) {
        if (request
            .getOrderedBuildEvent()
            .getEvent()
            .getBazelEvent()
            .getTypeUrl()
            .equals("type.googleapis.com/build_event_stream.BuildEvent")) {
          handleEvent(request.getOrderedBuildEvent().getEvent());
        } else {
          System.out.println("Got this request " + request);
        }
        PublishBuildToolEventStreamResponse response =
            PublishBuildToolEventStreamResponse.newBuilder()
                .setStreamId(request.getOrderedBuildEvent().getStreamId())
                .setSequenceNumber(request.getOrderedBuildEvent().getSequenceNumber())
                .build();
        responseObserver.onNext(response);
      }

      private void handleEvent(BuildEvent buildEvent) {
        try {
          BuildEventStreamProtos.BuildEvent event =
              BuildEventStreamProtos.BuildEvent.parseFrom(buildEvent.getBazelEvent().getValue());
          System.out.println("Got event" + event + "\nevent-end\n");
          if (event.hasStarted() && event.getStarted().getCommand().equals("build")) {
            BuildEventStreamProtos.BuildStarted buildStarted = event.getStarted();
            TaskId taskId = new TaskId(buildStarted.getUuid());
            TaskStartParams startParams = new TaskStartParams(taskId);
            startParams.setEventTime(buildStarted.getStartTimeMillis());
            System.out.println(startParams);
            taskParkingLot.add(taskId);
          }
          if (event.hasFinished()) {
            BuildEventStreamProtos.BuildFinished buildFinished = event.getFinished();
            if (taskParkingLot.size() == 0) {
              System.out.println("No start event id was found.");
              return;
            } else if (taskParkingLot.size() > 1) {
              System.out.println("More than 1 start event was found");
              return;
            }

            System.out.println("Finished: " + taskParkingLot.pop());
          }

//          if (event.getId().hasNamedSet()) {
//            System.out.println("named set" + event.getNamedSetOfFiles());
//          }
//          if (event.hasCompleted()) {
//            System.out.println("completed " + event.getCompleted());
//          }
//          if (event.hasAction()) {
//            System.out.println("action " + event.getAction());
//          }
//          if (event.hasAborted()) {
//            System.out.println("aborted " + event.getAborted());
//          }
//          if (event.hasProgress()) {
//            System.out.println("progress " + event.getProgress());
//          }

        } catch (IOException e) {
          System.err.println("Error deserializing BEP proto: " + e);
        }
      }

      @Override
      public void onError(Throwable throwable) {
        System.out.println("Error from BEP stream: " + throwable);
      }

      @Override
      public void onCompleted() {
        System.out.println("COMPLETED");

        responseObserver.onCompleted();
      }
    };
  }
}
