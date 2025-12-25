package com.bootleg.brevo.runtime.repo;

import com.bootleg.brevo.runtime.entity.ApplicationEntity;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ApplicationRepository extends ReactiveCrudRepository<ApplicationEntity, UUID> {

  @Query("""
    SELECT *
    FROM application
    WHERE customer_id = :customerId
      AND journey_code = :journeyCode
      AND status = 'IN_PROGRESS'
    LIMIT 1
  """)
  Mono<ApplicationEntity> findActiveByCustomer(String customerId, String journeyCode);

  @Query("""
    SELECT *
    FROM application
    WHERE client_id = :clientId
      AND journey_code = :journeyCode
      AND status = 'IN_PROGRESS'
    LIMIT 1
  """)
  Mono<ApplicationEntity> findActiveByClient(String clientId, String journeyCode);
}
