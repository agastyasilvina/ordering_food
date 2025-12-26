package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.ObsGroupStateEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ObsGroupStateRepository extends ReactiveCrudRepository<ObsGroupStateEntity, UUID> {

  @Query("""
      SELECT application_id, group_no, status,
             payload::text AS payload,
             submission_version, created_at, updated_at
      FROM bootleg_runtime.obs_group_state
      WHERE application_id = :applicationId
      ORDER BY group_no
    """)
  Flux<ObsGroupStateEntity> findAllByApplicationId(UUID applicationId);

  /**
   * Upsert a VALIDATED payload (runtime-only: validation skipped)
   * payload is stored as jsonb, but we pass it as text and cast to jsonb.
   */
  @Query("""
      INSERT INTO bootleg_runtime.obs_group_state(
        application_id, group_no, status, payload, submission_version, created_at, updated_at
      )
      VALUES (
        :applicationId, :groupNo, 'VALIDATED', CAST(:payloadJson AS jsonb), 1, now(), now()
      )
      ON CONFLICT (application_id, group_no)
      DO UPDATE SET
        status = 'VALIDATED',
        payload = CAST(:payloadJson AS jsonb),
        submission_version = bootleg_runtime.obs_group_state.submission_version + 1,
        updated_at = now()
    """)
  Mono<Void> upsertValidated(UUID applicationId, int groupNo, String payloadJson);

  @Query("""
      UPDATE bootleg_runtime.obs_group_state
      SET status = 'INVALIDATED',
          updated_at = now()
      WHERE application_id = :applicationId
        AND group_no > :fromGroupNo
        AND status = 'VALIDATED'
    """)
  Mono<Integer> invalidateAfter(UUID applicationId, int fromGroupNo);

  /**
   * Upsert an INVALIDATED payload (DEV/testing).
   * This simulates "validation failed" before real validation exists.
   */
  @Query("""
      INSERT INTO bootleg_runtime.obs_group_state(
        application_id, group_no, status, payload, submission_version, created_at, updated_at
      )
      VALUES (
        :applicationId, :groupNo, 'INVALIDATED', CAST(:payloadJson AS jsonb), 1, now(), now()
      )
      ON CONFLICT (application_id, group_no)
      DO UPDATE SET
        status = 'INVALIDATED',
        payload = CAST(:payloadJson AS jsonb),
        submission_version = bootleg_runtime.obs_group_state.submission_version + 1,
        updated_at = now()
    """)
  Mono<Void> upsertInvalidated(UUID applicationId, int groupNo, String payloadJson);


  // Safer invalidation helper for non-sequential group numbers (use with journey plan ordering)
  @Query("""
      UPDATE bootleg_runtime.obs_group_state
      SET status = 'INVALIDATED',
          updated_at = now()
      WHERE application_id = :applicationId
        AND group_no = :groupNo
        AND status = 'VALIDATED'
    """)
  Mono<Integer> invalidateOne(UUID applicationId, int groupNo);
}
