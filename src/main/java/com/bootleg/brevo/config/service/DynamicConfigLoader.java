package com.bootleg.brevo.config.service;

import com.bootleg.brevo.config.model.*;
import com.bootleg.brevo.config.repo.ConfigRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DynamicConfigLoader {

  private final ConfigRepository repo;

  public DynamicConfigLoader(ConfigRepository repo) {
    this.repo = repo;
  }

  private static String normalise(String s) {
    return s == null ? "" : s.trim().toUpperCase();
  }

  public Mono<GroupDefinition> getGroupDefinition(String journeyCode, int groupNo) {
    String jc = normalise(journeyCode);

    return repo.existsGroupInJourney(jc, groupNo)
      .flatMap(exists -> {
        if (!exists) {
          return Mono.error(new IllegalArgumentException(
            "Group " + groupNo + " is not configured for journey " + jc
          ));
        }

        Mono<List<ConfigRepository.GroupFormRow>> formsInGroupMono =
          repo.findFormsForGroup(groupNo).collectList();

        return formsInGroupMono.flatMap(formsInGroup -> {
          List<String> parentFormCodes = formsInGroup.stream()
            .sorted(Comparator.comparingInt(ConfigRepository.GroupFormRow::sortOrder))
            .map(ConfigRepository.GroupFormRow::formCode)
            .toList();

          Mono<List<ConfigRepository.ParentChildFormRow>> childRowsMono =
            repo.findChildFormsForParents(parentFormCodes).collectList();

          return childRowsMono.flatMap(childRows -> {
            Map<String, List<String>> childFormsByParent = childRows.stream()
              .collect(Collectors.groupingBy(
                ConfigRepository.ParentChildFormRow::parentFormCode,
                Collectors.mapping(ConfigRepository.ParentChildFormRow::childFormCode, Collectors.toList())
              ));

            childFormsByParent.replaceAll((k, v) -> v.stream().sorted().toList());

            // IMPORTANT: include children when fetching fields/rules
            LinkedHashSet<String> allFormCodesSet = new LinkedHashSet<>(parentFormCodes);
            childFormsByParent.values().forEach(allFormCodesSet::addAll);
            List<String> allFormCodes = new ArrayList<>(allFormCodesSet);

            Mono<List<ConfigRepository.FormFieldRow>> fieldRowsMono =
              repo.findFieldsForForms(allFormCodes).collectList();

            Mono<List<ConfigRepository.FormFieldRuleRow>> ruleRowsMono =
              repo.findRulesForForms(allFormCodes).collectList();

            return Mono.zip(fieldRowsMono, ruleRowsMono)
              .map(tuple -> {
                var fieldRows = tuple.getT1();
                var ruleRows  = tuple.getT2();

                // rulesByForm -> field -> list of rules
                Map<String, Map<String, List<FieldRule>>> rulesByFormField =
                  ruleRows.stream().collect(Collectors.groupingBy(
                    ConfigRepository.FormFieldRuleRow::formCode,
                    Collectors.groupingBy(
                      ConfigRepository.FormFieldRuleRow::fieldCode,
                      Collectors.mapping(r -> new FieldRule(
                        RuleKind.valueOf(normalise(r.ruleKind())),
                        r.min(),
                        r.max()
                      ), Collectors.toList())
                    )
                  ));

                // fieldsByForm -> list<FieldDefinition>
                Map<String, List<FieldDefinition>> fieldsByForm =
                  fieldRows.stream().collect(Collectors.groupingBy(
                    ConfigRepository.FormFieldRow::formCode,
                    Collectors.mapping(r -> {
                      List<FieldRule> rules =
                        rulesByFormField
                          .getOrDefault(r.formCode(), Map.of())
                          .getOrDefault(r.fieldCode(), List.of());

                      return new FieldDefinition(
                        r.fieldCode(),
                        FieldType.valueOf(normalise(r.fieldType())),
                        r.required(),
                        r.sortOrder(),
                        rules
                      );
                    }, Collectors.toList())
                  ));

                fieldsByForm.replaceAll((k, v) -> v.stream()
                  .sorted(Comparator.comparingInt(FieldDefinition::sortOrder))
                  .toList()
                );

                // Build FormDefinitions: parents in group order + children immediately after their parent
                Map<String, Integer> parentSortOrder = formsInGroup.stream()
                  .collect(Collectors.toMap(
                    ConfigRepository.GroupFormRow::formCode,
                    ConfigRepository.GroupFormRow::sortOrder
                  ));

                List<FormDefinition> formDefs = new ArrayList<>();

                for (String parent : parentFormCodes) {
                  int parentOrder = parentSortOrder.getOrDefault(parent, 0);

                  formDefs.add(new FormDefinition(
                    parent,
                    parentOrder,
                    fieldsByForm.getOrDefault(parent, List.of())
                  ));

                  List<String> children = childFormsByParent.getOrDefault(parent, List.of());
                  for (int i = 0; i < children.size(); i++) {
                    String child = children.get(i);

                    // derived order: parentOrder*100 + (i+1) keeps children right after parent
                    int childOrder = parentOrder * 100 + (i + 1);

                    formDefs.add(new FormDefinition(
                      child,
                      childOrder,
                      fieldsByForm.getOrDefault(child, List.of())
                    ));
                  }
                }

// Optional: if there are “orphan” forms (unlikely), append them deterministically
                Set<String> included = formDefs.stream()
                  .map(FormDefinition::formCode)
                  .collect(Collectors.toSet());

                List<String> orphans = allFormCodes.stream()
                  .filter(fc -> !included.contains(fc))
                  .sorted()
                  .toList();

                for (String fc : orphans) {
                  formDefs.add(new FormDefinition(
                    fc,
                    Integer.MAX_VALUE,
                    fieldsByForm.getOrDefault(fc, List.of())
                  ));
                }

// final sort (in-place, no reassignment)
                formDefs.sort(Comparator.comparingInt(FormDefinition::sortOrder));


                return new GroupDefinition(groupNo, formDefs, childFormsByParent);
              });
          });
        });
      });
  }
}
