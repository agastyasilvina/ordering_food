package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.ObsApplicationEntity;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ObsApplicationRepository extends ReactiveCrudRepository<ObsApplicationEntity, UUID> {

  @Query("""
    SELECT *
    FROM bootleg_runtime.obs_application
    WHERE customer_ref = :customerRef
      AND journey_id = :journeyId
      AND status = 'IN_PROGRESS'
    LIMIT 1
  """)
  Mono<ObsApplicationEntity> findActiveByCustomerRef(String customerRef, UUID journeyId);

  /**
   * Use this for "create new application" instead of save().
   * With assigned UUIDs, save() may try UPDATE and fail if row doesn't exist.
   */
  @Query("""
    INSERT INTO bootleg_runtime.obs_application(
      application_id,
      journey_id,
      customer_ref,
      status,
      current_group_no,
      furthest_group_no,
      version,
      created_at,
      updated_at
    )
    VALUES (
      :applicationId,
      :journeyId,
      :customerRef,
      :status,
      :currentGroupNo,
      :furthestGroupNo,
      0,
      now(),
      now()
    )
    RETURNING
      application_id,
      journey_id,
      customer_ref,
      status,
      current_group_no,
      furthest_group_no,
      version,
      created_at,
      updated_at
  """)
  Mono<ObsApplicationEntity> insertNew(
    UUID applicationId,
    UUID journeyId,
    String customerRef,
    String status,
    int currentGroupNo,
    int furthestGroupNo
  );

  // Pessimistic lock helper (use inside a transaction)
  @Query("""
    SELECT *
    FROM bootleg_runtime.obs_application
    WHERE application_id = :applicationId
    FOR UPDATE
  """)
  Mono<ObsApplicationEntity> lockById(UUID applicationId);

  // Optimistic lock helper (returns rows updated = 1 if success, else 0)
  @Query("""
    UPDATE bootleg_runtime.obs_application
    SET status = :status,
        current_group_no = :currentGroupNo,
        furthest_group_no = :furthestGroupNo,
        version = version + 1,
        updated_at = now()
    WHERE application_id = :applicationId
      AND version = :expectedVersion
  """)
  Mono<Integer> updateProgressWithVersion(
    UUID applicationId,
    int expectedVersion,
    String status,
    int currentGroupNo,
    int furthestGroupNo
  );

  // Optional: mark application updated without changing progress (handy for heartbeats/touches)
  @Query("""
    UPDATE bootleg_runtime.obs_application
    SET updated_at = now()
    WHERE application_id = :applicationId
  """)
  Mono<Integer> touch(UUID applicationId);
}
