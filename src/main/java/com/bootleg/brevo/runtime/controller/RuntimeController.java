package com.bootleg.brevo.runtime.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


import com.bootleg.brevo.runtime.dto.GroupSubmission;
import com.bootleg.brevo.runtime.mapper.JsonSubmissionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Minimal Runtime Controller:
 * 1) Create session
 * 2) Accept a payload and map it to GroupSubmission, then answer: valid payload or not
 *
 * No DB, no Redis, no validation rules yet.
 */
@Slf4j
@RestController
@RequestMapping("/runtime")
public class RuntimeController {

  private final JsonSubmissionMapper jsonSubmissionMapper;

  public RuntimeController(JsonSubmissionMapper jsonSubmissionMapper) {
    this.jsonSubmissionMapper = jsonSubmissionMapper;
  }

  // ---------------------------------------------------------------------------
  // 1) Session creation (in-memory UUID for now)
  // ---------------------------------------------------------------------------

  @PostMapping("/sessions")
  public Mono<StartSessionResponse> createSession(@RequestBody StartSessionRequest req) {
    if (req == null || req.journeyCode() == null || req.journeyCode().trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "journeyCode is required"));
    }

    String journeyCode = req.journeyCode().trim().toUpperCase();
    UUID sessionId = UUID.randomUUID();

    return Mono.just(new StartSessionResponse(sessionId, journeyCode, 1, "IN_PROGRESS"));
  }

  // ---------------------------------------------------------------------------
  // 2) Payload -> GroupSubmission mapping check
  // ---------------------------------------------------------------------------

  @PostMapping("/payload/check")
  public Mono<PayloadCheckResponse> checkPayload(@RequestBody JsonNode body) {
    try {
      GroupSubmission submission = jsonSubmissionMapper.fromNode(body);

      boolean valid = submission.submissions() != null
          && !submission.submissions().isEmpty()
          && submission.submissions().stream().allMatch(s ->
              s != null
              && s.formCode() != null
              && !s.formCode().isBlank()
              && s.fields() != null
          );

      log.info("Mapped GroupSubmission payload-version={} submission={}",
        submission.payloadVersion(), submission);

      return Mono.just(new PayloadCheckResponse(
          valid,
          valid ? "OK" : "Invalid payload: submissions must contain {formCode, fields} for each item",
          submission
      ));
    } catch (Exception e) {
      return Mono.just(new PayloadCheckResponse(false, e.getMessage(), null));
    }
  }

  // ---------------------------------------------------------------------------
  // DTOs (kept in one file)
  // ---------------------------------------------------------------------------

  public record StartSessionRequest(String journeyCode) {}

  public record StartSessionResponse(
      UUID sessionId,
      String journeyCode,
      int currentGroupNo,
      String status
  ) {}

  public record PayloadCheckResponse(
      boolean valid,
      String message,
      GroupSubmission parsed
  ) {}
}
