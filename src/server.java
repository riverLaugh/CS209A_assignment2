import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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


        private static void createFile(String relativePath, ObjectInputStream in) throws IOException {
            Path path = Paths.get("D:\\23f\\Java2\\CS209A_assignment2\\Storage", relativePath);
            Files.createDirectories(path.getParent()); // 确保父目录存在

            long fileLength = in.readLong(); // 读取文件长度
            try (OutputStream fileOut = new FileOutputStream(path.toFile())) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while (fileLength > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileLength))) > 0) {
                    fileOut.write(buffer, 0, bytesRead);
                    fileLength -= bytesRead;
                }
            }
        }

        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {
                String cmd = in.readUTF();
                switch (cmd) {
                    case "up" -> {
                        boolean isDirectory = in.readBoolean();
                        while (true) {
                            String key = in.readUTF();
                            if (key.equals("END_OF_FILES")) {
                                break;
                            }
                            if (isDirectory) {
                                while (true) {
                                    String relativePath = in.readUTF(); // 读取相对路径
                                    if ("END_OF_DIR".equals(relativePath)) {
                                        break; // 终止循环
                                    }
                                    createFile(relativePath, in); // 创建文件
                                }
                            } else {
                                createFile("", in); // 创建文件
                            }
                        }

                    }
                    case "down" -> {

                    }

                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
