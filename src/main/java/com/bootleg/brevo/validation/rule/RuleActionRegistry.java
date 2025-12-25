package com.bootleg.brevo.validation.rule;

import com.bootleg.brevo.config.model.RuleKind;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class RuleActionRegistry {

  private final Map<RuleKind, RuleAction> actions;

  public RuleActionRegistry(List<RuleAction> actionList) {
    EnumMap<RuleKind, RuleAction> map = new EnumMap<>(RuleKind.class);
    for (RuleAction a : actionList) {
      RuleAction prev = map.put(a.kind(), a);
      if (prev != null) {
        throw new IllegalStateException("Duplicate RuleAction for kind: " + a.kind());
      }
    }
    this.actions = Collections.unmodifiableMap(map);
  }

  public RuleAction get(RuleKind kind) {
    return actions.get(kind);
  }
}
