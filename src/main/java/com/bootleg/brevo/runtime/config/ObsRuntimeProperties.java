package com.bootleg.brevo.runtime.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "obs.runtime")
public record ObsRuntimeProperties(
  Duration sessionTtl
) {
  public ObsRuntimeProperties {
    if (sessionTtl == null) sessionTtl = Duration.ofMinutes(30);
  }
}
