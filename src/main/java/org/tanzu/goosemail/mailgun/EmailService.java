package org.tanzu.goosemail.mailgun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final WebClient webClient;
    private final String domain;
    private final String fromAddress;
    private final MarkdownService markdownService;

    public EmailService(
            @Value("${mailgun.api-key}") String apiKey,
            @Value("${mailgun.domain}") String domain,
            @Value("${mailgun.from}") String fromAddress,
            MarkdownService markdownService) {
        this.domain = domain;
        this.fromAddress = fromAddress;
        this.markdownService = markdownService;

        String credentials = Base64.getEncoder()
                .encodeToString(("api:" + apiKey).getBytes(StandardCharsets.UTF_8));

        this.webClient = WebClient.builder()
                .baseUrl("https://api.mailgun.net/v3")
                .defaultHeader("Authorization", "Basic " + credentials)
                .build();
    }

    public void sendEmail(String to, String subject, String markdownBody, String replyTo) {
        log.info("Sending email to: {} with subject: {} (reply-to: {})", to, subject, replyTo);

        String htmlBody = markdownService.convertToHtml(markdownBody);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("from", fromAddress);
        formData.add("h:Reply-To", replyTo);
        formData.add("to", to);
        formData.add("subject", subject);
        formData.add("text", markdownBody);
        formData.add("html", htmlBody);

        webClient.post()
                .uri("/{domain}/messages", domain)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Email sent successfully to {}", to))
                .doOnError(error -> log.error("Failed to send email to {}: {}", to, error.getMessage()))
                .block();
    }
}
