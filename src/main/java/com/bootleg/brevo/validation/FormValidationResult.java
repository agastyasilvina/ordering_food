package com.bootleg.brevo.validation;

import java.util.List;

public record FormValidationResult(
  boolean valid,
  List<ValidationError> errors
) {
  public static FormValidationResult ok() {
    return new FormValidationResult(true, List.of());
  }

  public static FormValidationResult fail(List<ValidationError> errors) {
    return new FormValidationResult(false, List.copyOf(errors));
  }
}
