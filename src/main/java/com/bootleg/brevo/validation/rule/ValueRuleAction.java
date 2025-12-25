package com.bootleg.brevo.validation.rule;

import com.bootleg.brevo.config.model.FieldDefinition;
import com.bootleg.brevo.config.model.FieldRule;
import com.bootleg.brevo.config.model.RuleKind;
import com.bootleg.brevo.validation.ValidationError;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class ValueRuleAction implements RuleAction {

  @Override
  public RuleKind kind() {
    return RuleKind.VALUE;
  }

  @Override
  public Optional<ValidationError> apply(String formCode, FieldDefinition field, String rawValue, FieldRule rule) {
    if (rawValue == null) return Optional.empty();

    BigDecimal value;
    try {
      value = new BigDecimal(rawValue.trim());
    } catch (Exception e) {
      return Optional.of(new ValidationError(
        formCode,
        field.fieldCode(),
        "TYPE",
        "Invalid numeric value",
        Map.of("actual", rawValue)
      ));
    }

    BigDecimal min = rule.min();
    BigDecimal max = rule.max();

    if ((min != null && value.compareTo(min) < 0) || (max != null && value.compareTo(max) > 0)) {
      return Optional.of(new ValidationError(
        formCode,
        field.fieldCode(),
        RuleKind.VALUE.toString(),
        "Value is out of range",
        Map.of("min", min, "max", max, "actual", value)
      ));
    }

    return Optional.empty();
  }
}
