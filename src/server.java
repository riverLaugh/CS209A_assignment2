import java.io.*;
import java.net.*;
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
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {
                String cmd = in.readUTF();
                System.out.println(cmd);
                switch (cmd) {
                    case "up" -> {
                        receiveFile(in);
                        System.out.println("finish ftp");
                    }
                    case "down" ->{
                        String fileName = in.readUTF();
                        File file = new File("D:\\23f\\Java2\\CS209A_assignment2\\download", fileName);
                        out.writeBoolean(file.isDirectory());
                        if (file.exists()) {
                            if(file.isDirectory()){
                                try(Stream<Path> files = Files.walk(file.toPath())){
                                    files.filter(Files::isRegularFile).forEach(filePath ->{
                                        try {
                                            out.writeUTF(filePath.toString());
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                                }
                                out.writeUTF("End of dir");

                            }else{
                                sendFile(file, out); // 发送文件
                            }
                        } else {
                            System.out.println("the file not exists");
                            // 文件不存在的处理
                        }
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

        private static void sendFile(File file, ObjectOutputStream out) throws IOException {
            out.writeUTF(file.getName());
            out.writeLong(file.length());
            try (FileInputStream fileIn = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fileIn.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        }


        private void receiveFile(ObjectInputStream in) throws IOException {
            String relativePath = in.readUTF();
            long fileLength = in.readLong();
            Path path = Paths.get("D:\\23f\\Java2\\CS209A_assignment2\\Storage", relativePath);
            Files.createDirectories(path.getParent());
            try (OutputStream fileOut = new FileOutputStream(path.toFile())) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while (fileLength > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileLength))) > 0) {
                    fileOut.write(buffer, 0, bytesRead);
                    fileLength -= bytesRead;
                }
            }
        }

        private static void sendFile(File subfile, ObjectOutputStream out, Path basePath) throws FileNotFoundException, IOException {
            String relativePath = basePath.relativize(subfile.toPath()).toString();
            try (FileInputStream fileIn = new FileInputStream(subfile)) {
                out.writeUTF(relativePath);
                out.writeLong(subfile.length());
                System.out.printf("Long : %d\n", subfile.length());
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fileIn.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        }

    }

}
