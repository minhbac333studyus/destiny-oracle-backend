package com.destinyoracle.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_insights", uniqueConstraints = {
    @UniqueConstraint(name = "uq_insight_user_date", columnNames = {"userId", "insightDate"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate insightDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;  // AI-generated summary

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String suggestions;  // JSON array of suggestions

    @Column(nullable = false)
    @Builder.Default
    private Integer tasksCompleted = 0;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
