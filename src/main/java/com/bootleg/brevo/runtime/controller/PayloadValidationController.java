package com.bootleg.brevo.runtime.controller;

import com.bootleg.brevo.runtime.dto.GroupSubmission;
import com.bootleg.brevo.validation.GroupValidationResult;
import com.bootleg.brevo.validation.ValidationError;
import com.bootleg.brevo.validation.services.GroupPayloadValidationService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/runtime")
public class PayloadValidationController {

  private final GroupPayloadValidationService service;

  public PayloadValidationController(GroupPayloadValidationService service) {
    this.service = service;
  }

  @PostMapping("/journeys/{journeyCode}/groups/{groupNo}/payload/validate")
  public Mono<GroupPayloadValidationResponse> validate(
    @PathVariable String journeyCode,
    @PathVariable int groupNo,
    @RequestBody GroupSubmission body
  ) {
    GroupValidationResult r = service.validate(journeyCode, groupNo, body);
    return Mono.just(new GroupPayloadValidationResponse(
      r.valid(),
      r.valid() ? "OK" : "Invalid payload",
      r.errors()
    ));
  }

  public record GroupPayloadValidationResponse(
    boolean valid,
    String message,
    List<ValidationError> errors
  ) {
  }
}
