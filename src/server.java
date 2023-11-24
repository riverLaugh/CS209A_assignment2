import java.io.*;
import java.net.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class server {
    private static final int PORT = 8888;
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server is listening on port " + PORT);
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println(clientSocket.toString());
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

        @Override
        public void run() {
            try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            ) {
                String cmd = in.readUTF();
//                System.out.println(cmd);
                String basePathString = "D:\\23f\\Java2\\CS209A_assignment2\\resources";
                switch (cmd) {
                    case "up" -> {
                        receiveFile(in);
                        System.out.println("finish ftp");
                    }
                    case "down" -> {
                        String fileName = in.readUTF();

                        File file = new File(basePathString, fileName);
                        out.writeBoolean(file.isDirectory());
                        out.flush();
//                        System.out.printf("%s is dir: %b\n", fileName, file.isDirectory());
                        if (file.isDirectory()) {
                            try (Stream<Path> files = Files.walk(file.toPath())) {
                                files.filter(Files::isRegularFile).forEach(filePath -> {
                                    try {
                                        System.out.println(filePath);
                                        String relativePath = Paths.get(basePathString).relativize(filePath).toString();
                                        System.out.println(relativePath);
                                        out.writeUTF(relativePath);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                            out.writeUTF("END_OF_DIR");
                            out.flush();
                        } else if (file.exists()) {
                            sendFile(file, out, Paths.get(basePathString));
                        } else {
                            System.out.println("File not found: " + fileName);
                        }
                    }
                    //内部指令，不提供接口
                    case "cancel" -> {
                        String fileName = in.readUTF();

                    }

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

        private static void sendFile(File file, ObjectOutputStream out, Path basePath) throws IOException {
            try (FileInputStream fileIn = new FileInputStream(file)) {
                String relativePath = basePath.relativize(file.toPath()).toString();
                out.writeUTF(relativePath);
                out.flush();
                out.writeLong(file.length());
                out.flush();
                System.out.printf("%s length is %d", file.getName(), file.length());
                System.out.println(file.toString() + "is sending");
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fileIn.read(buffer)) > 0) {
                    System.out.println((String.valueOf(length) + " bytes are parpring to send"));
                    out.write(buffer, 0, length);
                    out.flush();
                    System.out.println(String.valueOf(length + " bytes are sent"));
                }
            }
        }


        private void receiveFile(ObjectInputStream in) throws IOException {
            String relativePath = in.readUTF();
            long fileLength = in.readLong();
            Path path = Paths.get("D:\\23f\\Java2\\CS209A_assignment2\\Storage", relativePath);
            Files.createDirectories(path.getParent());
            try (OutputStream fileOut = new FileOutputStream(path.toFile())) {
                String inst = "";
                while (fileLength > 0) {
                     inst = in.readUTF();
                    if ("IN_PROGRESS".equals(inst)) {
                        int dataSize = in.readInt(); // 读取数据长度
                        byte[] data = new byte[dataSize];
                        in.readFully(data);
                        fileOut.write(data, 0, dataSize);
                        fileLength -= dataSize;
                    } else if ("CANCEL".equals(inst)){
                        break;
                    }
                }
                if ("CANCEL".equals(inst)){
                    fileOut.close();
                    Files.deleteIfExists(path);
                    // 检查并删除空的父目录
                    Path parentDir = path.getParent();
                    if (isDirectoryEmpty(parentDir)) {
                        Files.deleteIfExists(parentDir);
                    }
                    System.out.println(relativePath +" is canceled");
                }
            }

        }

        private static boolean isDirectoryEmpty(Path directory) throws IOException {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
                return !dirStream.iterator().hasNext();
            }
        }

    }

}
