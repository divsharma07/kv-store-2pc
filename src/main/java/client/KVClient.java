package client;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import kvGrpc.KeyValueGrpc;
import kvGrpc.Keyvalue;
import util.LoggerUtil;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Class that makes provides the functionality of calling the RPC methods.
 */
public class KVClient {

  private KeyValueGrpc.KeyValueBlockingStub kvStub;

  public KVClient(KeyValueGrpc.KeyValueBlockingStub kvStub) {
    this.kvStub = kvStub;
  }

  public String executeGet(String key) {
    Keyvalue.GetRequest getRequest = Keyvalue.GetRequest.newBuilder().setKey(key).build();

    Keyvalue.GetResponse response = kvStub.withDeadlineAfter(5, TimeUnit.SECONDS).get(getRequest);
    if (response.getResponseCode().equals(Status.OK.toString())) {
      LoggerUtil.writeLog(Level.INFO, response.getResponseMessage());
      return response.getValue();
    } else {
      LoggerUtil.writeLog(Level.SEVERE, response.getResponseMessage());
    }
    return "";
  }

  public void executePut(String key, String value) {
    Keyvalue.PutRequest putRequest = Keyvalue.PutRequest.newBuilder().setKey(key)
            .setValue(value).build();

    Keyvalue.PutResponse response = kvStub.withDeadlineAfter(5, TimeUnit.SECONDS).put(putRequest);
    if (!response.getResponseCode().equals(Status.OK.toString())) {
      LoggerUtil.writeLog(Level.SEVERE, response.getResponseMessage());
    } else {
      LoggerUtil.writeLog(Level.INFO, response.getResponseMessage());
    }
  }

  public void executeDelete(String key) {
    Keyvalue.DeleteRequest deleteRequest = Keyvalue.DeleteRequest.newBuilder().setKey(key).build();

    Keyvalue.DeleteResponse response = kvStub.withDeadlineAfter(5, TimeUnit.SECONDS).delete(deleteRequest);
    if (!response.getResponseCode().equals(Status.OK.toString())) {
      LoggerUtil.writeLog(Level.SEVERE, response.getResponseMessage());
    } else {
      LoggerUtil.writeLog(Level.INFO, response.getResponseMessage());
    }
  }
}
