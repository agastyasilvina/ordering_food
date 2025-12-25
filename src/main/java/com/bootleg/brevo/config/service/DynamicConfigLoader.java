package com.bootleg.brevo.config.service;

import com.bootleg.brevo.config.model.FieldDefinition;
import com.bootleg.brevo.config.model.FormDefinition;
import com.bootleg.brevo.config.model.GroupDefinition;
import com.bootleg.brevo.config.repo.ConfigRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

        return repo.findFormsForGroup(groupNo).collectList()
          .flatMap(formsInGroup -> {
            List<String> formCodes = formsInGroup.stream()
              .map(ConfigRepository.GroupFormRow::formCode)
              .toList();

            Mono<List<ConfigRepository.FormFieldRow>> fieldsMono =
              repo.findFieldsForForms(formCodes).collectList();

            Mono<List<ConfigRepository.ParentChildFormRow>> childrenMono =
              repo.findChildFormsForParents(formCodes).collectList();

            return Mono.zip(fieldsMono, childrenMono)
              .map(tuple -> {
                var fieldRows = tuple.getT1();
                var childRows = tuple.getT2();

                Map<String, List<FieldDefinition>> fieldsByForm = fieldRows.stream()
                  .collect(Collectors.groupingBy(
                    ConfigRepository.FormFieldRow::formCode,
                    Collectors.mapping(
                      r -> new FieldDefinition(r.fieldCode(), r.required(), r.sortOrder()),
                      Collectors.toList()
                    )
                  ));

                fieldsByForm.replaceAll((k, v) -> v.stream()
                  .sorted(Comparator.comparingInt(FieldDefinition::sortOrder))
                  .toList()
                );

                Map<String, List<String>> childFormsByParent = childRows.stream()
                  .collect(Collectors.groupingBy(
                    ConfigRepository.ParentChildFormRow::parentFormCode,
                    Collectors.mapping(ConfigRepository.ParentChildFormRow::childFormCode, Collectors.toList())
                  ));

                childFormsByParent.replaceAll((k, v) -> v.stream().sorted().toList());

                List<FormDefinition> formDefs = formsInGroup.stream()
                  .sorted(Comparator.comparingInt(ConfigRepository.GroupFormRow::sortOrder))
                  .map(fr -> new FormDefinition(
                    fr.formCode(),
                    fr.sortOrder(),
                    fieldsByForm.getOrDefault(fr.formCode(), List.of())
                  ))
                  .toList();

                return new GroupDefinition(groupNo, formDefs, childFormsByParent);
              });
          });
      });
  }
}
