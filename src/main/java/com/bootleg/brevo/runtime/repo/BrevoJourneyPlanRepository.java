package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.BrevoJourneyEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface BrevoJourneyPlanRepository extends R2dbcRepository<BrevoJourneyEntity, UUID> {

  @Query("""
      SELECT jg.position AS position,
             g.group_no  AS group_no
      FROM brevo_config.journey_tm j
      JOIN brevo_config.journey_group_tr jg ON jg.journey_id = j.journey_id
      JOIN brevo_config.group_tm g ON g.group_id = jg.group_id
      WHERE j.journey_code = :journeyCode
      ORDER BY jg.position
    """)
  Flux<JourneyGroupRow> fetchPlan(String journeyCode);

  record JourneyGroupRow(int position, int groupNo) {
  }
}
