package com.bootleg.brevo.validation.rule;

import com.bootleg.brevo.config.model.FieldDefinition;
import com.bootleg.brevo.config.model.FieldRule;
import com.bootleg.brevo.config.model.RuleKind;
import com.bootleg.brevo.validation.ValidationError;

import java.util.Optional;

public interface RuleAction {
  RuleKind kind();

  Optional<ValidationError> apply(
    String formCode,
    FieldDefinition field,
    String rawValue,
    FieldRule rule
  );
}
