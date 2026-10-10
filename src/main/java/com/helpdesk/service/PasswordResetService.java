package com.helpdesk.service;

import com.helpdesk.entity.AccountStatus;
import com.helpdesk.entity.PasswordResetToken;
import com.helpdesk.entity.User;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.repository.PasswordResetTokenRepository;
import com.helpdesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/** "Forgot password": emails a single-use link (valid 30 minutes) that lets the user choose a new password. */
@Service
public class PasswordResetService {

    private static final int VALIDITY_MINUTES = 30;
    private static final int MIN_SECONDS_BETWEEN_REQUESTS = 60;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder, EmailService emailService,
                                AuditLogService auditLogService,
                                @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Never reveals whether the email exists - the caller always shows the same message. */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) return;
        Optional<User> found = userRepository.findByEmail(email.trim());
        if (found.isEmpty() || found.get().getStatus() != AccountStatus.ACTIVE) return;
        User user = found.get();

        var open = tokenRepository.findByUserIdAndUsedFalse(user.getId());
        LocalDateTime now = LocalDateTime.now();
        boolean tooSoon = open.stream().anyMatch(t -> t.getCreatedAt() != null
                && t.getCreatedAt().isAfter(now.minusSeconds(MIN_SECONDS_BETWEEN_REQUESTS)));
        if (tooSoon) return;
        open.forEach(t -> t.setUsed(true)); // older links stop working

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokenRepository.save(PasswordResetToken.builder()
                .user(user).tokenHash(hash(token)).expiresAt(now.plusMinutes(VALIDITY_MINUTES)).build());
        auditLogService.log(user, "PASSWORD_RESET_REQUESTED", "User", user.getId(), "Forgot-password email requested");

        String link = baseUrl + "/reset-password?token=" + token;
        String plain = "Hello " + user.getFullName() + ",\n\n"
                + "We received a request to reset your University Help Desk password.\n"
                + "Open this link to choose a new password (valid for " + VALIDITY_MINUTES + " minutes, one use only):\n\n"
                + link + "\n\n"
                + "If you did not ask for this, you can ignore this email - your password stays the same.\n\n"
                + "University Help Desk";
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;background:#f4f5f9;padding:24px\">"
                + "<div style=\"max-width:480px;margin:0 auto;background:#ffffff;border-radius:12px;padding:32px;color:#1f2430\">"
                + "<h2 style=\"margin:0 0 16px;color:#3b25b8\">University Help Desk</h2>"
                + "<p>Hello " + escape(user.getFullName()) + ",</p>"
                + "<p>We received a request to reset your password. Click the button below to choose a new one.</p>"
                + "<p style=\"text-align:center;margin:28px 0\">"
                + "<a href=\"" + link + "\" style=\"background:#5b45e6;color:#ffffff;text-decoration:none;"
                + "padding:14px 28px;border-radius:8px;font-weight:bold;display:inline-block\">Reset my password</a></p>"
                + "<p style=\"font-size:13px;color:#5b6070\">This link works for " + VALIDITY_MINUTES
                + " minutes and can be used once.</p>"
                + "<p style=\"font-size:13px;color:#5b6070\">If the button does not work, copy this address into your browser:<br>"
                + "<a href=\"" + link + "\" style=\"color:#5b45e6;word-break:break-all\">" + link + "</a></p>"
                + "<hr style=\"border:none;border-top:1px solid #e3e5ec;margin:24px 0\">"
                + "<p style=\"font-size:12px;color:#8a8fa0\">If you did not ask for this, you can ignore this email - your password stays the same.</p>"
                + "</div></div>";
        emailService.sendHtmlAsync(user.getEmail(), "Reset your University Help Desk password", plain, html);
    }

    @Transactional(readOnly = true)
    public boolean isValid(String token) {
        return find(token).isPresent();
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        PasswordResetToken t = find(token).orElseThrow(() ->
                new BusinessRuleException("This reset link is invalid or has expired. Please request a new one."));
        String policyProblem = com.helpdesk.util.PasswordPolicy.check(newPassword);
        if (policyProblem != null) {
            throw new BusinessRuleException(policyProblem);
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessRuleException("The new password and its confirmation do not match.");
        }
        User user = t.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        t.setUsed(true);
        tokenRepository.findByUserIdAndUsedFalse(user.getId()).forEach(o -> o.setUsed(true));
        auditLogService.log(user, "PASSWORD_RESET_COMPLETED", "User", user.getId(), "Password reset via email link");
    }

    private Optional<PasswordResetToken> find(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return tokenRepository.findByTokenHash(hash(token.trim()))
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
