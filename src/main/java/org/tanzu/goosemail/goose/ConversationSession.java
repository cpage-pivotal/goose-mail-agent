package org.tanzu.goosemail.goose;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Tracks an active Goose conversation session tied to an email thread.
 *
 * @param sessionName  the Goose CLI session name (used with {@code -n})
 * @param workspaceDir the temporary workspace directory for this session
 * @param lastActivity the timestamp of the most recent interaction
 */
public record ConversationSession(String sessionName, Path workspaceDir, Instant lastActivity) {

    /**
     * Returns a copy of this session with {@code lastActivity} updated to now.
     */
    public ConversationSession touch() {
        return new ConversationSession(sessionName, workspaceDir, Instant.now());
    }
}
