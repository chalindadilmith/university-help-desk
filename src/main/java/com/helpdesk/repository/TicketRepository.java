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

    long countByStatus(TicketStatus status);

    /**
     * A staff member's queue: tickets already assigned to them, plus
     * unassigned tickets whose category's work-pool they belong to
     * (UC-F2-01 step 4-5: tickets are routed into a pool, then a staff
     * member from that pool takes ownership).
     */
    @Query("SELECT t FROM Ticket t WHERE t.assignedStaff.id = :staffId " +
           "OR (t.assignedStaff IS NULL AND t.category.pool IN " +
           "    (SELECT p FROM JobPool p JOIN p.staffMembers s WHERE s.id = :staffId)) " +
           "ORDER BY t.createdAt DESC")
    List<Ticket> findQueueForStaff(@Param("staffId") Long staffId);
}
