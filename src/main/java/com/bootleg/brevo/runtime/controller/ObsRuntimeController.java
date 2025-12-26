package com.bootleg.brevo.runtime.controller;

import com.bootleg.brevo.runtime.service.ObsRuntimeService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/runtime", produces = MediaType.APPLICATION_JSON_VALUE)
public class ObsRuntimeController {

  private final ObsRuntimeService runtimeService;

  public ObsRuntimeController(ObsRuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  //todo: USE CODE? DOUBLE CHECK
  @PostMapping("/sessions")
  public Mono<StartSessionResponse> start(@RequestBody StartSessionRequest req) {
    return runtimeService.startJourney(req.journeyId(), req.customerRef())
      .map(r -> new StartSessionResponse(
        r.sessionId(),
        r.applicationId(),
        r.journeyId(),
        r.customerRef(),
        r.currentGroupNo(),
        r.applicationStatus(),
        r.sessionExpiresAt()
      ));
  }

  public record StartSessionRequest(
    UUID journeyId,
    String customerRef
  ) {}

  public record StartSessionResponse(
    UUID sessionId,
    UUID applicationId,
    UUID journeyId,
    String customerRef,
    int currentGroupNo,
    String applicationStatus,
    OffsetDateTime sessionExpiresAt
  ) {}
}
