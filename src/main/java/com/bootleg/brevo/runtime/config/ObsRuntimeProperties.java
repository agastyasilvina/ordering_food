package com.bootleg.brevo.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "obs.runtime")
public record ObsRuntimeProperties(
  Duration sessionTtl
) {
  public ObsRuntimeProperties {
    if (sessionTtl == null) sessionTtl = Duration.ofMinutes(30);
  }
}
