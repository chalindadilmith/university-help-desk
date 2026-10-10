package com.helpdesk.service;

import com.helpdesk.entity.Role;
import com.helpdesk.entity.Semester;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.User;
import com.helpdesk.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds a SHORT plain-text summary of ONE student's tickets so the chatbot can answer
 * "what is the status of my ticket?".
 *
 * Privacy: this text is sent to an external AI service, so it is deliberately minimal.
 * It contains only the ticket reference, category, status, priority and dates. It does NOT
 * contain the student's name, email or ID, ticket subjects or descriptions, staff replies or
 * staff names, notifications, Lost &amp; Found details, or registration/module details.
 * Questions about those are answered with a pointer to the relevant page instead.
 */
@Service
public class StudentContextService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final int MAX_TICKETS = 8;

    private final TicketRepository ticketRepository;
    private final RegistrationService registrationService;

    public StudentContextService(TicketRepository ticketRepository, RegistrationService registrationService) {
        this.ticketRepository = ticketRepository;
        this.registrationService = registrationService;
    }

    @Transactional(readOnly = true)
    public String buildFor(User student) {
        if (student == null || student.getRole() != Role.STUDENT) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        List<Ticket> tickets = ticketRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
        sb.append("Tickets (").append(tickets.size()).append(" total, newest first):\n");
        if (tickets.isEmpty()) {
            sb.append("- none\n");
        }
        tickets.stream().limit(MAX_TICKETS).forEach(t -> sb
                .append("- ").append(t.getTicketRef())
                .append(" | category ").append(t.getCategory().getName())
                .append(" | status ").append(t.getStatus())
                .append(" | priority ").append(t.getPriority())
                .append(" | opened ").append(t.getCreatedAt().format(DATE))
                .append(t.getAssignedStaff() == null ? " | not yet assigned" : " | assigned to a staff member")
                .append("\n"));

        List<Semester> open = registrationService.openSemesters();
        sb.append("\nSemesters currently open for registration (public information): ")
          .append(open.isEmpty() ? "none" : open.stream().map(Semester::getName).collect(Collectors.joining(", ")))
          .append("\n");
        return sb.toString();
    }
}
