package com.bootleg.brevo.preload;

import com.bootleg.brevo.config.model.*;
import com.bootleg.brevo.config.repo.ConfigRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads a GroupDefinition from brevo_config.
 *
 * IMPORTANT:
 * A group can contain parent forms (via group_form_tr) and those parents can have child forms (via form_child_tr).
 * We flatten the group by loading fields/rules for BOTH parents + children and include child forms as FormDefinitions.
 */
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

        // Parents in group order
        List<ConfigRepository.GroupFormRow> parentsOrdered = formRows.stream()
          .sorted(Comparator.comparingInt(ConfigRepository.GroupFormRow::sortOrder))
          .toList();

        List<String> parentCodes = parentsOrdered.stream()
          .map(ConfigRepository.GroupFormRow::formCode)
          .toList();

        // Load parent -> child mapping first, then load fields/rules for (parents + children)
        return repo.findChildFormsForParents(parentCodes)
          .collectList()
          .map(this::toChildMap)
          .flatMap(childMap -> {

            // Children in stable order: follow parent order, then child list order
            LinkedHashSet<String> childOrdered = new LinkedHashSet<>();
            for (String p : parentCodes) {
              List<String> kids = childMap.getOrDefault(p, List.of());
              for (String c : kids) childOrdered.add(c);
            }

            // Query for ALL forms so child forms get fields/rules too
            List<String> allFormCodes = new ArrayList<>(parentCodes);
            allFormCodes.addAll(childOrdered);

            Mono<Map<String, List<ConfigRepository.FormFieldRow>>> fieldsMono =
              repo.findFieldsForForms(allFormCodes)
                .collectList()
                .map(rows -> rows.stream()
                  .collect(Collectors.groupingBy(ConfigRepository.FormFieldRow::formCode)));

            Mono<Map<String, Map<String, List<FieldRule>>>> rulesMono =
              repo.findRulesForForms(allFormCodes)
                .collectList()
                .map(this::toRulesMap);

            return Mono.zip(fieldsMono, rulesMono)
              .map(t -> {
                Map<String, List<ConfigRepository.FormFieldRow>> fieldsByForm = t.getT1();
                Map<String, Map<String, List<FieldRule>>> rulesByFormField = t.getT2();

                List<FormDefinition> forms = new ArrayList<>();

                // Parent FormDefinitions (keep group sort order)
                for (ConfigRepository.GroupFormRow fr : parentsOrdered) {
                  forms.add(buildForm(fr.formCode(), fr.sortOrder(), fieldsByForm, rulesByFormField));
                }

                // Child FormDefinitions (append after parents; stable order)
                int nextSort = parentsOrdered.get(parentsOrdered.size() - 1).sortOrder() + 1;
                for (String childCode : childOrdered) {
                  forms.add(buildForm(childCode, nextSort++, fieldsByForm, rulesByFormField));
                }

                return new GroupDefinition(groupNo, List.copyOf(forms), childMap);
              });
          });
      });
  }

  private FormDefinition buildForm(
    String formCode,
    int sortOrder,
    Map<String, List<ConfigRepository.FormFieldRow>> fieldsByForm,
    Map<String, Map<String, List<FieldRule>>> rulesByFormField
  ) {
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

    return new FormDefinition(formCode, sortOrder, fields);
  }

  private Map<String, List<String>> toChildMap(List<ConfigRepository.ParentChildFormRow> rows) {
    Map<String, List<String>> out = new HashMap<>();
    for (ConfigRepository.ParentChildFormRow r : rows) {
      out.computeIfAbsent(r.parentFormCode(), k -> new ArrayList<>()).add(r.childFormCode());
    }
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
