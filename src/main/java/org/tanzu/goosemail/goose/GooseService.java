package org.tanzu.goosemail.goose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tanzu.goose.cf.GooseExecutor;
import org.tanzu.goose.cf.GooseOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class GooseService {

    private static final Logger log = LoggerFactory.getLogger(GooseService.class);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    private final GooseExecutor executor;
    private final ConcurrentHashMap<String, ConversationSession> activeSessions = new ConcurrentHashMap<>();

    public GooseService(GooseExecutor executor) {
        this.executor = executor;
    }

    /**
     * Execute a prompt within a conversation session derived from the email thread.
     * <p>
     * If an active (non-expired) session exists for this sender + subject combination,
     * the prompt is sent as a follow-up in the same Goose session. Otherwise a new
     * session is created.
     */
    public String executePrompt(String sender, String subject, String prompt) {
        String sessionKey = deriveSessionKey(sender, subject);
        ConversationSession existing = activeSessions.get(sessionKey);

        boolean resume = existing != null && !isExpired(existing);

        if (resume) {
            log.info("Resuming session '{}' for thread [{}] from {}", existing.sessionName(), subject, sender);
            return executeInSession(sessionKey, existing, prompt, true);
        } else {
            if (existing != null) {
                log.info("Session expired for thread [{}] from {}, starting fresh", subject, sender);
                evictSession(sessionKey, existing);
            }
            return startNewSession(sessionKey, sender, subject, prompt);
        }
    }

    // ==================== Session Eviction ====================

    /**
     * Runs every 5 minutes to evict sessions that have been inactive for longer
     * than {@link #SESSION_TIMEOUT}.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void evictExpiredSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TIMEOUT);
        int evicted = 0;

        for (var entry : activeSessions.entrySet()) {
            if (entry.getValue().lastActivity().isBefore(cutoff)) {
                evictSession(entry.getKey(), entry.getValue());
                evicted++;
            }
        }

        if (evicted > 0) {
            log.info("Evicted {} expired session(s), {} active session(s) remaining", evicted, activeSessions.size());
        }
    }

    // ==================== Private Helpers ====================

    private String startNewSession(String sessionKey, String sender, String subject, String prompt) {
        Path workspaceDir = null;
        try {
            workspaceDir = Files.createTempDirectory("goose-workspace-");
            log.info("Created workspace {} for new session, thread [{}] from {}", workspaceDir, subject, sender);

            String sessionName = "mail-" + sessionKey;
            ConversationSession session = new ConversationSession(sessionName, workspaceDir, Instant.now());
            activeSessions.put(sessionKey, session);

            return executeInSession(sessionKey, session, prompt, false);

        } catch (Exception e) {
            log.error("Error starting new Goose session for thread [{}]: {}", subject, e.getMessage(), e);
            // Clean up workspace if session creation failed
            if (workspaceDir != null) {
                cleanupWorkspace(workspaceDir);
            }
            activeSessions.remove(sessionKey);
            return "Error processing your request: " + e.getMessage();
        }
    }

    private String executeInSession(String sessionKey, ConversationSession session, String prompt, boolean resume) {
        try {
            GooseOptions options = GooseOptions.builder()
                    .workingDirectory(session.workspaceDir())
                    .build();

            String response = executor.executeInSession(session.sessionName(), prompt, resume, options);

            // Update last-activity timestamp
            activeSessions.put(sessionKey, session.touch());

            log.info("Goose session '{}' execution completed (resume={})", session.sessionName(), resume);
            return response;

        } catch (Exception e) {
            log.error("Error executing in Goose session '{}': {}", session.sessionName(), e.getMessage(), e);
            return "Error processing your request: " + e.getMessage();
        }
    }

    private void evictSession(String sessionKey, ConversationSession session) {
        activeSessions.remove(sessionKey);
        cleanupWorkspace(session.workspaceDir());
        log.info("Evicted session '{}', cleaned up workspace {}", session.sessionName(), session.workspaceDir());
    }

    /**
     * Derive a deterministic session key from the sender address and the
     * normalized email subject (stripped of Re:/Fwd: prefixes, lowercased).
     */
    private String deriveSessionKey(String sender, String subject) {
        String normalized = normalizeSubject(subject);
        String raw = sender.toLowerCase() + "#" + normalized;
        return sha256Hex(raw).substring(0, 16);
    }

    private String normalizeSubject(String subject) {
        if (subject == null) {
            return "";
        }
        String s = subject.trim();
        // Repeatedly strip Re: / Fwd: / Fw: prefixes (case-insensitive)
        while (true) {
            String lower = s.toLowerCase();
            if (lower.startsWith("re:")) {
                s = s.substring(3).trim();
            } else if (lower.startsWith("fwd:")) {
                s = s.substring(4).trim();
            } else if (lower.startsWith("fw:")) {
                s = s.substring(3).trim();
            } else {
                break;
            }
        }
        return s.toLowerCase();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private boolean isExpired(ConversationSession session) {
        return session.lastActivity().plus(SESSION_TIMEOUT).isBefore(Instant.now());
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
