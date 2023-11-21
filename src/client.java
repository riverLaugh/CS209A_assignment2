import java.io.*;
import java.net.*;
import java.util.Scanner;

public class client {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) throws IOException {
        String cmd = "";
        while (!cmd.equalsIgnoreCase("quit")) {
            Scanner in = new Scanner(System.in);
            cmd = in.nextLine();
            String[] cmdList = cmd.split(" ");
            cmd = cmdList[0];
            switch (cmd) {
                case "up":
                    sendFile(cmdList[1]);
                    break;
                case "down":
                    receiveFile(cmdList[1]);
                    break;
            }

        }

    }

    private static void sendFile(String filePath) throws IOException {
        Socket socket = new Socket(HOST, PORT);
        File file = new File(filePath);
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            FileInputStream fileIn = new FileInputStream(file)) {
            out.writeUTF(file.getName());
            out.writeLong(file.length());
            byte[] buffer = new byte[4096];
            int length;
            while ((length = fileIn.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
        socket.close();
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
        socket.close();
    }
}
