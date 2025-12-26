package com.bootleg.brevo.runtime.service;

import com.bootleg.brevo.runtime.config.ObsRuntimeProperties;
import com.bootleg.brevo.runtime.entity.ObsApplicationEntity;
import com.bootleg.brevo.runtime.entity.ObsSessionEntity;
import com.bootleg.brevo.runtime.repo.BrevoJourneyConfigRepository;
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
  private final BrevoJourneyConfigRepository journeyConfigRepo;
  private final JourneyPlanService journeyPlanService;
  private final ObsRuntimeProperties props;

  public ObsRuntimeService(
    ObsApplicationRepository appRepo,
    ObsSessionRepository sessionRepo,
    BrevoJourneyConfigRepository journeyConfigRepo,
    JourneyPlanService journeyPlanService,
    ObsRuntimeProperties props
  ) {
    this.appRepo = appRepo;
    this.sessionRepo = sessionRepo;
    this.journeyConfigRepo = journeyConfigRepo;
    this.journeyPlanService = journeyPlanService;
    this.props = props;
  }

  /**
   * Start/resume a journey by journeyCode + customerRef.
   * - Warms journey plan cache (brevo_config) so submit flow won't keep querying config.
   * - Resolves journeyId from journeyCode.
   * - Reuses existing IN_PROGRESS application if any; otherwise creates new.
   * - Creates a new ACTIVE session every call (sliding TTL handled elsewhere).
   */
  public Mono<StartJourneyResult> startJourney(String journeyCode, String customerRef) {
    OffsetDateTime now = OffsetDateTime.now();

    return journeyPlanService.warmUp(journeyCode) // <-- this is the line you asked about
      .then(journeyConfigRepo.findJourneyIdByCode(journeyCode))
      .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid journeyCode: " + journeyCode)))
      .flatMap(journeyId ->
        appRepo.findActiveByCustomerRef(customerRef, journeyId)
          .switchIfEmpty(Mono.defer(() -> createNewApplication(journeyId, journeyCode, customerRef)))
          .flatMap(app -> createSession(app, now)
            .map(sess -> new StartJourneyResult(
              sess.sessionId(),
              app.applicationId(),
              app.journeyId(),
              app.journeyCode(),
              app.customerRef(),
              app.currentGroupNo(),
              app.status(),
              sess.expiresAt()
            )))
      );
  }

  private Mono<ObsApplicationEntity> createNewApplication(UUID journeyId, String journeyCode, String customerRef) {
    return appRepo.insertNew(
      UUID.randomUUID(),
      journeyId,
      journeyCode,
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
    String journeyCode,
    String customerRef,
    int currentGroupNo,
    String applicationStatus,
    OffsetDateTime sessionExpiresAt
  ) {}
}
