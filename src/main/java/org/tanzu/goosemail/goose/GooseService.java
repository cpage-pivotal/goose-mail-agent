package org.tanzu.goosemail.goose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tanzu.goose.cf.GooseExecutor;
import org.tanzu.goose.cf.GooseOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class GooseService {

    private static final Logger log = LoggerFactory.getLogger(GooseService.class);

    private final GooseExecutor executor;

    public GooseService(GooseExecutor executor) {
        this.executor = executor;
    }

    public String executePrompt(String prompt) {
        Path workspaceDir = null;
        try {
            workspaceDir = Files.createTempDirectory("goose-workspace-");
            log.info("Created temporary workspace: {}", workspaceDir);

            GooseOptions options = GooseOptions.builder()
                    .workingDirectory(workspaceDir)
                    .build();

            String response = executor.execute(prompt, options);

            log.info("Goose execution completed");
            return response;

        } catch (Exception e) {
            log.error("Error executing Goose: {}", e.getMessage(), e);
            return "Error processing your request: " + e.getMessage();
        } finally {
            if (workspaceDir != null) {
                cleanupWorkspace(workspaceDir);
            }
        }
    }

    private void cleanupWorkspace(Path workspaceDir) {
        try (Stream<Path> walk = Files.walk(workspaceDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", path);
                        }
                    });
            log.info("Cleaned up workspace: {}", workspaceDir);
        } catch (IOException e) {
            log.warn("Failed to cleanup workspace: {}", workspaceDir, e);
        }
    }
}
