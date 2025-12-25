package com.bootleg.brevo.preload;

import com.bootleg.brevo.config.model.FieldRule;
import com.bootleg.brevo.config.model.FieldType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PreloadSnapshot(
  Instant loadedAt,

  // List<String> JOURNEY
  List<String> journeys,

  // Map<String, List<Integer>> JOURNEYGROUPS
  Map<String, List<Integer>> journeyGroups,

  // groupKey -> [forms] (child forms flattened too)
  Map<String, List<String>> groupForms,

  // groupKey -> {parent: [childs]}
  Map<String, Map<String, List<String>>> childForms,

  // form -> [fieldCodes]
  Map<String, List<String>> formFields,

  // fieldKey -> field meta (type/required/order)
  Map<String, FieldMeta> fieldMeta,

  // fieldKey -> [rules]
  Map<String, List<FieldRule>> fieldRules
) {
  public static PreloadSnapshot empty() {
    return new PreloadSnapshot(
      Instant.EPOCH,
      List.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      Map.of(),
      Map.of()
    );
  }

  public record FieldMeta(
    String formCode,
    String fieldCode,
    FieldType fieldType,
    boolean required,
    int sortOrder
  ) {}
}
