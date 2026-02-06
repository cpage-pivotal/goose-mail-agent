package org.tanzu.goosemail.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.tanzu.goosemail.goose.GooseService;
import org.tanzu.goosemail.mailgun.EmailService;

@Service
public class MailAgentService {

    private static final Logger log = LoggerFactory.getLogger(MailAgentService.class);

    private final GooseService gooseService;
    private final EmailService emailService;

    public MailAgentService(GooseService gooseService, EmailService emailService) {
        this.gooseService = gooseService;
        this.emailService = emailService;
    }

    @Async
    public void processEmail(String sender, String subject, String body) {
        log.info("Processing email from {} with subject: {}", sender, subject);

        try {
            String response = gooseService.executePrompt(body);

            String replySubject = subject.startsWith("Re:") ? subject : "Re: " + subject;

            emailService.sendEmail(sender, replySubject, response);

            log.info("Successfully processed and replied to email from {}", sender);

        } catch (Exception e) {
            log.error("Error processing email from {}: {}", sender, e.getMessage(), e);

            try {
                emailService.sendEmail(
                        sender,
                        "Re: " + subject + " [Error]",
                        "Sorry, there was an error processing your request:\n\n" + e.getMessage()
                );
            } catch (Exception emailError) {
                log.error("Failed to send error response to {}: {}", sender, emailError.getMessage());
            }
        }
    }
}
