package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.SessionEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SessionRepository extends ReactiveCrudRepository<SessionEntity, UUID> {

  @Query("""
    SELECT session_id, application_id, status, created_at, ended_at
    FROM session
    WHERE application_id = :applicationId
      AND status = 'ACTIVE'
    ORDER BY created_at DESC
    LIMIT 1
  """)
  Mono<SessionEntity> findActiveByApplicationId(UUID applicationId);

  @Query("""
    UPDATE session
    SET status = 'ENDED', ended_at = :endedAt
    WHERE application_id = :applicationId
      AND status = 'ACTIVE'
  """)
  Mono<Integer> endAllActiveByApplicationId(UUID applicationId, OffsetDateTime endedAt);

  @Query("""
    SELECT session_id, application_id, status, created_at, ended_at
    FROM session
    WHERE application_id = :applicationId
    ORDER BY created_at DESC
  """)
  Flux<SessionEntity> findAllByApplicationId(UUID applicationId);
}
