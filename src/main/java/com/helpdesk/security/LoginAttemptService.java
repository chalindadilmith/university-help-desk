package com.helpdesk.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing. After too many failed logins for one email address
 * (or from one network address) further attempts are refused for a cool-down period,
 * even if the right password is then supplied. State is kept in memory, so it resets
 * when the application restarts - fine for a single-server deployment.
 *
 * The same lock applies whether or not the email belongs to a real account, so the
 * lock itself does not reveal which accounts exist.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES_PER_EMAIL = 5;
    private static final int MAX_FAILURES_PER_IP = 30;
    private static final long WINDOW_MS = Duration.ofMinutes(15).toMillis();
    private static final long LOCK_MS = Duration.ofMinutes(15).toMillis();
    private static final int PRUNE_AT_SIZE = 5000;

    private static final class Attempts {
        int failures;
        long windowStart;
        long lockedUntil;
    }

    private final ConcurrentHashMap<String, Attempts> records = new ConcurrentHashMap<>();

    public boolean isBlocked(String email, String ip) {
        long now = System.currentTimeMillis();
        return locked("u:" + normalise(email), now) || locked("ip:" + ip, now);
    }

    public void recordFailure(String email, String ip) {
        long now = System.currentTimeMillis();
        fail("u:" + normalise(email), MAX_FAILURES_PER_EMAIL, now);
        fail("ip:" + ip, MAX_FAILURES_PER_IP, now);
        if (records.size() > PRUNE_AT_SIZE) {
            records.entrySet().removeIf(e -> expired(e.getValue(), now));
        }
    }

    public void recordSuccess(String email) {
        records.remove("u:" + normalise(email));
    }

    /** Minutes the visitor should wait, for the message shown on the login page. */
    public static long lockMinutes() {
        return Duration.ofMillis(LOCK_MS).toMinutes();
    }

    private boolean locked(String key, long now) {
        Attempts r = records.get(key);
        return r != null && r.lockedUntil > now;
    }

    private void fail(String key, int max, long now) {
        records.compute(key, (k, r) -> {
            if (r == null || expired(r, now)) {
                r = new Attempts();
                r.windowStart = now;
            }
            r.failures++;
            if (r.failures >= max) {
                r.lockedUntil = now + LOCK_MS;
                r.failures = 0;
                r.windowStart = now;
            }
            return r;
        });
    }

    private static boolean expired(Attempts r, long now) {
        return r.lockedUntil <= now && now - r.windowStart > WINDOW_MS;
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
