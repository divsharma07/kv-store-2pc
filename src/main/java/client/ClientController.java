package client;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import kvGrpc.KeyValueGrpc;
import util.LoggerUtil;

/**
 * Orchestrates the functionality of the client.
 */
public class ClientController {

  public static void main(String[] args) throws InterruptedException {
    String serverAddress = args[0];
    int port = Integer.parseInt(args[1]);
    boolean addSeedData = Boolean.parseBoolean(args[2]);
    ManagedChannel managedChannel = ManagedChannelBuilder.forAddress(serverAddress, port)
            .usePlaintext().build();
    // synchronous blocking stub is initiated
    KeyValueGrpc.KeyValueBlockingStub kvStub = KeyValueGrpc.newBlockingStub(managedChannel);
    LoggerUtil.writeLog(Level.INFO, "Client has connected to the server");
    KVClient client = new KVClient(kvStub);

    if(addSeedData) {
      seedKeyValueStore("seed1.txt", client);
    }
    while (true) {
      executeOperations(readOperations(new Scanner(System.in), true), client);
    }
  }

  private static void executeOperations(ClientInput input, KVClient client) {
    switch (input.getRequestType()) {
      case GET -> client.executeGet(input.getKey());
      case PUT -> client.executePut(input.getKey(), input.getValue());
      case DELETE -> client.executeDelete(input.getKey());
    }
  }

  private static void seedKeyValueStore(String fileName, KVClient client) {
    LoggerUtil.writeLog(Level.INFO, "Adding seed data");
    List<ClientInput> requestList = new ArrayList<>();
    String fileData = readDataFromFile(fileName);
    Scanner s = new Scanner(fileData);
    while (s.hasNext()) {
      requestList.add(readOperations(new Scanner(s.nextLine()), false));
    }
    for(ClientInput request : requestList) {
      executeOperations(request, client);
    }
  }

  private static String readDataFromFile(String fileName) {
    String fileContent = "";
    try {
      FileInputStream fis = new FileInputStream(fileName);
      byte[] data = new byte[fis.available()];
      fis.read(data);
      fis.close();
      fileContent = new String(data, StandardCharsets.UTF_8);
    } catch (FileNotFoundException e) {
      System.out.println("Cannot find the specified file");
      e.printStackTrace();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return fileContent;
  }

  private static ClientInput readOperations(Scanner s, boolean printToConsole) {
    if (printToConsole) {
      System.out.println("Choose Functionality, 1 for PUT, 2 for GET, 3 for DELETE");
    }
    int methodChoice = 0;
    while (true) {
      try {
        methodChoice = Integer.parseInt(s.next());
        if (methodChoice < 1 || methodChoice > 4) {
          if (printToConsole) {
            System.out.println("Enter a valid choice");
          }
          continue;
        }
        break;
      } catch (NumberFormatException e) {
        if (printToConsole) {
          System.out.println("Please enter a valid number for functionality choice");
        }
      }
    }

    String key = "";
    String value = "";
    String jsonString = null;
    ClientInput input;

    if (methodChoice == 4) {
      return null;
    }
    if (printToConsole) {
      System.out.println("Please add the key you want to input");
    }
    key = s.next().trim();
    if (methodChoice == 3 || methodChoice == 2) {
      if (methodChoice == 2) {
        input = new ClientInput(RequestType.GET, key);
      } else {
        input = new ClientInput(RequestType.DELETE, key);
      }
    } else {
      if (printToConsole) {
        System.out.println("Enter the value to be inserted for key:" + key);
      }
      value = s.next();
      input = new ClientInput(RequestType.PUT, key, value);
    }
    return input;
  }
}
