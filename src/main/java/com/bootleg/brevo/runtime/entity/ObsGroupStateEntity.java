package com.bootleg.brevo.runtime.entity;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table(schema = "bootleg_runtime", name = "obs_group_state")
public record ObsGroupStateEntity(
  @Column("application_id")
  UUID applicationId,

  @Column("group_no")
  Integer groupNo,

  @Column("status")
  String status,                 // VALIDATED | INVALIDATED

  @Column("payload")
  String payloadJson,            // selected as payload::text

  @Column("submission_version")
  Integer submissionVersion,

  @Column("created_at")
  OffsetDateTime createdAt,

  @Column("updated_at")
  OffsetDateTime updatedAt
) {
}
