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
    private static ConcurrentHashMap<String, Task> uploadTasks;

    private static ConcurrentHashMap<String, Task> downloadTasks;

    public static class Task {
        private String fileName;
        private long totalSize;
        private long CurrentSize;
        private TaskStatus status;
        private TaskType type;

        public enum TaskStatus {
            IN_PROGRESS, PAUSED, CANCELLED, COMPLETED
        }

        public enum TaskType {
            UPLOAD, DOWNLOAD
        }

        public Task(String fileName, long totalSize, int a) {
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.CurrentSize = 0;
            this.status = TaskStatus.IN_PROGRESS;
            this.type = a == 1 ? TaskType.DOWNLOAD : TaskType.UPLOAD;
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
                                            Socket socket = new Socket(HOST, PORT);
                                            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                                                out.writeUTF(finalCmd);
                                                sendFile(filePath.toFile(), out, Path.of(basePathString));
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
                                    Socket socket = new Socket(HOST, PORT);
                                    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                                        System.out.println(socket.toString());
                                        out.writeUTF(finalCmd1);
                                        sendFile(file, out, Path.of(basePathString));
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
                    downloadTasks = new ConcurrentHashMap<>();
                    String basePathString = "D:\\23f\\Java2\\CS209A_assignment2\\Download";
                    ExecutorService executor = Executors.newCachedThreadPool();
                    downloadTasks = new ConcurrentHashMap<>();
                    for (int i = 1; i < cmdList.length; i++) {
                        String fileName = cmdList[i];
                        // 使用单独的线程处理每个文件/目录的下载
                        executor.submit(() -> {
                            try {
                                downloadFileOrDirectory(fileName, basePathString);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        System.out.printf("%s is submitted\n", fileName);
                    }
                    executor.shutdown();
                }
                case "status" -> {
                    if (uploadTasks != null) {
                        uploadTasks.forEach((fileName, task) -> {
                            System.out.println("UPLOAD-" + "File: " + fileName + ", Progress: " + task.getCurrentSize() + "/" + task.getTotalSize());
                        });
                    }
                    if (downloadTasks != null) {
                        downloadTasks.forEach((fileName, task) -> {
                            System.out.println("Download-" + "File: " + fileName + ", Progress: " + task.getCurrentSize() + "/" + task.getTotalSize());
                        });
                    }
                }
                case "pause" ->{

                }
                case "reboot" ->{

                }
                case "cancel" ->{

                }

            }
        }
        System.out.println("connect end");
    }

    private static void downloadFileOrDirectory(String fileName, String basePathString) throws IOException {
        try (Socket socket = new Socket(HOST, PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            System.out.printf("%s is in downloadFileOrDirectory\n", fileName);
            System.out.println(socket.toString());
            out.writeUTF("down");
            out.writeUTF(fileName);
            out.flush();
            boolean isDir = in.readBoolean();
            if (isDir) {
                // 处理目录
                String file;
                ArrayList<String> arrayList = new ArrayList<>();
                while (!(file = in.readUTF()).equals("END_OF_DIR")) {
                    arrayList.add(file);
                }
                arrayList.forEach(System.out::println);
                handleDirectoryDownload(arrayList, basePathString);
            } else {
                // 处理单个文件
                System.out.printf("%s is a single file\n", fileName);
                receiveFile(fileName, in, Paths.get(basePathString));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleDirectoryDownload(ArrayList<String> files, String basePathString) throws IOException {
        String file;
        ExecutorService executor = Executors.newCachedThreadPool();
        for (int i = 0; i < files.size(); i++) {
            file = files.get(i);
            String finalFile = file;
            executor.submit(() -> {
                try (Socket socket = new Socket(HOST, PORT);
                     ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
                ) {
                    out.writeUTF("down");
                    out.writeUTF(finalFile);
                    out.flush();
                    boolean isDir = in.readBoolean();
                    receiveFile(finalFile, in, Paths.get(basePathString));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        executor.shutdown();
    }

    private static void sendFile(File subfile, ObjectOutputStream out, Path basePath) throws FileNotFoundException, IOException {
        Task task = new Task(subfile.toString(), subfile.length(),0);
        uploadTasks.put(subfile.toString(), task);
        String relativePath = basePath.relativize(subfile.toPath()).toString();
        try (FileInputStream fileIn = new FileInputStream(subfile)) {
            out.writeUTF(relativePath);
            System.out.println(relativePath);
            out.writeLong(subfile.length());
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
        Task task = new Task(fileName, fileLength,1);
        downloadTasks.put(fileName, task);
        try (OutputStream fileOut = new FileOutputStream(filePath.toFile())) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while (fileLength > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileLength))) > 0) {
                fileOut.write(buffer, 0, bytesRead);
                fileLength -= bytesRead;
                task.updateCurrentSize(bytesRead);
                System.out.println(String.valueOf(bytesRead) + "is received");
            }
        }
        System.out.printf("%s tp success\n", fileName);
    }


}
