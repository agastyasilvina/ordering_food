package com.bootleg.brevo.runtime.mapper;


import com.bootleg.brevo.runtime.dto.FormSubmission;
import com.bootleg.brevo.runtime.dto.GroupSubmission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Component
public class JsonSubmissionMapper {

  private final ObjectMapper objectMapper;

  public JsonSubmissionMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static String uc(String s) {
    return s == null ? "" : s.trim().toUpperCase();
  }

  /**
   * Parse raw JSON string into GroupSubmission.
   */
  public GroupSubmission fromJson(String json) {
    if (json == null || json.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body must not be empty");
    }
    try {
      GroupSubmission parsed = objectMapper.readValue(json, GroupSubmission.class);
      return normalise(parsed);
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON: " + e.getOriginalMessage(), e);
    }
  }

  /**
   * Convert JsonNode (already parsed by Jackson) into GroupSubmission.
   */
  public GroupSubmission fromNode(JsonNode node) {
    if (node == null || node.isNull()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body must not be empty");
    }
    GroupSubmission parsed = objectMapper.convertValue(node, GroupSubmission.class);
    return normalise(parsed);
  }

  /**
   * Convert Map (e.g. @RequestBody Map) into GroupSubmission.
   */
  public GroupSubmission fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body must not be empty");
    }
    GroupSubmission parsed = objectMapper.convertValue(map, GroupSubmission.class);
    return normalise(parsed);
  }

  /**
   * Normalise codes/keys and guarantee non-null collections:
   * - default payloadVersion = 1 if missing
   * - submissions = empty list if missing
   * - formCode/parentFormCode uppercased + trimmed
   * - field keys uppercased + trimmed; fields map never null
   */
  private GroupSubmission normalise(GroupSubmission in) {
    if (in == null) {
      return new GroupSubmission(1, List.of());
    }

    int version = (in.payloadVersion() == null) ? 1 : in.payloadVersion();
    List<FormSubmission> subs = (in.submissions() == null) ? List.of() : in.submissions();

    List<FormSubmission> fixed = new ArrayList<>(subs.size());
    for (FormSubmission s : subs) {
      if (s == null) continue;

      String formCode = uc(s.formCode());
      String parentCode = s.parentFormCode() == null ? null : uc(s.parentFormCode());

      Map<String, String> fields = s.fields() == null ? Map.of() : s.fields();
      Map<String, String> fixedFields = new LinkedHashMap<>();
      for (Map.Entry<String, String> e : fields.entrySet()) {
        if (e.getKey() == null) continue;
        fixedFields.put(uc(e.getKey()), e.getValue()); // keep value as-is (validator handles blank)
      }

      fixed.add(new FormSubmission(formCode, parentCode, Collections.unmodifiableMap(fixedFields)));
    }

    return new GroupSubmission(version, Collections.unmodifiableList(fixed));
  }
}
