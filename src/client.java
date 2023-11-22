import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class client {
    private static final String HOST = "localhost";
    private static final int PORT = 8888;

    public static void main(String[] args) throws IOException {
        String cmd = "";
        while (!cmd.equalsIgnoreCase("quit")) {
            Scanner cmdIn = new Scanner(System.in);
            cmd = cmdIn.nextLine();
            String[] cmdList = cmd.split(" ");
            cmd = cmdList[0];
            switch (cmd) {
                case "up" -> {
                    String basePathString = "D:\\23f\\java2\\assignment2\\Upload";
                    ExecutorService executor = Executors.newCachedThreadPool();
                    for (int i = 1; i < cmdList.length; i++) {
                        Path path = Paths.get(basePathString, cmdList[i]);
                        File file = path.toFile();
                        if (file.isDirectory()) {
                            try (Stream<Path> paths = Files.walk(file.toPath())) {
                                String finalCmd = cmd;
                                paths.filter(Files::isRegularFile).forEach(filePath -> {
                                    executor.submit(() -> {
                                        try {
                                            Socket socket = new Socket(HOST, PORT);
                                            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                                                out.writeUTF(finalCmd);
                                                sendFile(filePath.toFile(), out, Path.of(basePathString));
                                            }
                                            socket.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    });
                                });
                            }
                        } else if (file.isFile()) {
                            String finalCmd1 = cmd;
                            executor.submit(() -> {
                                try {
                                    Socket socket = new Socket(HOST, PORT);
                                    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                                        out.writeUTF(finalCmd1);
                                        sendFile(file, out, Path.of(basePathString));
                                    }
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                    executor.shutdown();
                }
                // 其他命令处理...
//                case "down" -> {
//                    ExecutorService downloadExecutor = Executors.newCachedThreadPool();
//                    for (int i = 1; i < cmdList.length; i++) {
//                        String fileToDownload = cmdList[i];
//                        downloadExecutor.submit(() -> {
//                            try {
//                                Socket socket = new Socket(HOST, PORT);
//                                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
//                                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
//                                    out.writeUTF("down");
//                                    out.writeUTF(fileToDownload); // 发送下载文件或文件夹的名称
//                                    receiveFile(socket, in); // 接收文件的方法
//                                }
//                                socket.close();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        });
//                    }
//                    downloadExecutor.shutdown();
//                }
            }


        }
        System.out.println("connect end");
    }

    private static void sendFile(File subfile, ObjectOutputStream out, Path basePath) throws FileNotFoundException, IOException {
        String relativePath = basePath.relativize(subfile.toPath()).toString();
        try (FileInputStream fileIn = new FileInputStream(subfile)) {
            out.writeUTF(relativePath);
            System.out.println(relativePath);
            out.writeLong(subfile.length());
            System.out.printf("Long : %d\n", subfile.length());
            byte[] buffer = new byte[4096];
            int length;
            while ((length = fileIn.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }


    private static void createDirFile(String basepath, String relativePath, ObjectInputStream in) throws IOException {
        Path path = Paths.get(basepath, relativePath);
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

    private static void createSingleFile(String basepath, ObjectInputStream in) throws IOException {
        Path path = Paths.get(basepath, in.readUTF());
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

}
