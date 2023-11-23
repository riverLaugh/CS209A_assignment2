import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class client {
    private static final String HOST = "localhost";
    private static final int PORT = 8888;
    private static ConcurrentHashMap<String, Task> uploadTasks ;

    private static ConcurrentHashMap<String, Task> downloadTasks;
    public static class Task {
        private String fileName;
        private long totalSize;
        private long CurrentSize;
        public Task(String fileName, long totalSize) {
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.CurrentSize = 0;
        }
        // 方法来更新上传的大小
        public synchronized void updateCurrentSize(long size) {
            this.CurrentSize += size;
        }

        public String getCurrentSize() {
            return String.valueOf(CurrentSize);
        }

        public String getTotalSize() {
            return String.valueOf(totalSize);
        }

        // Getter 方法等
    }

    public static void main(String[] args) throws IOException {
        String cmd = "";
        while (!cmd.equalsIgnoreCase("quit")) {
            Scanner cmdIn = new Scanner(System.in);
            cmd = cmdIn.nextLine();
            String[] cmdList = cmd.split(" ");
            cmd = cmdList[0];
            switch (cmd) {
                case "up" -> {
                    String basePathString = "D:\\23f\\Java2\\CS209A_assignment2\\Upload";
                    ExecutorService executor = Executors.newCachedThreadPool();
                    uploadTasks = new ConcurrentHashMap<>();
                    for (int i = 1; i < cmdList.length; i++) {
                        Path path = Paths.get(basePathString, cmdList[i]);
                        File file = path.toFile();
                        if (file.isDirectory()) {
                            try (Stream<Path> paths = Files.walk(file.toPath())) {
                                String finalCmd = cmd;
                                paths.filter(Files::isRegularFile).forEach(filePath -> {
                                    executor.submit(() -> {
                                        try {
                                            Task task = new Task(filePath.toString(), filePath.toFile().length());
                                            uploadTasks.put(filePath.toString(), task);
                                            Socket socket = new Socket(HOST, PORT);
                                            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                                                out.writeUTF(finalCmd);
                                                sendFile(filePath.toFile(), out, Path.of(basePathString),task);
                                            }
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
                                    Task task = new Task(file.toString(), file.length());
                                    uploadTasks.put(file.toString(), task);
                                    Socket socket = new Socket(HOST, PORT);
                                    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                                        System.out.println(socket.toString());
                                        out.writeUTF(finalCmd1);
                                        sendFile(file, out, Path.of(basePathString),task);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                    executor.shutdown();

                }
                case "down" -> {
                    String basePathString = "D:\\23f\\Java2\\CS209A_assignment2\\Download";
                    ExecutorService executor = Executors.newCachedThreadPool();
                    downloadTasks = new ConcurrentHashMap<>();
                    for (int i = 1; i < cmdList.length; i++) {
                        String fileName = cmdList[i];
                        executor.submit(() -> {
                            try (Socket socket = new Socket(HOST, PORT);
                                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                                out.writeUTF("down");
                                out.writeUTF(fileName);
                                boolean isDir = in.readBoolean();
                                if(isDir){
                                    ArrayList<String> files = new ArrayList<>();
                                    String str;
                                    while (!(str = in.readUTF()).equals("End of dir")){
                                        files.add(str);
                                    }
                                    for (String file : files) {
                                        executor.submit(() -> {
                                            try {
                                                receiveFile(file, in, Paths.get(basePathString));
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        });
                                    }
                                }else{
                                    receiveFile(fileName, in, Paths.get(basePathString));
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                    executor.shutdown();
                }
                case "status"->{
                    uploadTasks.forEach((fileName, task) -> {
                        System.out.println("File: " + fileName + ", Progress: " + task.getCurrentSize() + "/" + task.getTotalSize());
                    });
                }
            }
        }
        System.out.println("connect end");
    }

    private static void sendFile(File subfile, ObjectOutputStream out, Path basePath,Task task) throws FileNotFoundException, IOException {
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
                task.updateCurrentSize(length);
            }
        }
    }

    private static void receiveFile(String fileName, ObjectInputStream in, Path basePath) throws IOException {
        String relativePath = in.readUTF();
        long fileLength = in.readLong();
        Path filePath = basePath.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Task task = new Task(fileName, fileLength);
        downloadTasks.put(fileName, task);
        try (OutputStream fileOut = new FileOutputStream(filePath.toFile())) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while (fileLength > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileLength))) > 0) {
                fileOut.write(buffer, 0, bytesRead);
                fileLength -= bytesRead;
                task.updateCurrentSize(bytesRead);
            }
        }
    }


}
