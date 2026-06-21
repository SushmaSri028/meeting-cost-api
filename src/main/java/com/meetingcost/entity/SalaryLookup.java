package com.meetingcost.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "salary_lookup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // NULL user_id = global default; non-null = user's personal override
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "title_keyword", nullable = false, length = 255)
    private String titleKeyword;       // e.g., "Senior Engineer"

    @Column(name = "annual_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal annualSalary;   // e.g., 160000.00
}