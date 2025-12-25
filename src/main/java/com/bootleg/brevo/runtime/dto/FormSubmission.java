package com.bootleg.brevo.runtime.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FormSubmission(
  String formCode,
  String parentFormCode,      // null for normal; "FORM_C" if this is a child submission
  Map<String, String> fields  // String for now (so “not empty” is trivial)
) {
}
