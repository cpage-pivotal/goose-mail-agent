package org.tanzu.goosemail.mailgun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tanzu.goosemail.agent.MailAgentService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/webhook")
public class MailgunWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MailgunWebhookController.class);

    private final MailAgentService mailAgentService;
    private final String signingKey;

    public MailgunWebhookController(
            MailAgentService mailAgentService,
            @Value("${mailgun.signing-key}") String signingKey) {
        this.mailAgentService = mailAgentService;
        this.signingKey = signingKey;
    }

    @PostMapping("/mailgun")
    public ResponseEntity<String> handleIncomingEmail(
            @RequestParam("sender") String sender,
            @RequestParam("subject") String subject,
            @RequestParam("body-plain") String bodyPlain,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("token") String token,
            @RequestParam("signature") String signature) {

        log.info("Received email from: {} with subject: {}", sender, subject);

        if (!verifySignature(timestamp, token, signature)) {
            log.warn("Invalid webhook signature from sender: {}", sender);
            return ResponseEntity.status(403).body("Invalid signature");
        }

        try {
            mailAgentService.processEmail(sender, subject, bodyPlain);
            return ResponseEntity.ok("Email processed");
        } catch (Exception e) {
            log.error("Error processing email from {}: {}", sender, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error processing email");
        }
    }

    private boolean verifySignature(String timestamp, String token, String signature) {
        try {
            String data = timestamp + token;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hmacBytes);
            return computedSignature.equalsIgnoreCase(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying signature: {}", e.getMessage());
            return false;
        }
    }
}
