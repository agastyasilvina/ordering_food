package com.bootleg.brevo.validation.services;

import com.bootleg.brevo.config.model.FieldDefinition;
import com.bootleg.brevo.config.model.FieldRule;
import com.bootleg.brevo.config.model.FormDefinition;
import com.bootleg.brevo.preload.PreloadKeys;
import com.bootleg.brevo.preload.PreloadSnapshot;
import com.bootleg.brevo.preload.PreloadStore;
import com.bootleg.brevo.runtime.dto.FormSubmission;
import com.bootleg.brevo.runtime.dto.GroupSubmission;
import com.bootleg.brevo.validation.FormValidationResult;
import com.bootleg.brevo.validation.GroupValidationResult;
import com.bootleg.brevo.validation.ValidationError;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GroupPayloadValidationService {

  private final PreloadStore preloadStore;
  private final FormValidationService formValidationService;

  public GroupPayloadValidationService(PreloadStore preloadStore,
                                       FormValidationService formValidationService) {
    this.preloadStore = preloadStore;
    this.formValidationService = formValidationService;
  }

  public GroupValidationResult validate(String journeyCode, int groupNo, GroupSubmission submission) {
    List<ValidationError> errors = new ArrayList<>();

    if (journeyCode == null || journeyCode.isBlank()) {
      return GroupValidationResult.fail(List.of(new ValidationError(
        null, null, "JOURNEY", "journeyCode is required", Map.of()
      )));
    }

    if (submission == null || submission.submissions() == null || submission.submissions().isEmpty()) {
      return GroupValidationResult.fail(List.of(new ValidationError(
        null, null, "PAYLOAD",
        "submissions must not be empty",
        Map.of("journeyCode", journeyCode, "groupNo", groupNo)
      )));
    }

    PreloadSnapshot snap = preloadStore.current();
    String gk = PreloadKeys.groupKey(journeyCode, groupNo);

    // Allowed forms in this group (already flattened with child forms)
    List<String> allowedFormsList = snap.groupForms().get(gk);
    if (allowedFormsList == null) {
      return GroupValidationResult.fail(List.of(new ValidationError(
        null, null, "UNKNOWN_GROUP",
        "Group is not configured for this journey (did you call POST /config/refresh?)",
        Map.of("journeyCode", journeyCode, "groupNo", groupNo)
      )));
    }
    Set<String> allowedForms = new HashSet<>(allowedFormsList);

    // Parent -> child mapping (optional enforcement)
    Map<String, List<String>> childMap = snap.childForms().getOrDefault(gk, Map.of());

    for (FormSubmission formSub : submission.submissions()) {
      if (formSub == null) {
        errors.add(new ValidationError(null, null, "FORM", "Form submission is null", Map.of()));
        continue;
      }

      String formCode = formSub.formCode();
      if (formCode == null || formCode.isBlank()) {
        errors.add(new ValidationError(null, null, "FORM", "Missing formCode", Map.of()));
        continue;
      }

      // 1) stop random forms in a group
      if (!allowedForms.contains(formCode)) {
        errors.add(new ValidationError(
          formCode, null, "UNKNOWN_FORM",
          "Form is not allowed in this group",
          Map.of("journeyCode", journeyCode, "groupNo", groupNo)
        ));
        continue;
      }

      // 2) if it's a child submission, ensure parentFormCode is valid
      String parent = formSub.parentFormCode();
      if (parent != null && !parent.isBlank()) {
        List<String> allowedChildren = childMap.getOrDefault(parent, List.of());
        if (!allowedChildren.contains(formCode)) {
          errors.add(new ValidationError(
            formCode, null, "INVALID_CHILD_FORM",
            "Child form is not allowed for given parentFormCode",
            Map.of("parentFormCode", parent, "expectedChildren", allowedChildren)
          ));
          continue;
        }
      }

      // 3) build FormDefinition from preload maps
      FormDefinition formDef = buildFormDefinitionFromPreload(snap, formCode);
      if (formDef == null) {
        errors.add(new ValidationError(
          formCode, null, "FORM_CONFIG_MISSING",
          "Form fields/rules not found in preload snapshot",
          Map.of("formCode", formCode)
        ));
        continue;
      }

      // 4) reuse your existing form validator
      FormValidationResult r = formValidationService.validate(formDef, formSub);
      errors.addAll(r.errors());
    }

    return errors.isEmpty() ? GroupValidationResult.ok() : GroupValidationResult.fail(errors);
  }

  private FormDefinition buildFormDefinitionFromPreload(PreloadSnapshot snap, String formCode) {
    List<String> fieldCodes = snap.formFields().get(formCode);
    if (fieldCodes == null) return null;

    List<FieldDefinition> fields = new ArrayList<>(fieldCodes.size());

    for (String fieldCode : fieldCodes) {
      String fk = PreloadKeys.fieldKey(formCode, fieldCode);

      PreloadSnapshot.FieldMeta meta = snap.fieldMeta().get(fk);
      if (meta == null) continue;

      List<FieldRule> rules = snap.fieldRules().getOrDefault(fk, List.of());

      fields.add(new FieldDefinition(
        meta.fieldCode(),
        meta.fieldType(),
        meta.required(),
        meta.sortOrder(),
        rules
      ));
    }

    fields.sort(Comparator.comparingInt(FieldDefinition::sortOrder));
    return new FormDefinition(formCode, 0, List.copyOf(fields));
  }
}
