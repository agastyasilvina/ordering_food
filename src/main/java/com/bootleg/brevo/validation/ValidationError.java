package com.bootleg.brevo.validation;

import java.util.Map;

/**
 * One validation problem found in a submitted form.
 */
public record ValidationError(
  String formCode,
  String fieldCode,         // can be null for form-level errors
  String rule,              // REQUIRED / LENGTH / VALUE / TYPE / UNKNOWN_FIELD / UNKNOWN_RULE
  String message,
  Map<String, Object> meta
) {
}
