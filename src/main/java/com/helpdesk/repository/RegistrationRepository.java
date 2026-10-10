package com.helpdesk.repository;

import com.helpdesk.entity.Registration;
import com.helpdesk.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    Optional<Registration> findByStudentIdAndSemesterId(Long studentId, Long semesterId);

    List<Registration> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    long countByStatus(RegistrationStatus status);

    List<Registration> findByReferenceNoIsNull();

    /** Used to block deleting an offering that students have already registered for. */
    boolean existsByCourseOfferings_Id(Long offeringId);

    /** Admin approval list, optionally filtered by semester and/or status. */
    @Query("select r from Registration r where (:semesterId is null or r.semester.id = :semesterId) " +
           "and (:status is null or r.status = :status) order by r.createdAt desc")
    List<Registration> search(@Param("semesterId") Long semesterId, @Param("status") RegistrationStatus status);

    /**
     * Seats taken in an offering by registrations in the given statuses,
     * optionally ignoring one registration (the one being edited/approved).
     */
    @Query("select count(r) from Registration r join r.courseOfferings o where o.id = :offeringId " +
           "and r.status in :statuses and (:excludeId is null or r.id <> :excludeId)")
    long countSeatsTaken(@Param("offeringId") Long offeringId,
                         @Param("statuses") Collection<RegistrationStatus> statuses,
                         @Param("excludeId") Long excludeId);
}
