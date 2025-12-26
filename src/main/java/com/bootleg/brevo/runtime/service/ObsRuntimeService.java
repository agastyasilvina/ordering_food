package com.bootleg.brevo.runtime.service;

import com.bootleg.brevo.runtime.config.ObsRuntimeProperties;
import com.bootleg.brevo.runtime.entity.ObsApplicationEntity;
import com.bootleg.brevo.runtime.entity.ObsSessionEntity;
import com.bootleg.brevo.runtime.repo.ObsApplicationRepository;
import com.bootleg.brevo.runtime.repo.ObsSessionRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ObsRuntimeService {

  private final ObsApplicationRepository appRepo;
  private final ObsSessionRepository sessionRepo;
  private final ObsRuntimeProperties props;

  public ObsRuntimeService(
    ObsApplicationRepository appRepo,
    ObsSessionRepository sessionRepo,
    ObsRuntimeProperties props
  ) {
    this.appRepo = appRepo;
    this.sessionRepo = sessionRepo;
    this.props = props;
  }

  public Mono<StartJourneyResult> startJourney(UUID journeyId, String customerRef) {
    OffsetDateTime now = OffsetDateTime.now();

    // TODO: validate journeyId exists in config schema (later)

    return appRepo.findActiveByCustomerRef(customerRef, journeyId)
      .switchIfEmpty(Mono.defer(() -> createNewApplication(journeyId, customerRef)))
      .flatMap(app -> createSession(app, now)
        .map(sess -> new StartJourneyResult(
          sess.sessionId(),
          app.applicationId(),
          app.journeyId(),
          app.customerRef(),
          app.currentGroupNo(),
          app.status(),
          sess.expiresAt()
        )));
  }

  private Mono<ObsApplicationEntity> createNewApplication(UUID journeyId, String customerRef) {
    UUID appId = UUID.randomUUID();

    return appRepo.insertNew(
      appId,
      journeyId,
      customerRef,
      "IN_PROGRESS",
      0,   // current_group_no = last validated group; start at 0
      0    // furthest_group_no optional; keep 0 for now
    );
  }

  private Mono<ObsSessionEntity> createSession(ObsApplicationEntity app, OffsetDateTime now) {
    UUID sessionId = UUID.randomUUID();
    OffsetDateTime expiresAt = now.plus(props.sessionTtl());

    return sessionRepo.insertNew(
      sessionId,
      app.applicationId(),
      "ACTIVE",
      expiresAt
    );
  }

  public record StartJourneyResult(
    UUID sessionId,
    UUID applicationId,
    UUID journeyId,
    String customerRef,
    int currentGroupNo,
    String applicationStatus,
    OffsetDateTime sessionExpiresAt
  ) {}
}
