package com.bootleg.brevo.runtime.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(schema = "brevo_config", name = "journey_tm")
public record BrevoJourneyEntity(
  @Id
  @Column("journey_id")
  UUID journeyId,

  @Column("journey_code")
  String journeyCode,

  @Column("journey_name")
  String journeyName
) {
}
