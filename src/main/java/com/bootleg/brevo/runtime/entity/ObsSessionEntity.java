package com.bootleg.brevo.runtime.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table(schema = "bootleg_runtime", name = "obs_session")
public record ObsSessionEntity(
  @Id
  @Column("session_id")
  UUID sessionId,

  @Column("application_id")
  UUID applicationId,

  @Column("status")
  String status,                 // ACTIVE | EXPIRED | CLOSED

  @Column("expires_at")
  OffsetDateTime expiresAt,

  @Column("created_at")
  OffsetDateTime createdAt,

  @Column("updated_at")
  OffsetDateTime updatedAt
) {
}
