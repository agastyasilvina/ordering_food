package com.bootleg.brevo.config.model;

import java.math.BigDecimal;

public record FieldRule(
  RuleKind kind,
  BigDecimal min,
  BigDecimal max
) {}
