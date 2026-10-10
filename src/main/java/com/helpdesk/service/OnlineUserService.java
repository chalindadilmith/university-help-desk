package com.helpdesk.service;

import com.helpdesk.entity.User;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.security.CustomUserDetails;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Who is logged in right now, read from Spring Security's session registry. */
@Service
public class OnlineUserService {

    /** A user counts as "active now" when their last request was within this many minutes. */
    public static final int ACTIVE_WINDOW_MINUTES = 5;

    /** One logged-in person: how many browser sessions they have open and when they last did anything. */
    public record OnlineEntry(User user, int sessions, LocalDateTime lastActivity, boolean activeNow) { }

    private final SessionRegistry sessionRegistry;
    private final UserRepository userRepository;

    public OnlineUserService(SessionRegistry sessionRegistry, UserRepository userRepository) {
        this.sessionRegistry = sessionRegistry;
        this.userRepository = userRepository;
    }

    /** Everyone with at least one unexpired session, most recently active first. */
    public List<OnlineEntry> online() {
        List<OnlineEntry> result = new ArrayList<>();
        LocalDateTime activeCutoff = LocalDateTime.now().minusMinutes(ACTIVE_WINDOW_MINUTES);
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!(principal instanceof CustomUserDetails details)) {
                continue;
            }
            List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
            if (sessions.isEmpty()) {
                continue;
            }
            LocalDateTime last = sessions.stream()
                    .map(s -> LocalDateTime.ofInstant(Instant.ofEpochMilli(s.getLastRequest().getTime()), ZoneId.systemDefault()))
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            // reload so the name / status shown are current, not the copy cached in the session
            User user = userRepository.findById(details.getUser().getId()).orElse(details.getUser());
            result.add(new OnlineEntry(user, sessions.size(), last, last != null && last.isAfter(activeCutoff)));
        }
        result.sort((a, b) -> {
            if (a.lastActivity() == null || b.lastActivity() == null) {
                return a.lastActivity() == null ? (b.lastActivity() == null ? 0 : 1) : -1;
            }
            return b.lastActivity().compareTo(a.lastActivity());
        });
        return result;
    }

    public Set<Long> onlineUserIds() {
        Set<Long> ids = new HashSet<>();
        for (OnlineEntry entry : online()) {
            ids.add(entry.user().getId());
        }
        return ids;
    }
}
