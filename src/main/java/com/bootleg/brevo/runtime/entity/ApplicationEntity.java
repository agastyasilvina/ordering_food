package com.bootleg.brevo.runtime.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("application")
public record ApplicationEntity(
  @Id
  @Column("application_id")
  UUID applicationId,

  @Column("journey_code")
  String journeyCode,

  @Column("customer_id")
  String customerId,

  @Column("client_id")
  String clientId,

  @Column("current_group_no")
  Integer currentGroupNo,

  @Column("furthest_group_no")
  Integer furthestGroupNo,

  @Column("status")
  String status,

  @Column("created_at")
  OffsetDateTime createdAt,

  @Column("updated_at")
  OffsetDateTime updatedAt
) {}
