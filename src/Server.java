import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) {
        int port = 26000;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            while (true) {
                Socket socketa = serverSocket.accept();
                System.out.println("Client A Connected");
                Socket socketb = serverSocket.accept();
                System.out.println("Client B Connected");

                new ServerThread(socketa, socketb).start();
            }
        } catch (Exception E){
            System.out.println("Exception: " + E.getMessage());
            E.printStackTrace();
        }
    }
}
