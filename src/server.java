import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class server {
    private static final int PORT = 12345;
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server is listening on port " + PORT);

        try {
            Socket clientSocket = serverSocket.accept();
            ClientHandler clientHandler = new ClientHandler(clientSocket);
            while (true) {
                clientHandler.run();
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
        private static void createDirFile(String relativePath, ObjectInputStream in) throws IOException {
            Path path = Paths.get("D:\\23f\\java2\\assignment2\\Storage", relativePath);
            System.out.printf("path : %s", path.toString());
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

        private static void createSingleFile( ObjectInputStream in) throws IOException {
            Path path = Paths.get("D:\\23f\\java2\\assignment2\\Storage", in.readUTF());
            System.out.printf("path : %s", path.toString());

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
        private static void sendSingleFile(File subfile, ObjectOutputStream out) throws FileNotFoundException, IOException {
            try (FileInputStream fileIn = new FileInputStream(subfile)) {
                out.writeUTF(subfile.getName());
                System.out.printf("file name : %s\n", subfile.getName());
                out.writeLong(subfile.length());
                System.out.printf("Long : %d\n", subfile.length());
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fileIn.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        }

        private static void sendDirFile(File subfile, ObjectOutputStream out) throws FileNotFoundException, IOException {
            try (FileInputStream fileIn = new FileInputStream(subfile)) {
                out.writeLong(subfile.length());
                System.out.printf("Long : %d\n", subfile.length());
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fileIn.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        }

        private static void sendFile(File file, ObjectOutputStream out, String basePathString) throws IOException {
            if (file.isDirectory()) {
                System.out.println("it is a dir");
                out.writeBoolean(true);
                System.out.println(true);
                out.writeUTF("FILES_TP");
                System.out.println("FILES_TP");
                try (Stream<Path> files = Files.walk(file.toPath());) {
                    Path basePath = Paths.get(basePathString);
                    files.forEach(path -> {
                        System.out.println(path.toString());
                        File subfile = path.toFile();
                        if (!subfile.isDirectory()) { //只取文件
                            String relativePath = basePath.relativize(path).toString();
                            try {
                                out.writeUTF(relativePath); // dir1/"1.txt"
                                System.out.println(relativePath);
                                sendDirFile(subfile, out);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                }
                out.writeUTF("END_OF_DIR");
            } else if (file.isFile()){
                System.out.println("it is a file");
                out.writeBoolean(false);
                out.writeUTF("FILES_TP");
                System.out.println("FILES_TP");
                sendSingleFile(file, out);
            }
            else{
                System.out.println(file.toPath().toString());
            }
        }

        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {
                String cmd = in.readUTF();
                System.out.printf("cmd : %s\n", cmd);
                switch (cmd) {
                    case "up" -> {
                        boolean isDirectory = in.readBoolean();
                        while (true) {
                            String key = in.readUTF();
                            System.out.printf("key : %s\n", key);
                            if (key.equals("END_OF_FILES")) {
                                break;
                            }
                            if (isDirectory) {
                                while (true) {
                                    String relativePath = in.readUTF(); // 读取相对路径
                                    System.out.println(relativePath);
                                    if ("END_OF_DIR".equals(relativePath)) {
                                        break; // 终止循环
                                    }
                                    createDirFile(relativePath, in); // 创建文件
                                }
                            } else {
                                createSingleFile( in); // 创建文件
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
