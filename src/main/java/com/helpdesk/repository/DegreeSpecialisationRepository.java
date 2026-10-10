package com.helpdesk.repository;

import com.helpdesk.entity.DegreeSpecialisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DegreeSpecialisationRepository extends JpaRepository<DegreeSpecialisation, Long> {
    List<DegreeSpecialisation> findAllByOrderByNameAsc();
    List<DegreeSpecialisation> findByActiveTrueOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
}
