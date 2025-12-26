package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.ObsSessionEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ObsSessionRepository extends ReactiveCrudRepository<ObsSessionEntity, UUID> {

  @Query("""
    INSERT INTO bootleg_runtime.obs_session(
      session_id,
      application_id,
      status,
      expires_at,
      created_at,
      updated_at
    )
    VALUES (
      :sessionId,
      :applicationId,
      :status,
      :expiresAt,
      now(),
      now()
    )
    RETURNING
      session_id,
      application_id,
      status,
      expires_at,
      created_at,
      updated_at
  """)
  Mono<ObsSessionEntity> insertNew(
    UUID sessionId,
    UUID applicationId,
    String status,
    OffsetDateTime expiresAt
  );

  @Query("""
    SELECT session_id, application_id, status, expires_at, created_at, updated_at
    FROM bootleg_runtime.obs_session
    WHERE session_id = :sessionId
      AND status = 'ACTIVE'
      AND expires_at > now()
    LIMIT 1
  """)
  Mono<ObsSessionEntity> findActive(UUID sessionId);

  @Query("""
    UPDATE bootleg_runtime.obs_session
    SET expires_at = :newExpiresAt,
        updated_at = now()
    WHERE session_id = :sessionId
      AND status = 'ACTIVE'
  """)
  Mono<Integer> touch(UUID sessionId, OffsetDateTime newExpiresAt);

  @Query("""
    UPDATE bootleg_runtime.obs_session
    SET status = 'EXPIRED',
        updated_at = now()
    WHERE session_id = :sessionId
      AND status = 'ACTIVE'
  """)
  Mono<Integer> markExpired(UUID sessionId);

  @Query("""
    SELECT session_id, application_id, status, expires_at, created_at, updated_at
    FROM bootleg_runtime.obs_session
    WHERE application_id = :applicationId
    ORDER BY created_at DESC
  """)
  Flux<ObsSessionEntity> findAllByApplicationId(UUID applicationId);
}
