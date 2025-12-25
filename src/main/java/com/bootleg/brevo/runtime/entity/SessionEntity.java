package com.bootleg.brevo.runtime.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("session")
public record SessionEntity(
  @Id
  @Column("session_id")
  UUID sessionId,

  @Column("application_id")
  UUID applicationId,

  @Column("status")
  String status,

  @Column("created_at")
  OffsetDateTime createdAt,

  @Column("ended_at")
  OffsetDateTime endedAt
) {}
