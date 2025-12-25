package com.bootleg.brevo.config.repo;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ConfigRepository {

  private final DatabaseClient db;

  public ConfigRepository(DatabaseClient db) {
    this.db = db;
  }

  // ---------- Row DTOs ----------
  public record GroupFormRow(String formCode, int sortOrder) {}
  public record FormFieldRow(String formCode, String fieldCode, boolean required, int sortOrder) {}
  public record ParentChildFormRow(String parentFormCode, String childFormCode) {}

  // ---------- Queries ----------

  /** Checks if a group belongs to a journey (prevents nonsense calls). */
  public Mono<Boolean> existsGroupInJourney(String journeyCode, int groupNo) {
    String sql = """
      SELECT 1
      FROM brevo_config.journey_tm j
      JOIN brevo_config.journey_group_tr jgt ON jgt.journey_id = j.journey_id
      JOIN brevo_config.group_tm g ON g.group_id = jgt.group_id
      WHERE j.journey_code = :journeyCode
        AND g.group_no = :groupNo
      LIMIT 1
      """;

    return db.sql(sql)
      .bind("journeyCode", journeyCode)
      .bind("groupNo", groupNo)
      .fetch()
      .one()
      .map(x -> true)
      .defaultIfEmpty(false);
  }

  /** Group -> ordered forms. */
  public Flux<GroupFormRow> findFormsForGroup(int groupNo) {
    String sql = """
      SELECT f.form_code,
             gft.sort_order AS form_sort_order
      FROM brevo_config.group_tm g
      JOIN brevo_config.group_form_tr gft ON gft.group_id = g.group_id
      JOIN brevo_config.form_tm f ON f.form_id = gft.form_id
      WHERE g.group_no = :groupNo
      ORDER BY gft.sort_order
      """;

    return db.sql(sql)
      .bind("groupNo", groupNo)
      .map((row, md) -> new GroupFormRow(
        must(row.get("form_code", String.class)),
        must(row.get("form_sort_order", Integer.class))
      ))
      .all();
  }

  /** Forms -> ordered fields (safe IN binding, no array codec drama). */
  public Flux<FormFieldRow> findFieldsForForms(List<String> formCodes) {
    if (formCodes == null || formCodes.isEmpty()) return Flux.empty();

    String in = namedInClause("c", formCodes.size());

    String sql = """
      SELECT f.form_code,
             fld.field_code,
             fft.is_required,
             fft.sort_order AS field_sort_order
      FROM brevo_config.form_tm f
      JOIN brevo_config.form_field_tr fft ON fft.form_id = f.form_id
      JOIN brevo_config.field_tm fld ON fld.field_id = fft.field_id
      WHERE f.form_code IN ( %s )
      ORDER BY f.form_code, fft.sort_order
      """.formatted(in);

    DatabaseClient.GenericExecuteSpec spec = db.sql(sql);
    for (int i = 0; i < formCodes.size(); i++) {
      spec = spec.bind("c" + i, formCodes.get(i));
    }

    return spec.map((row, md) -> new FormFieldRow(
        must(row.get("form_code", String.class)),
        must(row.get("field_code", String.class)),
        Boolean.TRUE.equals(row.get("is_required", Boolean.class)),
        must(row.get("field_sort_order", Integer.class))
      ))
      .all();
  }

  /** Parent forms -> child forms mapping (safe IN binding). */
  public Flux<ParentChildFormRow> findChildFormsForParents(List<String> parentFormCodes) {
    if (parentFormCodes == null || parentFormCodes.isEmpty()) return Flux.empty();

    String in = namedInClause("p", parentFormCodes.size());

    String sql = """
      SELECT p.form_code AS parent_form_code,
             c.form_code AS child_form_code
      FROM brevo_config.form_tm p
      JOIN brevo_config.field_parameter_form_tr rel
           ON rel.parent_form_tm_id = p.form_id
      JOIN brevo_config.form_tm c
           ON c.form_id = rel.child_form_tm_id
      WHERE p.form_code IN ( %s )
      ORDER BY p.form_code, c.form_code
      """.formatted(in);

    DatabaseClient.GenericExecuteSpec spec = db.sql(sql);
    for (int i = 0; i < parentFormCodes.size(); i++) {
      spec = spec.bind("p" + i, parentFormCodes.get(i));
    }

    return spec.map((row, md) -> new ParentChildFormRow(
        must(row.get("parent_form_code", String.class)),
        must(row.get("child_form_code", String.class))
      ))
      .all();
  }

  // ---------- Helpers ----------
  private static String namedInClause(String prefix, int size) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < size; i++) {
      if (i > 0) sb.append(", ");
      sb.append(":").append(prefix).append(i);
    }
    return sb.toString();
  }

  private static <T> T must(T v) {
    if (v == null) throw new IllegalStateException("Unexpected NULL from DB mapping");
    return v;
  }

  // (If you need it later)
  @SuppressWarnings("unused")
  private static List<String> toList(Iterable<String> it) {
    if (it == null) return List.of();
    if (it instanceof List<String> list) return list;
    ArrayList<String> out = new ArrayList<>();
    for (String s : it) out.add(s);
    return out;
  }
}
