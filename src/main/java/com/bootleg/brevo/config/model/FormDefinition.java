package com.bootleg.brevo.config.model;

import java.util.List;

public record FormDefinition(
  String formCode,
  int sortOrder,
  List<FieldDefinition> fields
) {
}
