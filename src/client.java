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
                switch (cmd) {
                    case "up" -> {
//                        out.writeUTF("ftp start");
                        for (int i = 1; i < cmdList.length; i++) {
                            out.writeUTF("FILES_TP");
                            String basePathString = "D:\\23f\\Java2\\CS209A_assignment2\\Upload";
                            Path path = Paths.get(basePathString, cmdList[i]);
                            File file = path.toFile();
                            sendFile(file, out, basePathString);
                        }
                        out.writeUTF("END_OF_FILES");
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
            out.writeLong(subfile.length());
            byte[] buffer = new byte[4096];
            int length;
            while ((length = fileIn.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private static void sendFile(File file, ObjectOutputStream out, String basePathString) throws IOException {
        if (file.isDirectory()) {
            out.writeBoolean(true);
            try (Stream<Path> files = Files.walk(file.toPath());) {
                Path basePath = Paths.get(basePathString);
                files.forEach(path -> {
                    System.out.println(path.toString());
                    File subfile = path.toFile();
                    if (!subfile.isDirectory()) { //只取文件
                        String relativePath = basePath.relativize(path).toString();
                        try {
                            out.writeUTF(relativePath); // "1.txt"
                            sendSingleFile(subfile, out);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            out.writeUTF("END_OF_DIR");
        } else {
            out.writeBoolean(false);
            sendSingleFile(file, out);
        }
    }


    private static void receiveFile(String fileName) throws IOException {
        Socket socket = new Socket(HOST, PORT);
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream()); FileOutputStream fileOut = new FileOutputStream("Download/" + fileName); ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.writeUTF(fileName);
            long fileLength = in.readLong();
            byte[] buffer = new byte[4096];
            int length;
            while (fileLength > 0 && (length = in.read(buffer, 0, (int) Math.min(buffer.length, fileLength))) != -1) {
                fileOut.write(buffer, 0, length);
                fileLength -= length;
            }
        }
    }
}
