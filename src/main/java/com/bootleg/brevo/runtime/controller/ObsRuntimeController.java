package com.bootleg.brevo.runtime.controller;

import com.bootleg.brevo.runtime.service.ObsRuntimeService;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/runtime", produces = MediaType.APPLICATION_JSON_VALUE)
public class ObsRuntimeController {

  private final ObsRuntimeService runtimeService;

  public ObsRuntimeController(ObsRuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

//  @PostMapping("/sessions")
//  public Mono<StartSessionResponse> start(@Valid @RequestBody StartSessionRequest req) {
//    return runtimeService.startSession(req.journeyCode(), req.customerRef())
//      .map(r -> new StartSessionResponse(
//        r.sessionId(),
//        r.applicationId(),
//        r.journeyCode(),
//        r.currentGroupNo(),
//        r.applicationStatus(),
//        r.sessionExpiresAt(),
//        r.nextGroupNo(),
//        r.groups().stream()
//          .map(g -> new GroupSnapshot(
//            String.valueOf(g.groupNo()),
//            g.groupNo(),
//            g.status(),
//            g.validateResult(),
//            g.payload()
//          ))
//          .toList()
//      ));
//  }

  @PostMapping("/sessions")
  public Mono<StartSessionResponse> start(
    @Valid @RequestBody StartSessionRequest req,
    @RequestParam(name = "restart", required = false, defaultValue = "false") boolean restart
  ) {
    Mono<ObsRuntimeService.StartSessionResult> flow = restart
      ? runtimeService.restartSession(req.journeyCode(), req.customerRef())
      : runtimeService.startSession(req.journeyCode(), req.customerRef());

    return flow.map(r -> new StartSessionResponse(
      r.sessionId(),
      r.applicationId(),
      r.journeyCode(),
      r.currentGroupNo(),
      r.applicationStatus(),
      r.sessionExpiresAt(),
      r.nextGroupNo(),
      r.groups().stream()
        .map(g -> new GroupSnapshot(
          String.valueOf(g.groupNo()),
          g.groupNo(),
          g.status(),
          g.validateResult(),
          g.payload()
        ))
        .toList()
    ));
  }


  @GetMapping("/sessions/{sessionId}/current")
  public Mono<CurrentResponse> current(@PathVariable UUID sessionId) {
    return runtimeService.getCurrent(sessionId)
      .map(r -> new CurrentResponse(
        r.sessionId(),
        r.applicationId(),
        r.journeyCode(),
        r.currentGroupNo(),
        r.applicationStatus(),
        r.sessionExpiresAt(),
        r.nextGroupNo(),
        r.groups().stream()
          .map(g -> new GroupSnapshot(
            String.valueOf(g.groupNo()),
            g.groupNo(),
            g.status(),
            g.validateResult(),
            g.payload()
          ))
          .toList()
      ));
  }

//  // Body is the envelope you want:
//  // { "payloadVersion": 1, "submissions": [ ... ] }
//  // Stored as jsonb. For now, runtime-only => VALIDATED.
//  @PutMapping("/sessions/{sessionId}/groups/{groupCode}")
//  public Mono<SubmitGroupResponse> submit(
//    @PathVariable UUID sessionId,
//    @PathVariable String groupCode,
//    @RequestBody JsonNode payload
//  ) {
//    return runtimeService.submitGroup(sessionId, groupCode, payload)
//      .map(validated -> new SubmitGroupResponse(payload, validated));
//  }


  //Without extended
//  @PutMapping("/sessions/{sessionId}/groups/{groupCode}")
//  public Mono<SubmitGroupResponse> submit(
//    @PathVariable UUID sessionId,
//    @PathVariable String groupCode,
//    @RequestBody JsonNode payload,
//    @RequestHeader(name = "X-OBS-FORCE-INVALID", required = false) Boolean forceInvalid
//  ) {
//    boolean forced = Boolean.TRUE.equals(forceInvalid);
//
//    return runtimeService.submitGroup(sessionId, groupCode, payload, forced)
//      .map(validated -> new SubmitGroupResponse(payload, validated));

// ...

  /**
   * Submit group payload.
   * - Always enforces journey order
   * - Touches session TTL if still active
   * - If expired, auto-creates a new session (same application) and returns it via headers
   * <p>
   * DEV-ONLY:
   * - X-OBS-FORCE-INVALID: true => store as INVALIDATED and do not advance progress
   */
  @PutMapping("/sessions/{sessionId}/groups/{groupCode}")
  public Mono<ResponseEntity<SubmitGroupResponse>> submit(
    @PathVariable UUID sessionId,
    @PathVariable String groupCode,
    @RequestBody JsonNode payload,
    @RequestHeader(name = "X-OBS-FORCE-INVALID", required = false) Boolean forceInvalid
  ) {
    boolean forced = Boolean.TRUE.equals(forceInvalid);

    return runtimeService.submitGroup(sessionId, groupCode, payload, forced)
      .map(out -> ResponseEntity.ok()
        // Client should use this sessionId for subsequent calls
        .header("X-OBS-SESSION-ID", out.effectiveSessionId().toString())
        .header("X-OBS-SESSION-EXPIRES-AT", out.sessionExpiresAt().toString())
        // Useful for debugging: tells you if renewal happened
        .header("X-OBS-SESSION-RENEWED", Boolean.toString(out.sessionRenewed()))
        .body(new SubmitGroupResponse(payload, out.validateResult()))
      );
  }


  // ---------------------------------------------------------------------------
  // DTOs (kept in one file, as requested)
  // ---------------------------------------------------------------------------

  public record StartSessionRequest(
    @NotBlank
    @JsonAlias({"journeyCode", "journey_code"})
    String journeyCode,

    @NotBlank
    @JsonAlias({"customerRef", "customer_ref"})
    String customerRef
  ) {
  }

  public record StartSessionResponse(
    UUID sessionId,
    UUID applicationId,
    String journeyCode,
    int currentGroupNo,
    String applicationStatus,
    OffsetDateTime sessionExpiresAt,
    Integer nextGroupNo,
    List<GroupSnapshot> groups
  ) {
  }

  public record CurrentResponse(
    UUID sessionId,
    UUID applicationId,
    String journeyCode,
    int currentGroupNo,
    String applicationStatus,
    OffsetDateTime sessionExpiresAt,
    Integer nextGroupNo,
    List<GroupSnapshot> groups
  ) {
  }

  public record GroupSnapshot(
    String groupCode,        // for now: String.valueOf(groupNo)
    int groupNo,
    String status,           // VALIDATED | INVALIDATED | MISSING
    Boolean validateResult,
    JsonNode payload
  ) {
  }

  /**
   * Submit response shape you wanted:
   * { payload: <json envelope>, validateResult: true/false }
   */
  public record SubmitGroupResponse(
    JsonNode payload,
    boolean validateResult
  ) {
  }
}
