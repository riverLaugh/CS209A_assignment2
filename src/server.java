import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class server {
    private static final int PORT = 12345;
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server is listening on port " + PORT);

        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            }
        } finally {
            serverSocket.close();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

                String fileName = in.readUTF();
                File file = new File(fileName);

                if (file.exists() && !file.isDirectory()) { // Send file to client
                    out.writeLong(file.length());
                    FileInputStream fileIn = new FileInputStream(file);
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = fileIn.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                    fileIn.close();
                } else { // Receive file from client
                    long fileLength = in.readLong();
                    FileOutputStream fileOut = new FileOutputStream("Storage/" + fileName);
                    byte[] buffer = new byte[4096];
                    int length;
                    while (fileLength > 0 && (length = in.read(buffer, 0, (int) Math.min(buffer.length, fileLength))) != -1) {
                        fileOut.write(buffer, 0, length);
                        fileLength -= length;
                    }
                    fileOut.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
