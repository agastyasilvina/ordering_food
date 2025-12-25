package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.GroupSubmissionEntity;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GroupSubmissionRepository extends ReactiveCrudRepository<GroupSubmissionEntity, UUID> {

  @Query("""
    SELECT application_id, group_no, payload_json::text AS payload_json, version, updated_at
    FROM group_submission
    WHERE application_id = :applicationId
    ORDER BY group_no
  """)
  Flux<GroupSubmissionEntity> findAllByApplicationId(UUID applicationId);

  @Query("""
    SELECT application_id, group_no, payload_json::text AS payload_json, version, updated_at
    FROM group_submission
    WHERE application_id = :applicationId AND group_no = :groupNo
    LIMIT 1
  """)
  Mono<GroupSubmissionEntity> findOne(UUID applicationId, int groupNo);

  // Upsert: if (application_id, group_no) exists -> update payload + bump version
  @Query("""
    INSERT INTO group_submission(application_id, group_no, payload_json, version, updated_at)
    VALUES (:applicationId, :groupNo, CAST(:payloadJson AS jsonb), 1, now())
    ON CONFLICT (application_id, group_no)
    DO UPDATE SET
      payload_json = CAST(:payloadJson AS jsonb),
      version = group_submission.version + 1,
      updated_at = now()
  """)
  Mono<Void> upsert(UUID applicationId, int groupNo, String payloadJson);

  @Query("""
    DELETE FROM group_submission
    WHERE application_id = :applicationId AND group_no > :fromGroupNo
  """)
  Mono<Void> deleteAfter(UUID applicationId, int fromGroupNo);
}
