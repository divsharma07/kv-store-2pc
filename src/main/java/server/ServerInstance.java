package server;

import kvGrpc.KeyValueGrpc;
import kvGrpc.TPCGrpc;

/**
 * Represents each server instance and stores a gRPC client stub that is reused in the application
 * for server-server communication.
 */
public class ServerInstance {
  private int port;
  private String address;

  private TPCGrpc.TPCBlockingStub stub;

  public ServerInstance(int port, String address, TPCGrpc.TPCBlockingStub stub) {
    this.port = port;
    this.address = address;
    this.stub = stub;
  }

  public TPCGrpc.TPCBlockingStub getStub() {
    return stub;
  }

  public int getPort() {
    return port;
  }

  public String getAddress() {
    return address;
  }
}
