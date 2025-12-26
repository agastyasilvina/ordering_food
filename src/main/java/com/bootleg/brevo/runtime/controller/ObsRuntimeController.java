package com.bootleg.brevo.runtime.controller;

import com.bootleg.brevo.runtime.service.ObsRuntimeService;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
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

  @PostMapping("/sessions")
  public Mono<StartSessionResponse> start(@Valid @RequestBody StartSessionRequest req) {
    // IMPORTANT:
    // Your service method must accept journeyCode now (String), not journeyId (UUID),
    // otherwise your code will stay red.
    return runtimeService.startJourney(req.journeyCode(), req.customerRef())
      .map(r -> new StartSessionResponse(
        r.sessionId(),
        r.applicationId(),
        req.journeyCode(),      // we have it right here already
        r.currentGroupNo(),
        r.applicationStatus(),
        r.sessionExpiresAt(),
        List.of()               // TODO: fill from obs_group_state later
      ));
  }

  public record StartSessionRequest(
    @NotBlank
    @JsonAlias({"journeyCode", "journey_code"})
    String journeyCode,

    @NotBlank
    @JsonAlias({"customerRef", "customer_ref"})
    String customerRef
  ) {}

  public record StartSessionResponse(
    UUID sessionId,
    UUID applicationId,
    String journeyCode,
    int currentGroupNo,
    String applicationStatus,
    OffsetDateTime sessionExpiresAt,
    List<GroupSnapshot> groups
  ) {}

  public record GroupSnapshot(
    String groupCode,        // e.g. "1", "2", "4"
    int groupNo,
    String status,           // VALIDATED | INVALIDATED
    Boolean validateResult,  // true if VALIDATED, false if INVALIDATED, null if not submitted
    JsonNode payload         // optional
  ) {}
}
