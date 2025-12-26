package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.ObsGroupStateEntity;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

  @Query("""
    SELECT application_id, group_no, status,
           payload::text AS payload,
           submission_version, created_at, updated_at
    FROM bootleg_runtime.obs_group_state
    WHERE application_id = :applicationId
      AND group_no = :groupNo
    LIMIT 1
  """)
  Mono<ObsGroupStateEntity> findOne(UUID applicationId, int groupNo);

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
      submission_version = obs_group_state.submission_version + 1,
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
}
