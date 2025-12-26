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
public class LengthRuleAction implements RuleAction {

  @Override
  public RuleKind kind() {
    return RuleKind.LENGTH;
  }

  @Override
  public Optional<ValidationError> apply(String formCode, FieldDefinition field, String rawValue, FieldRule rule) {
    if (rawValue == null) return Optional.empty();

    Integer min = toInt(rule.min());
    Integer max = toInt(rule.max());
    int len = rawValue.length();

    if ((min != null && len < min) || (max != null && len > max)) {
      return Optional.of(new ValidationError(
        formCode,
        field.fieldCode(),
        RuleKind.LENGTH.toString(),
        "Length is out of range",
        Map.of("min", min, "max", max, "actual", len)
      ));
    }
    return Optional.empty();
  }

  private Integer toInt(BigDecimal bd) {
    if (bd == null) return null;
    try {
      return bd.intValueExact();
    } catch (ArithmeticException e) {
      return bd.intValue();
    }
  }
}
