package com.bootleg.brevo.runtime.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table(schema = "bootleg_runtime", name = "obs_application")
public record ObsApplicationEntity(
  @Id
  @Column("application_id")
  UUID applicationId,

  @Column("journey_id")
  UUID journeyId,

  @Column("journey_code")
  String journeyCode,

  @Column("customer_ref")
  String customerRef,

  @Column("status")
  String status,

  @Column("current_group_no")
  Integer currentGroupNo,

  @Column("furthest_group_no")
  Integer furthestGroupNo,

  @Column("version")
  Integer version,

  @Column("created_at")
  OffsetDateTime createdAt,

  @Column("updated_at")
  OffsetDateTime updatedAt
) {
}
