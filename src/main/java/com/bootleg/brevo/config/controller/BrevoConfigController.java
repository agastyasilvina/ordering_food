package com.bootleg.brevo.config.controller;

import com.bootleg.brevo.config.model.GroupDefinition;
import com.bootleg.brevo.config.service.DynamicConfigLoader;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/config")
@Validated
public class BrevoConfigController {

  private final DynamicConfigLoader loader;

  public BrevoConfigController(DynamicConfigLoader loader) {
    this.loader = loader;
  }

  @GetMapping("/journeys/{journeyCode}/groups/{groupNo}")
  public Mono<GroupDefinition> getGroupDefinition(
    @PathVariable String journeyCode,
    @PathVariable @Min(1) int groupNo,
    @RequestHeader(value = "Language", defaultValue = "id-ID") String language
  ) {
    return loader.getGroupDefinition(journeyCode, groupNo);
  }
}
