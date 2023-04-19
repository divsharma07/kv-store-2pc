package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import kvGrpc.TPCGrpc;
import util.LoggerUtil;

/**
 * Orchestrates the functionality of the Server. Discovers other servers and saves their instance to call them
 * remotely for the Two Phase Commit.
 */
public class ServerController {
  private Server grpcServer;
  private ManagedChannel channel;

  private static final Logger logger = Logger.getLogger(ServerController.class.getName());

  private static final long DURATION_SECONDS = 60;

  private static final String serverFilePath = "liveServers.txt";

  public static void main(String[] args) throws IOException, InterruptedException {
    ServerController controller = new ServerController();
    int port = Integer.parseInt(args[0]);
    List<ServerInstance> otherServers = discoverOtherServers(port, "localhost");
    controller.grpcStart(port, otherServers);
  }

  private static void writeToFile(List<ServerInstance> servers) throws IOException {
    // deleting all current contents
    new FileWriter(serverFilePath, false).close();
    FileWriter fw = getFileWriter(serverFilePath);
    BufferedWriter writer = new BufferedWriter(fw);
    for(ServerInstance each: servers) {
      writer.write(String.format("%s:%d\n", each.getAddress(),each.getPort()));
    }
  }

  private static FileWriter getFileWriter(String serverFilePath) throws IOException {
    File file= new File (new File("").getAbsolutePath() + File.separator + serverFilePath);
    FileWriter fw;
    if (file.exists())
    {
      fw = new FileWriter(file,true);
    }
    else
    {
      file.createNewFile();
      fw = new FileWriter(file);
    }
    return fw;
  }

  private static List<ServerInstance> discoverOtherServers(int currServerPort, String currServerAddr) throws IOException {
    List<ServerInstance> servers = new ArrayList<>();
    BufferedReader reader = new BufferedReader(new FileReader(serverFilePath));
    String line = "";
    while((line = reader.readLine()) != null) {
      String[] split = line.split(":");
      // keeping localhost as default
      String addr = split[0];
      int port = Integer.parseInt(split[1]);
      if(port == currServerPort && addr.equals(currServerAddr)) continue;
      ManagedChannel managedChannel = ManagedChannelBuilder.forAddress(addr, port)
              .usePlaintext().build();
      // synchronous blocking stub is initiated, and this gets used throughout the application
      TPCGrpc.TPCBlockingStub stub = TPCGrpc.newBlockingStub(managedChannel);
      servers.add(new ServerInstance(port, addr, stub));
    }
    return servers;
  }

  private void grpcStart(int port, List<ServerInstance> otherServers) throws IOException, InterruptedException {
    if(grpcServer != null) {
      throw new IllegalStateException("The server is already running");
    }
    Map<String, String> kvMap = new ConcurrentHashMap<>();
    LockByKey lock = new LockByKey();
    grpcServer = ServerBuilder.forPort(port).addService(new KVService(lock, otherServers, kvMap, port))
            .addService(new TwoPhaseService(lock, kvMap, port))
            .build();

    grpcServer.start();
    LoggerUtil.writeLog(Level.INFO, "Server has started");
    grpcServer.awaitTermination();
  }

}
