package server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import kvGrpc.KeyValueGrpc;
import kvGrpc.Keyvalue;
import kvGrpc.Twophase;
import util.LoggerUtil;


/**
 * A thread safe grpc based implementation. LockByKey takes care of the thread safety since only
 * one client is allowed to deal with a particular key at a time.
 */
public class KVService extends KeyValueGrpc.KeyValueImplBase {

  private final Map<String, String> kvMap;
  private final LockByKey lock;
  private final List<ServerInstance> otherServers;

  private final int port;

  public KVService(LockByKey lock, List<ServerInstance> otherServers, Map<String, String> kvMap, int port) {
    super();
    this.lock = lock;
    this.otherServers = otherServers;
    this.kvMap = kvMap;
    this.port = port;
  }

  @Override
  public void put(Keyvalue.PutRequest request, StreamObserver<Keyvalue.PutResponse> responseObserver) {
    String key = request.getKey();
    String value = request.getValue();

    try {
      lock.lock(key);
      Keyvalue.PutResponse.Builder responseBuilder = Keyvalue.PutResponse.newBuilder();
      boolean successful = prepareAndCommit(responseBuilder, responseObserver,
              key, value);
      if (successful) {
        boolean commitSucceeded = sendCommitRequests(key, value, Twophase.RequestType.PUT);
        if (!commitSucceeded) {
          sendPeerServerDownMessage(responseBuilder, responseObserver);
          return;
        }

        if (kvMap.containsKey(key)) {
          responseBuilder.setResponseCode(Status.OK.toString());
          responseBuilder.setResponseMessage("Key exists, updated its value to " + value);
          LoggerUtil.writeLog(Level.INFO, "Key " + key + " updated to contain value: " + value);
        } else {
          responseBuilder.setResponseCode(Status.OK.toString());
          responseBuilder.setResponseMessage("Key " + key + " added and contains value: " + value);
          LoggerUtil.writeLog(Level.INFO, "Key " + key + " added and contains value: " + value);
        }
        // key gets added or updated
        kvMap.put(key, value);
        // sends value to client
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
      }
    } finally {
      lock.unlock(key);
    }
  }

  private boolean prepareAndCommit(Keyvalue.PutResponse.Builder responseBuilder,
                                   StreamObserver<Keyvalue.PutResponse> responseObserver,
                                   String key, String value) {
    List<Twophase.PrepareResponse> prepareResponses = sendPrepareRequests(key, value, Twophase.RequestType.PUT);

    if (prepareResponses.size() != otherServers.size()) {
      sendPeerServerDownMessage(responseBuilder, responseObserver);
      return false;
    }
    if (!allResponsesPositive(prepareResponses)) {
      boolean abortSucceeded = sendAbortRequests(key);
      if (!abortSucceeded) {
        sendPeerServerDownMessage(responseBuilder, responseObserver);
        return false;
      }
    }

    return true;
  }

  private boolean prepareAndCommit(Keyvalue.DeleteResponse.Builder responseBuilder,
                                   StreamObserver<Keyvalue.DeleteResponse> responseObserver,
                                   String key, String value) {
    List<Twophase.PrepareResponse> prepareResponses = sendPrepareRequests(key, value, Twophase.RequestType.DELETE);

    if (prepareResponses.size() != otherServers.size()) {
      sendPeerServerDownMessage(responseBuilder, responseObserver);
      return false;
    }
    if (!allResponsesPositive(prepareResponses)) {
      boolean abortSucceeded = sendAbortRequests(key);
      if (!abortSucceeded) {
        sendPeerServerDownMessage(responseBuilder, responseObserver);
        return false;
      }
    }
    return true;
  }

  // sending commit requests to server.
  // Returns a true value if all servers received the message
  private boolean sendCommitRequests(String key, String value, Twophase.RequestType requestType) {
    for (ServerInstance server : otherServers) {
      try {
        Twophase.CommitRequest request = Twophase.CommitRequest.newBuilder().setKey(key)
                .setValue(value).setOriginServer(String.valueOf(port))
                .setRequestTypeValue(requestType.getNumber()).build();
        server.getStub().withDeadlineAfter(3, TimeUnit.SECONDS).commit(request);
        LoggerUtil.writeLog(Level.INFO, "Commit message sent to server: " + server.getPort() +
                " for the key: " + key);
      } catch (StatusRuntimeException e) {
        LoggerUtil.writeLog(Level.SEVERE, "One of the server seems to be down, " +
                "please restart all servers otherwise all proceeding calls will fail since KV values are in memory.");
        return false;
      }
    }

    return true;
  }

  private void sendPeerServerDownMessage(Keyvalue.PutResponse.Builder responseBuilder, StreamObserver<Keyvalue.PutResponse> responseObserver) {
    responseBuilder.setResponseCode(Status.ABORTED.toString());
    responseBuilder.setResponseMessage("One of the servers is down. We are working on the fix. Degraded functionality. Only GET will work.");
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  private void sendPeerServerDownMessage(Keyvalue.DeleteResponse.Builder responseBuilder,
                                         StreamObserver<Keyvalue.DeleteResponse> responseObserver) {
    responseBuilder.setResponseCode(Status.ABORTED.toString());
    responseBuilder.setResponseMessage("At least one of the servers is down. " +
            "We are working on a fix. Degraded functionality. Only GET will work.");
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  // sending abort requests to server.
  // Returns a true value if all servers received the message
  private boolean sendAbortRequests(String key) {
    for (ServerInstance server : otherServers) {
      try {
        Twophase.AbortRequest request = Twophase.AbortRequest.newBuilder().setKey(key)
                .setOriginServer(String.valueOf(port)).build();
        server.getStub().withDeadlineAfter(3, TimeUnit.SECONDS).abort(request);
        LoggerUtil.writeLog(Level.INFO, "Abort message sent to server: " + server.getPort() +
                " for the key: " + key);
      } catch (StatusRuntimeException e) {
        LoggerUtil.writeLog(Level.SEVERE, "One of the server seems to be down, " +
                "please restart all servers otherwise all proceeding calls will fail since KV values are in memory.");
        return false;
      }
    }
    return true;
  }

  private boolean allResponsesPositive(List<Twophase.PrepareResponse> prepareResponses) {
    for (Twophase.PrepareResponse response : prepareResponses) {
      if (!response.getSuccess()) return false;
    }
    return true;
  }

  private List<Twophase.PrepareResponse> sendPrepareRequests(String key, String value,
                                                             Twophase.RequestType requestType) {
    List<Twophase.PrepareResponse> responses = new ArrayList<>();
    String oldKeyValue = "";
    if (kvMap.containsKey(key)) {
      oldKeyValue = kvMap.get(key);
    }
    for (ServerInstance server : otherServers) {
      try {
        Twophase.PrepareRequest request = Twophase.PrepareRequest.newBuilder().setKey(key)
                .setValue(value).setOriginServer(String.valueOf(port)).setOldValue(oldKeyValue)
                .setRequestTypeValue(requestType.getNumber()).build();
        responses.add(server.getStub().withDeadlineAfter(3, TimeUnit.SECONDS).prepare(request));
        LoggerUtil.writeLog(Level.INFO, "Prepare message sent to server: " + server.getPort() +
                " for the key: " + key);
      } catch (StatusRuntimeException e) {
        LoggerUtil.writeLog(Level.SEVERE, "One of the server seems to be down, " +
                "please restart all servers otherwise all proceeding calls will fail since KV values are in memory.");
      }
    }
    return responses;
  }

  @Override
  public void get(Keyvalue.GetRequest request, StreamObserver<Keyvalue.GetResponse> responseObserver) {
    String key = request.getKey();
    String value = kvMap.get(key);

    try {
      // this lock makes sure if a particular key is on going a two phase commit, the incoming request waits till the value
      // gets updated
      lock.lock(key);
      Keyvalue.GetResponse.Builder responseBuilder = Keyvalue.GetResponse.newBuilder();
      if (value != null) {
        responseBuilder.setResponseCode(Status.OK.toString());
        responseBuilder.setValue(value);
        responseBuilder.setResponseMessage("The value of key: " + key + " fetched is value: " + value);
        LoggerUtil.writeLog(Level.INFO, "The value of key: " + key + " fetched is value: " + value);
      } else {
        responseBuilder.setResponseCode(Status.NOT_FOUND.toString());
        responseBuilder.setResponseMessage("Key " + key + " not found");
        LoggerUtil.writeLog(Level.SEVERE, "Client tried to fetch key: " + key + " but it was not found");
      }
      // sends value to client
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    } finally {
      lock.unlock(key);
    }
  }

  @Override
  public void delete(Keyvalue.DeleteRequest request, StreamObserver<Keyvalue.DeleteResponse> responseObserver) {
    String key = request.getKey();
    String value = kvMap.get(key);

    try {
      lock.lock(key);
      Keyvalue.DeleteResponse.Builder responseBuilder = Keyvalue.DeleteResponse.newBuilder();

      if (value == null) {
        responseBuilder.setResponseCode(Status.NOT_FOUND.toString());
        responseBuilder.setResponseMessage("Key " + key + " not found");
        LoggerUtil.writeLog(Level.SEVERE, "Client tried to remove key: " + key + " but it was not found");
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
        return;
      }
      boolean successful = prepareAndCommit(responseBuilder, responseObserver, key, value);
      if (successful) {
        sendCommitRequests(key, "", Twophase.RequestType.DELETE);
        kvMap.remove(key);
        responseBuilder.setResponseCode(Status.OK.toString());
        responseBuilder.setResponseMessage("Key " + key + " deleted");
        LoggerUtil.writeLog(Level.INFO, "Client removed the key: " + key);
      }
      // sends value to client
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    } finally {
      lock.unlock(key);
    }
  }


}
