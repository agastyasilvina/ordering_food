package com.bootleg.brevo.preload;

import com.bootleg.brevo.config.model.FieldDefinition;
import com.bootleg.brevo.config.model.FieldRule;
import com.bootleg.brevo.config.model.FormDefinition;
import com.bootleg.brevo.config.model.GroupDefinition;
import com.bootleg.brevo.config.repo.ConfigRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PreloadStore {

  private final ConfigRepository repo;
  private final GroupDefinitionLoader groupLoader;

  private final AtomicReference<PreloadSnapshot> snapshotRef =
    new AtomicReference<>(PreloadSnapshot.empty());

  private final AtomicReference<Mono<PreloadSnapshot>> refreshInFlight =
    new AtomicReference<>();

  public PreloadStore(ConfigRepository repo, GroupDefinitionLoader groupLoader) {
    this.repo = repo;
    this.groupLoader = groupLoader;
  }

  private static List<String> flattenForms(List<FormDefinition> forms, Map<String, List<String>> child) {
    LinkedHashSet<String> out = new LinkedHashSet<>();

    List<FormDefinition> sorted = forms == null ? List.of()
      : forms.stream().sorted(Comparator.comparingInt(FormDefinition::sortOrder)).toList();

    for (FormDefinition f : sorted) {
      out.add(f.formCode());
      List<String> kids = child.getOrDefault(f.formCode(), List.of());
      for (String c : kids) out.add(c);
    }
    return List.copyOf(out);
  }

  private static Map<String, List<String>> freezeChild(Map<String, List<String>> child) {
    Map<String, List<String>> out = new HashMap<>();
    for (var e : child.entrySet()) out.put(e.getKey(), List.copyOf(e.getValue()));
    return Map.copyOf(out);
  }

  private static Map<String, List<Integer>> freezeJourneyGroups(Map<String, List<Integer>> in) {
    Map<String, List<Integer>> out = new HashMap<>();
    for (var e : in.entrySet()) out.put(e.getKey(), List.copyOf(e.getValue()));
    return Map.copyOf(out);
  }

  /**
   * Hot path: safe under heavy read traffic.
   */
  public PreloadSnapshot current() {
    return snapshotRef.get();
  }

  /**
   * Cold path: rebuild from DB, single-flight (no stampede).
   */
  public Mono<PreloadSnapshot> refreshAll() {
    Mono<PreloadSnapshot> existing = refreshInFlight.get();
    if (existing != null) return existing;

    Mono<PreloadSnapshot> job = buildSnapshotFromDb()
      .doOnNext(snapshotRef::set)
      .doFinally(sig -> refreshInFlight.set(null))
      .cache();

    if (refreshInFlight.compareAndSet(null, job)) return job;
    return refreshInFlight.get();
  }

  private Mono<PreloadSnapshot> buildSnapshotFromDb() {
    return repo.findActiveJourneyCodes()
      .collectList()
      .flatMap(journeys -> {
        if (journeys.isEmpty()) {
          return Mono.just(PreloadSnapshot.empty());
        }

        // journey -> groupNos (from DB)
        return Flux.fromIterable(journeys)
          .flatMap(journeyCode ->
              repo.findGroupNosByJourneyCode(journeyCode).collectList()
                .map(groupNos -> Map.entry(journeyCode, List.copyOf(groupNos)))
            , 4)
          .collectMap(Map.Entry::getKey, Map.Entry::getValue)
          .flatMap(journeyGroups -> {

            // unique groupNos to load once
            Set<Integer> allGroupNos = new TreeSet<>();
            for (List<Integer> gns : journeyGroups.values()) allGroupNos.addAll(gns);

            return Flux.fromIterable(allGroupNos)
              .flatMap(groupNo ->
                  groupLoader.load(groupNo).map(def -> Map.entry(groupNo, def)),
                6)
              .collectMap(Map.Entry::getKey, Map.Entry::getValue)
              .map(groupDefByNo -> buildSnapshot(
                journeys,
                journeyGroups,
                groupDefByNo
              ));
          });
      });
  }

  private PreloadSnapshot buildSnapshot(
    List<String> journeys,
    Map<String, List<Integer>> journeyGroups,
    Map<Integer, GroupDefinition> groupDefByNo
  ) {
    Map<String, List<String>> groupForms = new HashMap<>();
    Map<String, Map<String, List<String>>> childForms = new HashMap<>();
    Map<String, List<String>> formFields = new HashMap<>();
    Map<String, PreloadSnapshot.FieldMeta> fieldMeta = new HashMap<>();
    Map<String, List<FieldRule>> fieldRules = new HashMap<>();

    // per journey/group -> forms + child mapping
    for (String journey : journeys) {
      List<Integer> groups = journeyGroups.getOrDefault(journey, List.of());
      for (int groupNo : groups) {
        GroupDefinition gd = groupDefByNo.get(groupNo);
        if (gd == null) continue;

        String gk = PreloadKeys.groupKey(journey, groupNo);

        Map<String, List<String>> child = gd.childFormsByParent() == null ? Map.of() : gd.childFormsByParent();
        childForms.put(gk, freezeChild(child));

        // forms: ordered + flatten children after their parent
        groupForms.put(gk, flattenForms(gd.forms(), child));

        // also populate formFields + field meta/rules (global by formCode)
        for (FormDefinition f : gd.forms()) {
          String formCode = f.formCode();

          List<FieldDefinition> fields = f.fields() == null ? List.of() : f.fields();
          List<String> fieldCodes = fields.stream()
            .sorted(Comparator.comparingInt(FieldDefinition::sortOrder))
            .map(FieldDefinition::fieldCode)
            .toList();

          formFields.put(formCode, fieldCodes);

          for (FieldDefinition fd : fields) {
            String fk = PreloadKeys.fieldKey(formCode, fd.fieldCode());

            fieldMeta.put(fk, new PreloadSnapshot.FieldMeta(
              formCode,
              fd.fieldCode(),
              fd.fieldType(),
              fd.required(),
              fd.sortOrder()
            ));

            fieldRules.put(fk, fd.rules() == null ? List.of() : List.copyOf(fd.rules()));
          }
        }
      }
    }

    return new PreloadSnapshot(
      Instant.now(),
      List.copyOf(journeys),
      freezeJourneyGroups(journeyGroups),
      Map.copyOf(groupForms),
      Map.copyOf(childForms),
      Map.copyOf(formFields),
      Map.copyOf(fieldMeta),
      Map.copyOf(fieldRules)
    );
  }
}
