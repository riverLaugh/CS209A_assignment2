import java.io.*;
import java.net.*;
import java.nio.file.DirectoryStream;
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
    private static ConcurrentHashMap<String, Task> uploadTasks = new ConcurrentHashMap<>();
    ;

    private static ConcurrentHashMap<String, Task> downloadTasks = new ConcurrentHashMap<>();

    public static class Task {
        private String filePath;
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

        public Task(String filePath, long totalSize, int a) {
            this.filePath = filePath;
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

        private final Object pauseLock = new Object();

        public void pause() {
            synchronized (pauseLock) {
                if (status == TaskStatus.IN_PROGRESS) {
                    status = TaskStatus.PAUSED;
                }
            }
        }

        public void resume() {
            synchronized (pauseLock) {
                if (status == TaskStatus.PAUSED) {
                    status = TaskStatus.IN_PROGRESS;
                    pauseLock.notifyAll(); // 唤醒所有等待的线程
                }
            }
        }



        // 用于等待任务恢复的方法
        public void waitForResume() throws InterruptedException {
            synchronized (pauseLock) {
                while (status == TaskStatus.PAUSED) {
                    pauseLock.wait(); // 等待直到被唤醒
                    System.out.println("status:" + status.toString());
                }
            }
        }


        public synchronized void cancel() {
            synchronized (pauseLock) {
                status = TaskStatus.CANCELLED;
                pauseLock.notifyAll();
            }
        }

        public synchronized TaskStatus getStatus() {
            return status;
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
                    for (int i = 1; i < cmdList.length; i++) {
                        Path path = Paths.get(basePathString, cmdList[i]);
                        File file = path.toFile();
                        if (!file.exists()) {
                            System.out.println("file not found: " + cmdList[i]);
                        }

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
                    for (int i = 1; i < cmdList.length; i++) {
                        String fileName = cmdList[i];
                        // 使用单独的线程处理每个文件/目录的下载
                        executor.submit(() -> {
                            try {
                                downloadFileOrDirectory(fileName, basePathString);
                            } catch (IOException e) {
                                System.out.println("something closed,if you know,dont panic");
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
                case "pause" -> {
                    String type = cmdList[1]; // 任务ID，例如文件名
                    String taskName = cmdList[2]; // 任务ID，例如文件名
                    Task task;
                    if (type.equals("upload")) {
                        task = uploadTasks.get(taskName);
                        if (task != null) {
                            task.pause();
                        }
                    } else if (type.equals("download")) {
                        task = downloadTasks.get(taskName);
                        if (task != null) {
                            task.pause();
                        }
                    }
                    System.out.println(taskName +"is paused");
                }
                case "resume" -> {
                    String type = cmdList[1]; // 任务ID，例如文件名
                    String taskName = cmdList[2]; // 任务ID，例如文件名
                    Task task;
                    if (type.equals("upload")) {
                        task = uploadTasks.get(taskName);
                        if (task != null) {
                            task.resume();
                        }
                    } else if (type.equals("download")) {
                        task = downloadTasks.get(taskName);
                        if (task != null) {
                            task.resume();
                        }
                    }
                    System.out.println(taskName +"is resumed");
                }
                case "cancel" -> {
                    String type = cmdList[1]; // 任务ID，例如文件名
                    String taskName = cmdList[2]; // 任务ID，例如文件名
                    Task task;
                    if (type.equals("upload")) {
                        task = uploadTasks.get(taskName);
                        if (task != null) {
                            task.cancel();
                        }
                    } else if (type.equals("download")) {
                        task = downloadTasks.get(taskName);
                        if (task != null) {
                            task.cancel();
                        }
                    }
                    System.out.println(taskName +"is canceled");
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
                receiveFile(fileName,out, in, Paths.get(basePathString));
            }
        } catch (IOException e) {
            System.out.println("something close , if you know , dont panic");
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
                    receiveFile(finalFile,out, in, Paths.get(basePathString));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        executor.shutdown();
    }

    private static void sendFile(File subfile, ObjectOutputStream out, Path basePath) throws FileNotFoundException, IOException {
        Task task = new Task(subfile.toString(), subfile.length(), 0);
        String relativePath = basePath.relativize(subfile.toPath()).toString();
        uploadTasks.put(relativePath, task);
        try (FileInputStream fileIn = new FileInputStream(subfile)) {
            out.writeUTF(relativePath);
            System.out.println(relativePath);
            out.writeLong(subfile.length());
            byte[] buffer = new byte[4096];
            int length;
            while ((length = fileIn.read(buffer)) > 0) {
                try {
                    task.waitForResume(); // 等待任务恢复
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // 或适当处理中断
                }
                if (task.getStatus() == Task.TaskStatus.CANCELLED) {
                    out.writeUTF("CANCEL");
                    out.flush();
                    break; // 终止任务
                }else{
                    out.writeUTF("IN_PROGRESS");
                    out.flush();
                }
                out.writeInt(length);
                out.write(buffer, 0, length);
                out.flush();
                task.updateCurrentSize(length);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Thread interrupted", e);
                }
            }
        }
    }

    private static void receiveFile(String fileName,ObjectOutputStream out, ObjectInputStream in, Path basePath) {
        try {
            String relativePath = in.readUTF();
            long fileLength = in.readLong();
            Path filePath = basePath.resolve(relativePath);
            Files.createDirectories(filePath.getParent());
            Task task = new Task(filePath.toString(), fileLength, 1);
            downloadTasks.put(fileName, task);
            try (OutputStream fileOut = new FileOutputStream(filePath.toFile())) {
                while (fileLength > 0) {
                    if (task.status == Task.TaskStatus.CANCELLED) {
                        out.writeUTF("CANCEL");
                    } else if (task.status == Task.TaskStatus.PAUSED) {
                        out.writeUTF("PAUSED");
                        try {
                            task.waitForResume(); // 等待任务恢复
                            if (task.status == Task.TaskStatus.CANCELLED) {
                                out.writeUTF("CANCEL");
                                fileOut.close();
                                Files.deleteIfExists(filePath);
                                // 检查并删除空的父目录
                                Path parentDir = filePath.getParent();
                                if (isDirectoryEmpty(parentDir)) {
                                    Files.deleteIfExists(parentDir);
                                }
                                break;
                            } else {
                                out.writeUTF("IN_PROGRESS");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return; // 或适当处理中断
                        }
                        System.out.println(fileName + " status change to " + task.status);
                    }

                    out.writeUTF("IN_PROGRESS");
                    out.flush();
                    int bytesRead = in.readInt();
                    byte[] buffer = new byte[bytesRead];
                    in.readFully(buffer);
                    fileOut.write(buffer, 0, bytesRead);
                    fileLength -= bytesRead;
                    task.updateCurrentSize(bytesRead);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interrupted", e);
                    }
                }
            }
        }catch (IOException e){
            System.out.println("something closed,if you know,dont panic");
        }
        System.out.printf("%s tp success\n", fileName);
    }

    private static boolean isDirectoryEmpty(Path directory) throws IOException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
            return !dirStream.iterator().hasNext();
        }
    }

    private static void cancelUploadTask(String relativePath) {
        try (Socket socket = new Socket(HOST, PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.writeUTF("cancel");
            out.writeUTF(relativePath);
            out.flush();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
