import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TURNServer {
    static final int numberOfChannels = 32; //each game with two players requires 8 channels (A->TS->Server | B->TS->Server | Server->TS->A | Server->TS->B). I leave it up to the turnserver host to make sure this variable is a multiple of 8.
    static Socket[] allocatedChannels = new Socket[numberOfChannels];
    static AtomicInteger nextChannel = new AtomicInteger(0);
    private static TURNServer turn;
    protected static int PORT = 49152;
    private ExecutorService executorService = Executors.newFixedThreadPool(numberOfChannels);
    public static void main(String[] args) {
        turn = new TURNServer();
        turn.runServer();
    }
    private void runServer(){
        try(ServerSocket turnServer = new ServerSocket(PORT)){
            System.out.println("TURN Server listening on port " + PORT);
            while(true) {
                try {
                    Socket s1 = turnServer.accept();
                    executorService.submit(new TURNServerThread(s1));
                } catch(IOException ex){
                    System.out.println("Error accepting connection");
                    ex.printStackTrace();
                }
            }
        }catch (Exception E){
            System.out.println("Exception: " + E.getMessage());
            E.printStackTrace();
        }
    }
}
class TURNServerThread implements Runnable {

    private Socket s;
    private Socket receiver;
    private BufferedReader reader;
    private OutputStream receiveOut;
    private PrintWriter receiveWriter;
    boolean terminate = false;
    private PrintWriter writer;
    AtomicBoolean allocated = new AtomicBoolean(false);


    public TURNServerThread(Socket s) {
        this.s = s;
    }

    private synchronized int findReceiverInChannels(InetAddress adr, int port){
        for (int i = 0; i < TURNServer.allocatedChannels.length; i++) {
            if(TURNServer.allocatedChannels[i] != null) {
                if (TURNServer.allocatedChannels[i].getInetAddress().equals(adr) && TURNServer.allocatedChannels[i].getPort() == port) {
                    return i;
                }
            }
        }
        return -1;
    }
    private synchronized Boolean receive(ChangeEvent e) throws IOException {
        if (!allocated.get()) {
            String response = (String) e.getSource();
            System.out.println(response);
            if (response.startsWith("allocate")) {
                if (TURNServer.nextChannel.get() < TURNServer.numberOfChannels - 2) {
                    TURNServer.allocatedChannels[TURNServer.nextChannel.get()] = s;
                    System.out.println(s + ": allocated to channel " + TURNServer.nextChannel.get());
                    TURNServer.nextChannel.set(TURNServer.nextChannel.get() + 1);
                    String[] responseArray = response.split("\\|"); //splits string by |
                    InetAddress recAddress = InetAddress.getByName(responseArray[1]);
                    int recPort = Integer.parseInt(responseArray[2].strip());
                    receiver = new Socket(recAddress, recPort);
                    receiveOut = receiver.getOutputStream();
                    receiveWriter = new PrintWriter(receiveOut, true);
                    receiveWriter.println(s.getInetAddress().getHostAddress() + "|" + s.getPort() + "|" + TURNServer.PORT);
                    TURNServer.allocatedChannels[TURNServer.nextChannel.get()] = receiver;
                    System.out.println(receiver + ": allocated to channel " + TURNServer.nextChannel.get());
                    TURNServer.nextChannel.set(TURNServer.nextChannel.get() + 1);
                    receiveWriter.println("Allocation Successful");
                    writer.println("Allocation Successful");
                    SocketListener sl2 = new SocketListener(receiver.getInputStream());
                    sl2.addChangeListener(se -> {
                        try {
                            serverMessage(se);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                    Thread s1 = new Thread(sl2);
                    s1.start();
                    allocated.set(true);
                } else {
                    writer.println("Allocation Failed");
                }
            }
        } else{
            String response = (String) e.getSource();
            System.out.println("Received: " +response);
            if(response.isEmpty()){
                return false;
            }
            receiveWriter.println(response);
            System.out.println("Sent to " + receiver.getPort() + ": " +response);
        }
        return true;
    }
    private synchronized void serverMessage(ChangeEvent e) throws IOException{
        String response = (String) e.getSource();
        System.out.println("Received from server: " +response);
        if(response.strip().isEmpty()){
            return;
        }
        writer.println(response);
        System.out.println("Server sent to " + s.getPort() + ": " +response);
    }

    public void run() {
        try {
            System.out.println(s + ": running");
            InputStream input = s.getInputStream();
            reader = new BufferedReader(new InputStreamReader(input));
            OutputStream output = s.getOutputStream();
            writer = new PrintWriter(output, true);
        } catch(Exception e){
            e.printStackTrace();
        }
        try{

            SocketListener sl1 = new SocketListener( s.getInputStream() );
            sl1.addChangeListener(e -> {
                try {
                    boolean success = receive(e);
                    if(!success){
                        terminate = true;
                        Thread.currentThread().interrupt();
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            Thread t1 = new Thread(sl1);
            t1.start();
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }
}

