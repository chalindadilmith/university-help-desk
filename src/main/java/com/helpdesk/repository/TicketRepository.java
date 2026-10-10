package com.helpdesk.repository;

import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Ticket> findAllByOrderByCreatedAtDesc();

    /** Everything a staff member is (or was) handling - used by the member report. */
    List<Ticket> findByAssignedStaffIdOrderByCreatedAtDesc(Long staffId);

    long countByStatus(TicketStatus status);

    /**
     * A staff member's queue: tickets already assigned to them, plus
     * unassigned tickets whose category's work-pool they belong to
     * (UC-F2-01 step 4-5: tickets are routed into a pool, then a staff
     * member from that pool takes ownership).
     */
    @Query("SELECT t FROM Ticket t WHERE t.assignedStaff.id = :staffId " +
           "OR (t.assignedStaff IS NULL AND (" +
           "    (t.routedPool IS NOT NULL AND t.routedPool IN " +
           "        (SELECT p FROM JobPool p JOIN p.staffMembers s WHERE s.id = :staffId)) " +
           " OR (t.routedPool IS NULL AND t.category.pool IN " +
           "        (SELECT p2 FROM JobPool p2 JOIN p2.staffMembers s2 WHERE s2.id = :staffId)))) " +
           "ORDER BY t.createdAt DESC")
    List<Ticket> findQueueForStaff(@Param("staffId") Long staffId);

    /** Workload shown when transferring a ticket: how many unfinished tickets each staff member holds. */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.assignedStaff.id = :staffId " +
           "AND t.status IN (com.helpdesk.entity.TicketStatus.OPEN, com.helpdesk.entity.TicketStatus.IN_PROGRESS, " +
           "com.helpdesk.entity.TicketStatus.ESCALATED, com.helpdesk.entity.TicketStatus.REOPENED)")
    long countActiveForStaff(@Param("staffId") Long staffId);
}
