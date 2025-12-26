package com.bootleg.brevo.config.model;

import java.util.List;

public record FieldDefinition(
  String fieldCode,
  FieldType fieldType,                 // TEXT / NOMINAL (from field_tm.field_type)
  boolean required,                    // from form_field_tr.is_required
  int sortOrder,                       // from form_field_tr.sort_order
  List<FieldRule> rules                // from form_field_rule_tr
) {
}
