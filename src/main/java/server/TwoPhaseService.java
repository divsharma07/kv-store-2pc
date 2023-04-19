package server;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import kvGrpc.TPCGrpc;
import kvGrpc.Twophase;
import util.LoggerUtil;

public class TwoPhaseService extends TPCGrpc.TPCImplBase {
  private Map<String, String> kvMap;
  private LockByKey lock;
  private final int port;

  public TwoPhaseService(LockByKey lock, Map<String, String> kvMap, int port) {
    this.kvMap = kvMap;
    this.lock = lock;
    this.port = port;
  }

  @Override
  public void abort(Twophase.AbortRequest request, StreamObserver<Twophase.AbortResponse> responseObserver) {
    LoggerUtil.writeLog(Level.INFO, "Abort message received from server: " + request.getOriginServer() +
            " for the key: " + request.getKey());
    lock.unlock(request.getKey());
  }

  @Override
  public void prepare(Twophase.PrepareRequest request, StreamObserver<Twophase.PrepareResponse> responseObserver) {
    String key = request.getKey();
    String value = "";

    lock.lock(key);
    if (Context.current().isCancelled()) {
      // if client call waits for too long and client cancels
      responseObserver.onError(Status.CANCELLED.withDescription("Cancelled by client").asRuntimeException());
      lock.unlock(key);
      return;
    }

      LoggerUtil.writeLog(Level.INFO, "Prepare message received from server: " + request.getOriginServer() +
              " for the key: " + request.getKey());
      Twophase.PrepareResponse.Builder responseBuilder = Twophase.PrepareResponse.newBuilder();
      Twophase.RequestType requestType = request.getRequestType();
      responseBuilder.setOriginServer(String.valueOf(port));
      if (requestType == Twophase.RequestType.PUT) {
        value = request.getValue();
        String oldValue = request.getOldValue();
        if (kvMap.get(key) == null || kvMap.get(key).equals(oldValue)) {
          responseBuilder.setMessage("Prepared for Commit").setSuccess(true);
        } else {
          responseBuilder.setMessage("Not Prepared. Please abort").setSuccess(false);
        }
      } else if (requestType == Twophase.RequestType.DELETE) {
        if (kvMap.get(key) != null) {
          responseBuilder.setMessage("Prepared for Commit").setSuccess(true);
        } else {
          responseBuilder.setMessage("Not Prepared. Please abort").setSuccess(false);
        }
      }
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
      // notice how lock on the key is not release just yet, but would be released when the transaction for a key
      // is either committed or aborted.
  }

  @Override
  public void commit(Twophase.CommitRequest request, StreamObserver<Twophase.CommitResponse> responseObserver) {
    String key = "";
    String value = "";
    try {
//      lock.lock(key);
      key = request.getKey();
      LoggerUtil.writeLog(Level.INFO, "Commit message received from server: " + request.getOriginServer() +
              " for the key: " + request.getKey());
      Twophase.CommitResponse.Builder responseBuilder = Twophase.CommitResponse.newBuilder();
      Twophase.RequestType requestType = request.getRequestType();
      responseBuilder.setOriginServer(String.valueOf(port));
      if (requestType == Twophase.RequestType.PUT) {
        LoggerUtil.writeLog(Level.INFO, "Commit message received from server: " + request.getOriginServer() +
                " to put the key: " + request.getKey());
        value = request.getValue();
        kvMap.put(key, value);
        LoggerUtil.writeLog(Level.INFO, "Commit complete, Key :" + request.getKey() + " has the value :" + value);
        responseBuilder.setMessage("Commit Successful").setSuccess(true);
      } else if (requestType == Twophase.RequestType.DELETE) {
        LoggerUtil.writeLog(Level.INFO, "Commit message received from server: " + request.getOriginServer() +
                " to delete the key: " + request.getKey());
        kvMap.remove(key);
        responseBuilder.setMessage("Commit Successful").setSuccess(true);
        LoggerUtil.writeLog(Level.INFO, "Commit complete, Key :" + request.getKey() + " deleted.");
      }
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    } finally {
      lock.unlock(key);
    }
  }
}
