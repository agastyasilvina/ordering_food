package com.bootleg.brevo.validation;

import java.util.List;

public record GroupValidationResult(
  boolean valid,
  List<ValidationError> errors
) {
  public static GroupValidationResult ok() {
    return new GroupValidationResult(true, List.of());
  }

  public static GroupValidationResult fail(List<ValidationError> errors) {
    return new GroupValidationResult(false, List.copyOf(errors));
  }
}
