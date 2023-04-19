## Project 3
### About Project
This project involves implementation of a Key Value store, that constitutes a multithreaded server that 
exposes PUT, GET and DELETE functionalities using RPC. The server also provides consistency guarantees while being replicable across multiple instances.
The key to being able to do that is using Two Phase Commit Protocol. More about this in the assignment overview.

### How to build the project?
The project uses Maven to orchestrate its building process. The project can be either built using an IDE like Intellij IDEA which provides Maven extensions.

If Maven is installed and is in the local path, we can use this command to create the jar and proto target files again:

mvn install 

or

mvn clean install

Both of the processes above lead to 2 jar files as output in the target/ folder. Jars are names server.jar and client.jar. The proto file in the src/java/proto folder is also build by a maven plugin.

### How to run the project?
After building and getting the jars, run the following commands:


#### Server Command
java -jar ./server.jar <portNumber>

Run the following command in separate terminals. Make sure to stick with the port numbers:

`java -jar ./server.jar 12345`
`java -jar ./server.jar 12346`
`java -jar ./server.jar 12347`
`java -jar ./server.jar 12348`
`java -jar ./server.jar 12349`

Where port number is the port on which the server should bind to.

** Note: Make sure that the liveServers.txt file is at the same location as the jars. This contains all the servers that need to be started up before the system is up and running
If any of these servers is down, PUT and DELETE wont work. Also if one of them goes down after the first request has been made, its best to restart all of them, because all proceeding PUTs and DELETEs will fail.**

#### Client Command
java -jar ./client.jar <serverAddress> <portNumber> <seedData>

Where the arguments mean the following:
1. serverAddress: The address the server is running on. This would always be localhost in our implementation since we are not hosting the server remotely.
2. portNumber: The port number that the server is running on.
3. seedData: This is a boolean value, that decides if the client uses the seed1.txt file to seed the data.

**Note: Make sure that the seed1.txt file, that contains the seed data is added to the same location as the jars. This file contains the seed data**

### Making requests via the client
There are two ways we can interact with the client.
1. Add more lines of instructions to the seed1.txt file and recompile. Take care of the pattern that is used in the rest of the commands. Convention goes like, 1 is used for PUT, 2 for GET and 3 for Delete.
2. Use the console to send new requests to the server. This does not require recompiling.

Note: The client keeps on running till the program is interrupted and keeps on expecting more requests. Same goes for the server.

### Executive Summary
#### Assignment Overview
The assignment has bolstered my understanding of the following concepts:
1. A deeper understanding of how RPC works and choosing amongst the myriad implementations. Eventually for this project, gRPC was chosen partially because of the fact that I have previously used it but also because it has
many advantages over the other options, for example it has proto file implementations across multiple programming languages and also provides better performance.
2. The main focus of the assignment is actually the implementation of Two Phase commit protocol, and it made me think of ways in which we could achieve it the core goals:
    * All servers have consistent data at each time.
    * If some servers don't have consistent data at any given time, they make the request wait till they do.
    * PUT and DELETE need to go through the two phase protocol. GET stays locked if the key that needs to be retrieved is one which is going through Two Phase commit.
3. The multi threading approach for this assignment is a bit more granular than the last. Instead of locking each of the RPC method as a whole, a new class called LockByKey is created which uses Semaphores to lock each of the key that is going through a change. This is how the whole process looks like:
    * A new proto file is defined which sets the contract for the Two Phase commit protocol.
    * Each of the server that received an initial request to PUT/GET/DELETE becomes the coordinator of the Two Phase commit.
    * The coordinator sends a prepare message to all other servers, if all agree that they are prepared, we proceed to committing.
    * Even if one of the servers fails to prepare we abort.
    * Once a Prepare RPC call is made, PUT, DELETE and GET, get locked for that key. This lock only gets revoked when the commit or abort message is received. NB: We are assuming coordinator or any server infact, does not go down, else we will have a deadlock situation.
    * Similarly if a PUT/DELETE/GET call is made on a server for a particular key, prepare call for the same key needs to wait till the PUT/DELETE/GET are complete.
    * The approach is quite performant because of the per key locking mechanism, which will not call delays as number of requests increase.
    * ConcurrentHashMap is used to further make sure that multiple threads are not editing the KVMap.
    * The lock that is implemented by the LockByKey class is kept common between the KVService and TwoPhaseService, to make sure that the same key is being locked while it is either directly accessed by a client or is updated by a two phase commit.
4. There are timeouts involved both in client calls as well server to server two phase calls. Client call waits till 5 seconds before terminating. Calls amongst servers time out after 3 seconds.


#### Technical Impression
This was an interesting project which made me think more about how we could make a key value service, which coordinates and keeps all nodes consistent. There are indeed many ways to do this. I eventually picked the method of locking which makes the whole system consistent yet performant.
Locking by each key is a way in which we only make threads wait if they will actually change the same key, which is like an pseudo optimisitic approach in the sense it does not force locks on parts of the system that will not have inconsistencies. Using semaphores was also an interesting 
decision because this allows me to granularly maintain which key gets accessed and when to lock or unlock it. To be able to lock when prepare starts and only release when committed or aborted was also important to make sure there aren't situations where we prepare for a new commit before committing a previous command
Basically the project made me think deeply about the edge cases that arise when multiple clients send requests to the same server, or multiple clients send requests to the multiple server. The model that is finally used for concurrency makes the best use of the cached thread pool that gRPC provides, while also avoiding an additional coordinator that orchestrates the whole transaction. 
This is indeed a performant and cost-effective system. We could extend this using persistence which would get rid of some of the flaws that we see currently, namely, if a server crashes we lose all memory. 
We could also avoid deadlock caused by coordinator failure by having a timer that counts how long a lock is being held for and deleting it after a certain amount of time. 
