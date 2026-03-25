package com.destinyoracle.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_memories", indexes = {
    @Index(name = "idx_conv_mem_conv", columnList = "conversationId")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID conversationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private Integer messagesCompressed;

    @Column(nullable = false)
    private Integer tokenEstimate;

    @Column(nullable = false)
    private Integer compressionRound;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
