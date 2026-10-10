package com.helpdesk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Sends plain-text emails in the background so a slow or broken mail server never blocks
 * (or breaks) the request. If no mail account is configured the message is only logged.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String username;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.username = username == null ? "" : username.trim();
        this.from = from == null ? "" : from.trim();
    }

    public void sendAsync(String to, String subject, String body) {
        sendHtmlAsync(to, subject, body, null);
    }

    /** Sends an email with an HTML version (button, styling) and a plain-text fallback. */
    public void sendHtmlAsync(String to, String subject, String plainText, String html) {
        if (to == null || to.isBlank()) return;
        CompletableFuture.runAsync(() -> send(to, subject, plainText, html));
    }

    private void send(String to, String subject, String body, String html) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || username.isEmpty()) {
            log.warn("Mail is not configured (set MAIL_USERNAME / MAIL_PASSWORD). Email NOT sent.\nTo: {}\nSubject: {}\n{}",
                    to, subject, body);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from.isEmpty() ? username : from);
            helper.setTo(to);
            helper.setSubject(subject);
            if (html == null) {
                helper.setText(body, false);
            } else {
                helper.setText(body, html); // plain text + HTML alternative
            }
            sender.send(message);
            log.info("Email sent to {} ({})", to, subject);
        } catch (Exception e) {
            log.error("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}
