package com.bootleg.brevo.validation.services;

import com.bootleg.brevo.config.model.FieldDefinition;
import com.bootleg.brevo.config.model.FieldRule;
import com.bootleg.brevo.config.model.FormDefinition;
import com.bootleg.brevo.runtime.dto.FormSubmission;
import com.bootleg.brevo.validation.FormValidationResult;
import com.bootleg.brevo.validation.ValidationError;
import com.bootleg.brevo.validation.rule.RuleAction;
import com.bootleg.brevo.validation.rule.RuleActionRegistry;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FormValidationService {

  private final RuleActionRegistry registry;

  public FormValidationService(RuleActionRegistry registry) {
    this.registry = registry;
  }

  public FormValidationResult validate(FormDefinition formDef, FormSubmission formSub) {
    List<ValidationError> errors = new ArrayList<>();
    Map<String, String> payload = formSub.fields() == null ? Map.of() : formSub.fields();

    // unknown fields
    Set<String> allowed = new HashSet<>();
    for (FieldDefinition fd : formDef.fields()) allowed.add(fd.fieldCode());
    for (String key : payload.keySet()) {
      if (!allowed.contains(key)) {
        errors.add(new ValidationError(
          formSub.formCode(), key, "UNKNOWN_FIELD",
          "Field is not allowed for this form",
          Map.of("formCode", formDef.formCode())
        ));
      }
    }

    formDef.fields().stream()
      .sorted(Comparator.comparingInt(FieldDefinition::sortOrder))
      .forEach(fd -> validateField(formDef, formSub, fd, payload, errors));

    return errors.isEmpty() ? FormValidationResult.ok() : FormValidationResult.fail(errors);
  }

  private void validateField(
    FormDefinition formDef,
    FormSubmission formSub,
    FieldDefinition fd,
    Map<String, String> payload,
    List<ValidationError> errors
  ) {
    String raw = payload.get(fd.fieldCode());

    if (fd.required() && isBlank(raw)) {
      errors.add(new ValidationError(
        formSub.formCode(), fd.fieldCode(), "REQUIRED",
        "Field is required",
        Map.of("formCode", formDef.formCode())
      ));
      return;
    }

    if (isBlank(raw)) return;

    List<FieldRule> rules = fd.rules() == null ? List.of() : fd.rules();

    // field can have MORE than one rule -> run them all
    for (FieldRule rule : rules) {
      if (rule == null || rule.kind() == null) continue;

      RuleAction action = registry.get(rule.kind());
      if (action == null) {
        errors.add(new ValidationError(
          formSub.formCode(), fd.fieldCode(), "UNKNOWN_RULE",
          "Unsupported rule: " + rule.kind(),
          Map.of("ruleKind", String.valueOf(rule.kind()))
        ));
        continue;
      }

      action.apply(formSub.formCode(), fd, raw, rule).ifPresent(errors::add);
    }
  }

  private boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
