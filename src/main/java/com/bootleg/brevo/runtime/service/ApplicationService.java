package com.bootleg.brevo.runtime.service;


import com.bootleg.brevo.runtime.entity.ApplicationEntity;
import com.bootleg.brevo.runtime.entity.SessionEntity;
import com.bootleg.brevo.runtime.repo.ApplicationRepository;
import com.bootleg.brevo.runtime.repo.SessionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ApplicationService {

  private final ApplicationRepository applicationRepo;
  private final SessionRepository sessionRepo;

  public ApplicationService(ApplicationRepository applicationRepo, SessionRepository sessionRepo) {
    this.applicationRepo = applicationRepo;
    this.sessionRepo = sessionRepo;
  }

  public Mono<StartResult> startOrResume(String journeyCode, String customerId, String clientId) {
    Mono<ApplicationEntity> lookup =
      hasText(customerId)
        ? applicationRepo.findActiveByCustomer(customerId, journeyCode)
        : Mono.empty();

    lookup = lookup.switchIfEmpty(
      hasText(clientId)
        ? applicationRepo.findActiveByClient(clientId, journeyCode)
        : Mono.empty()
    );

    return lookup
      .flatMap(app -> createSession(app).map(sess -> new StartResult(app, sess)))
      .switchIfEmpty(createNewApplication(journeyCode, customerId, clientId)
        .flatMap(app -> createSession(app).map(sess -> new StartResult(app, sess))))
      // Handles race: two requests creating app at the same time (unique index trips)
      .onErrorResume(DuplicateKeyException.class, ex ->
        applicationRepo.findActiveByCustomer(customerId, journeyCode)
          .flatMap(app -> createSession(app).map(sess -> new StartResult(app, sess)))
      );
  }

  private Mono<ApplicationEntity> createNewApplication(String journeyCode, String customerId, String clientId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    ApplicationEntity app = new ApplicationEntity(
      UUID.randomUUID(),
      journeyCode,
      normalise(customerId),
      normalise(clientId),
      1,
      1,
      "IN_PROGRESS",
      now,
      now
    );

    return applicationRepo.save(app);
  }

  private Mono<SessionEntity> createSession(ApplicationEntity app) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    SessionEntity sess = new SessionEntity(
      UUID.randomUUID(),
      app.applicationId(),
      "ACTIVE",
      now,
      null
    );

    return sessionRepo.save(sess);
  }

  private static boolean hasText(String s) {
    return s != null && !s.isBlank();
  }

  private static String normalise(String s) {
    return hasText(s) ? s.trim() : null;
  }

  public record StartResult(ApplicationEntity application, SessionEntity session) {}
}
