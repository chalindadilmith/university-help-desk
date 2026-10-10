package com.helpdesk.service;

import com.helpdesk.entity.AuditLog;
import com.helpdesk.entity.Feedback;
import com.helpdesk.entity.LostFoundClaim;
import com.helpdesk.entity.LostFoundItem;
import com.helpdesk.entity.Registration;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.entity.User;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.FeedbackRepository;
import com.helpdesk.repository.JobPoolRepository;
import com.helpdesk.repository.LostFoundClaimRepository;
import com.helpdesk.repository.LostFoundItemRepository;
import com.helpdesk.repository.RegistrationRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the full report for one member (student, staff or admin): every profile detail plus all of
 * their ticket, feedback, lost-and-found, registration and recent-activity records.
 */
@Service
public class UserReportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Everything shown on the member report page and in its CSV export. */
    public record UserReport(
            User user,
            List<String> pools,
            List<Ticket> tickets,
            Map<String, Long> ticketsByStatus,
            double averageResolutionHours,
            List<Feedback> feedbackGiven,
            List<Feedback> feedbackReceived,
            double averageRatingReceived,
            List<LostFoundItem> lostFoundItems,
            List<LostFoundClaim> claims,
            List<Registration> registrations,
            List<AuditLog> recentActivity,
            LocalDateTime generatedAt) {

        public boolean isStudent() { return user.getRole() == Role.STUDENT; }
    }

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final FeedbackRepository feedbackRepository;
    private final LostFoundItemRepository itemRepository;
    private final LostFoundClaimRepository claimRepository;
    private final RegistrationRepository registrationRepository;
    private final JobPoolRepository poolRepository;
    private final AuditLogService auditLogService;

    public UserReportService(UserRepository userRepository, TicketRepository ticketRepository,
                             FeedbackRepository feedbackRepository, LostFoundItemRepository itemRepository,
                             LostFoundClaimRepository claimRepository, RegistrationRepository registrationRepository,
                             JobPoolRepository poolRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.feedbackRepository = feedbackRepository;
        this.itemRepository = itemRepository;
        this.claimRepository = claimRepository;
        this.registrationRepository = registrationRepository;
        this.poolRepository = poolRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public UserReport build(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        boolean student = user.getRole() == Role.STUDENT;
        List<Ticket> tickets = student
                ? ticketRepository.findByStudentIdOrderByCreatedAtDesc(userId)
                : ticketRepository.findByAssignedStaffIdOrderByCreatedAtDesc(userId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            byStatus.put(s.name(), tickets.stream().filter(t -> t.getStatus() == s).count());
        }
        double avgHours = tickets.stream()
                .filter(t -> t.getResolvedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes())
                .average().orElse(0) / 60.0;

        List<Feedback> given = feedbackRepository.findByStudentId(userId);
        List<Feedback> received = feedbackRepository.findByStaffId(userId);
        double avgRating = received.stream().mapToInt(Feedback::getRating).average().orElse(0);

        List<String> pools = poolRepository.findByStaffMembersId(userId).stream()
                .map(p -> p.getName()).sorted().toList();

        return new UserReport(user, pools, tickets, byStatus, avgHours, given, received, avgRating,
                itemRepository.findByReporterIdOrderByCreatedAtDesc(userId),
                claimRepository.findByClaimantIdOrderByCreatedAtDesc(userId),
                registrationRepository.findByStudentIdOrderByCreatedAtDesc(userId),
                auditLogService.recentByActor(userId),
                LocalDateTime.now());
    }

    /** The same report as a CSV document (sections separated by blank lines). */
    @Transactional(readOnly = true)
    public String toCsv(Long userId) {
        UserReport r = build(userId);
        User u = r.user();
        StringBuilder sb = new StringBuilder();
        sb.append("MEMBER REPORT\n");
        row(sb, "Generated", r.generatedAt().format(FMT));
        row(sb, "Full name", u.getFullName());
        row(sb, "Role", u.getRole().name());
        row(sb, "Status", u.getStatus().name());
        row(sb, "University / staff ID", u.getUniversityId());
        row(sb, "Student ID", u.getStudentCode());
        row(sb, "Email", u.getEmail());
        row(sb, "Work pools", String.join("; ", r.pools()));
        row(sb, "Account created", fmt(u.getCreatedAt()));
        row(sb, "Last login", fmt(u.getLastLoginAt()));
        row(sb, "Must change password", String.valueOf(u.isMustChangePassword()));
        sb.append('\n');

        sb.append(r.isStudent() ? "TICKETS RAISED\n" : "TICKETS ASSIGNED\n");
        sb.append("Reference,Subject,Category,Priority,Status,Student,Assigned staff,Created,Resolved,Description\n");
        for (Ticket t : r.tickets()) {
            sb.append(csv(t.getTicketRef())).append(',').append(csv(t.getSubject())).append(',')
              .append(csv(t.getCategory().getName())).append(',').append(t.getPriority()).append(',')
              .append(t.getStatus()).append(',').append(csv(t.getStudent().getFullName())).append(',')
              .append(csv(t.getAssignedStaff() == null ? "" : t.getAssignedStaff().getFullName())).append(',')
              .append(fmt(t.getCreatedAt())).append(',').append(fmt(t.getResolvedAt())).append(',')
              .append(csv(t.getDescription())).append('\n');
        }
        sb.append('\n');

        sb.append("FEEDBACK GIVEN\nTicket,Staff,Rating,Comment,Date\n");
        for (Feedback f : r.feedbackGiven()) {
            sb.append(csv(f.getTicket().getTicketRef())).append(',').append(csv(f.getStaff().getFullName())).append(',')
              .append(f.getRating()).append(',').append(csv(f.getComment())).append(',').append(fmt(f.getCreatedAt())).append('\n');
        }
        sb.append('\n');

        sb.append("FEEDBACK RECEIVED\nTicket,Student,Rating,Comment,Date\n");
        for (Feedback f : r.feedbackReceived()) {
            sb.append(csv(f.getTicket().getTicketRef())).append(',').append(csv(f.getStudent().getFullName())).append(',')
              .append(f.getRating()).append(',').append(csv(f.getComment())).append(',').append(fmt(f.getCreatedAt())).append('\n');
        }
        sb.append('\n');

        sb.append("LOST & FOUND REPORTS\nType,Description,Location,Item date,Status,Reported\n");
        for (LostFoundItem i : r.lostFoundItems()) {
            sb.append(i.getItemType()).append(',').append(csv(i.getDescription())).append(',').append(csv(i.getLocation())).append(',')
              .append(i.getItemDate()).append(',').append(i.getStatus()).append(',').append(fmt(i.getCreatedAt())).append('\n');
        }
        sb.append('\n');

        sb.append("LOST & FOUND CLAIMS\nItem,Status,Proof,Submitted\n");
        for (LostFoundClaim c : r.claims()) {
            sb.append(csv(c.getItem().getDescription())).append(',').append(c.getStatus()).append(',')
              .append(csv(c.getProofDescription())).append(',').append(fmt(c.getCreatedAt())).append('\n');
        }
        sb.append('\n');

        sb.append("SEMESTER REGISTRATIONS\nReference,Semester,Year,Semester of study,Credits,Status,Submitted\n");
        for (Registration reg : r.registrations()) {
            sb.append(csv(reg.getReferenceNo())).append(',').append(csv(reg.getSemester().getName())).append(',')
              .append(reg.getAcademicYear() == null ? "" : reg.getAcademicYear()).append(',')
              .append(reg.getSemesterOfStudy() == null ? "" : reg.getSemesterOfStudy()).append(',')
              .append(reg.getTotalCredits() == null ? "" : reg.getTotalCredits()).append(',')
              .append(reg.getStatus()).append(',').append(fmt(reg.getCreatedAt())).append('\n');
        }
        sb.append('\n');

        sb.append("RECENT ACTIVITY (audit log)\nWhen,Action,Target,Details\n");
        for (AuditLog a : r.recentActivity()) {
            sb.append(fmt(a.getCreatedAt())).append(',').append(csv(a.getAction())).append(',')
              .append(csv(a.getTargetEntity() + (a.getTargetId() == null ? "" : " #" + a.getTargetId()))).append(',')
              .append(csv(a.getDetails())).append('\n');
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append(csv(label)).append(',').append(csv(value)).append('\n');
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }

    /** Quotes a CSV field and neutralises spreadsheet formulas (a leading = + - @ would be executed by Excel). */
    private static String csv(String value) {
        String v = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
