import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.stream.Stream;

public class client {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) throws IOException {
        String cmd = "";
        Socket socket = new Socket(HOST, PORT);
        while (!cmd.equalsIgnoreCase("quit")) {
            Scanner in = new Scanner(System.in);
            cmd = in.nextLine();
            String[] cmdList = cmd.split(" ");
            cmd = cmdList[0];
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                out.writeUTF(cmd);
                System.out.println(cmd);
                switch (cmd) {
                    case "up" -> {
                        for (int i = 1; i < cmdList.length; i++) {
                            String basePathString = "D:\\23f\\java2\\assignment2\\Upload";
                            Path path = Paths.get(basePathString, cmdList[i]);
                            File file = path.toFile();
                            sendFile(file, out, basePathString);
                        }
                        out.writeUTF("END_OF_FILES");
                        System.out.println("END_OF_FILES");
                    }
                    case "down" ->{

                    }
                }
            }
        }
        socket.close();
        System.out.println("connect end");
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

    private static void createDirFile(String basepath ,String relativePath, ObjectInputStream in) throws IOException {
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
