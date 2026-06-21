package com.meetingcost.repository;

import com.meetingcost.entity.SalaryLookup;
import com.meetingcost.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryLookupRepository extends JpaRepository<SalaryLookup, UUID> {

    // Get user-specific overrides (user-set salary for a title)
    List<SalaryLookup> findByUser(User user);

    // Get global defaults (user is null = system defaults)
    List<SalaryLookup> findByUserIsNull();

    // Find exact match by keyword (case-insensitive)
    Optional<SalaryLookup> findByUserIsNullAndTitleKeywordIgnoreCase(String titleKeyword);
}