package com.bootleg.brevo.preload;

import com.bootleg.brevo.config.model.*;
import com.bootleg.brevo.config.repo.ConfigRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class GroupDefinitionLoader {

  private final ConfigRepository repo;

  public GroupDefinitionLoader(ConfigRepository repo) {
    this.repo = repo;
  }

  public Mono<GroupDefinition> load(int groupNo) {
    return repo.findFormsForGroup(groupNo)
      .collectList()
      .flatMap(formRows -> {
        if (formRows.isEmpty()) {
          return Mono.just(new GroupDefinition(groupNo, List.of(), Map.of()));
        }

        List<String> formCodes = formRows.stream().map(ConfigRepository.GroupFormRow::formCode).toList();

        Mono<Map<String, List<String>>> childMapMono =
          repo.findChildFormsForParents(formCodes)
            .collectList()
            .map(this::toChildMap);

        Mono<Map<String, List<ConfigRepository.FormFieldRow>>> fieldsMono =
          repo.findFieldsForForms(formCodes)
            .collectList()
            .map(rows -> rows.stream().collect(Collectors.groupingBy(ConfigRepository.FormFieldRow::formCode)));

        Mono<Map<String, Map<String, List<FieldRule>>>> rulesMono =
          repo.findRulesForForms(formCodes)
            .collectList()
            .map(this::toRulesMap);

        return Mono.zip(childMapMono, fieldsMono, rulesMono)
          .map(t -> {
            Map<String, List<String>> childMap = t.getT1();
            Map<String, List<ConfigRepository.FormFieldRow>> fieldsByForm = t.getT2();
            Map<String, Map<String, List<FieldRule>>> rulesByFormField = t.getT3();

            List<FormDefinition> forms = new ArrayList<>();
            for (ConfigRepository.GroupFormRow fr : formRows) {
              String formCode = fr.formCode();

              List<FieldDefinition> fields = fieldsByForm.getOrDefault(formCode, List.of()).stream()
                .sorted(Comparator.comparingInt(ConfigRepository.FormFieldRow::sortOrder))
                .map(r -> {
                  FieldType fieldType = parseFieldType(r.fieldType());
                  List<FieldRule> rules = rulesByFormField
                    .getOrDefault(formCode, Map.of())
                    .getOrDefault(r.fieldCode(), List.of());

                  return new FieldDefinition(
                    r.fieldCode(),
                    fieldType,
                    r.required(),
                    r.sortOrder(),
                    rules
                  );
                })
                .toList();

              forms.add(new FormDefinition(formCode, fr.sortOrder(), fields));
            }

            return new GroupDefinition(groupNo, List.copyOf(forms), childMap);
          });
      });
  }

  private Map<String, List<String>> toChildMap(List<ConfigRepository.ParentChildFormRow> rows) {
    Map<String, List<String>> out = new HashMap<>();
    for (ConfigRepository.ParentChildFormRow r : rows) {
      out.computeIfAbsent(r.parentFormCode(), k -> new ArrayList<>()).add(r.childFormCode());
    }
    // freeze
    Map<String, List<String>> frozen = new HashMap<>();
    for (var e : out.entrySet()) frozen.put(e.getKey(), List.copyOf(e.getValue()));
    return Map.copyOf(frozen);
  }

  private Map<String, Map<String, List<FieldRule>>> toRulesMap(List<ConfigRepository.FormFieldRuleRow> rows) {
    Map<String, Map<String, List<FieldRule>>> out = new HashMap<>();

    for (ConfigRepository.FormFieldRuleRow r : rows) {
      RuleKind kind = parseRuleKind(r.ruleKind());
      FieldRule rule = new FieldRule(kind, r.min(), r.max());

      out.computeIfAbsent(r.formCode(), k -> new HashMap<>())
        .computeIfAbsent(r.fieldCode(), k -> new ArrayList<>())
        .add(rule);
    }

    // freeze deeply
    Map<String, Map<String, List<FieldRule>>> frozen = new HashMap<>();
    for (var formEntry : out.entrySet()) {
      Map<String, List<FieldRule>> inner = new HashMap<>();
      for (var fieldEntry : formEntry.getValue().entrySet()) {
        inner.put(fieldEntry.getKey(), List.copyOf(fieldEntry.getValue()));
      }
      frozen.put(formEntry.getKey(), Map.copyOf(inner));
    }
    return Map.copyOf(frozen);
  }

  private FieldType parseFieldType(String raw) {
    if (raw == null) throw new IllegalStateException("field_type is null");
    return FieldType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
  }

  private RuleKind parseRuleKind(String raw) {
    if (raw == null) throw new IllegalStateException("rule_kind is null");
    return RuleKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
  }
}
