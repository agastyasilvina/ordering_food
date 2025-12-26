package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.BrevoJourneyEntity;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface BrevoJourneyConfigRepository extends R2dbcRepository<BrevoJourneyEntity, UUID> {

  @Query("""
    SELECT journey_id
    FROM brevo_config.journey_tm
    WHERE journey_code = :journeyCode
    LIMIT 1
  """)
  Mono<UUID> findJourneyIdByCode(String journeyCode);
}
