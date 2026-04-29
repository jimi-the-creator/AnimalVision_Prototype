package com.animalvision.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProcessRunner {
    public ProcessResult run(List<String> command, Path workingDirectory, Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        Process process = builder.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread stdoutThread = readStream(process.getInputStream(), stdout, outputConsumer);
        Thread stderrThread = readStream(process.getErrorStream(), stderr, outputConsumer);

        int exitCode = process.waitFor();
        stdoutThread.join();
        stderrThread.join();

        return new ProcessResult(exitCode, stdout.toString(), stderr.toString());
    }

    public ProcessResult run(Path workingDirectory, Consumer<String> outputConsumer, String... command)
            throws IOException, InterruptedException {
        return run(new ArrayList<>(List.of(command)), workingDirectory, outputConsumer);
    }

    private Thread readStream(InputStream stream, StringBuilder sink, Consumer<String> outputConsumer) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.append(line).append(System.lineSeparator());
                    if (outputConsumer != null) {
                        outputConsumer.accept(line);
                    }
                }
            } catch (IOException e) {
                sink.append(e.getMessage()).append(System.lineSeparator());
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {
        public boolean succeeded() {
            return exitCode == 0;
        }
    }
}
