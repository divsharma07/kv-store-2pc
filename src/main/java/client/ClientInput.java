package client;

/**
 * Class that stores data that is accepted as input and required to make the RPC call.
 */
class ClientInput {
  private final RequestType requestType;
  private final String key;
  private String value;

  ClientInput(RequestType requestType, String key, String value) {
    this.requestType = requestType;
    this.key = key;
    this.value = value;
  }

  ClientInput(RequestType requestType, String key) {
    this.requestType = requestType;
    this.key = key;
  }

  public RequestType getRequestType() {
    return requestType;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }
}

enum RequestType {
  PUT, DELETE, GET
}