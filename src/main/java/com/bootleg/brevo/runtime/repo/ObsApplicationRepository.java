package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.ObsApplicationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

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

  @Query("""
      SELECT *
      FROM bootleg_runtime.obs_application
      WHERE customer_ref = :customerRef
        AND journey_id = :journeyId
        AND status IN ('IN_PROGRESS', 'READY_FOR_FINALISATION')
      ORDER BY updated_at DESC
      LIMIT 1
    """)
  Mono<ObsApplicationEntity> findOpenByCustomerRef(String customerRef, UUID journeyId);


  /**
   * Use this for "create new application" instead of save().
   * With assigned UUIDs, save() may try UPDATE and fail if row doesn't exist.
   */
  @Query("""
      INSERT INTO bootleg_runtime.obs_application(
        application_id,
        journey_id,
        journey_code,
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
        :journeyCode,
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
        journey_code,
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
    String journeyCode,
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

  // Simple update (runtime-only flow) – increments version for visibility
  @Query("""
      UPDATE bootleg_runtime.obs_application
      SET status = :status,
          current_group_no = :currentGroupNo,
          updated_at = now(),
          version = version + 1
      WHERE application_id = :applicationId
    """)
  Mono<Integer> updateProgressSimple(UUID applicationId, String status, int currentGroupNo);

  // Optional: mark application updated without changing progress (handy for heartbeats/touches)
  @Query("""
      UPDATE bootleg_runtime.obs_application
      SET updated_at = now()
      WHERE application_id = :applicationId
    """)
  Mono<Integer> touch(UUID applicationId);

  //TODO: Restarting
  @Query("""
      SELECT *
      FROM bootleg_runtime.obs_application
      WHERE customer_ref = :customerRef
        AND journey_id = :journeyId
        AND status IN ('IN_PROGRESS', 'READY_FOR_FINALISATION')
      ORDER BY updated_at DESC
      LIMIT 1
    """)
  Mono<ObsApplicationEntity> findLatestOpenByCustomerRef(String customerRef, UUID journeyId);

  @Query("""
      UPDATE bootleg_runtime.obs_application
      SET status = :status,
          updated_at = now(),
          version = version + 1
      WHERE application_id = :applicationId
    """)
  Mono<Integer> updateStatus(UUID applicationId, String status);


}
