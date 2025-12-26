package com.bootleg.brevo.runtime.entity;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("group_submission")
public record GroupSubmissionEntity(
  @Column("application_id")
  UUID applicationId,

  @Column("group_no")
  Integer groupNo,

  @Column("payload_json")
  String payloadJson,          // keep as String for now; store JSON text into JSONB

  @Column("version")
  Integer version,

  @Column("updated_at")
  OffsetDateTime updatedAt
) {
}
