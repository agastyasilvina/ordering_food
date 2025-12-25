package com.bootleg.brevo.config.model;

import java.util.List;
import java.util.Map;

public record GroupDefinition(
    int groupNo,
    List<FormDefinition> forms,
    Map<String, List<String>> childFormsByParent
) {}
