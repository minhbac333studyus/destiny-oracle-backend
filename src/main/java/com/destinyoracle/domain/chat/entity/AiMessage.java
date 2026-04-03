package com.destinyoracle.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_messages", indexes = {
    @Index(name = "idx_ai_msg_conv", columnList = "conversation_id, createdAt"),
    @Index(name = "idx_ai_msg_compressed", columnList = "conversation_id, compressed")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversation conversation;

    @Column(nullable = false, length = 20)
    private String role;  // "USER" or "ASSISTANT"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 50)
    private String actionType;  // null, "TASK", "REMINDER", "LIST", "INSIGHT"

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String actionPayload;

    @Column(nullable = false)
    @Builder.Default
    private Boolean compressed = false;

    @Column(name = "compression_memory_id")
    private UUID compressionMemoryId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
