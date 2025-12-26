package com.bootleg.brevo.runtime.service;

import com.bootleg.brevo.runtime.config.ObsRuntimeProperties;
import com.bootleg.brevo.runtime.entity.ObsApplicationEntity;
import com.bootleg.brevo.runtime.entity.ObsGroupStateEntity;
import com.bootleg.brevo.runtime.entity.ObsSessionEntity;
import com.bootleg.brevo.runtime.repo.BrevoJourneyConfigRepository;
import com.bootleg.brevo.runtime.repo.ObsApplicationRepository;
import com.bootleg.brevo.runtime.repo.ObsGroupStateRepository;
import com.bootleg.brevo.runtime.repo.ObsSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ObsRuntimeService {

  private final ObsApplicationRepository appRepo;
  private final ObsSessionRepository sessionRepo;
  private final ObsGroupStateRepository groupRepo;

  private final BrevoJourneyConfigRepository journeyConfigRepo;
  private final JourneyPlanService journeyPlanService;

  private final ObsRuntimeProperties props;
  private final ObjectMapper objectMapper;
  private final TransactionalOperator tx;

  public ObsRuntimeService(
    ObsApplicationRepository appRepo,
    ObsSessionRepository sessionRepo,
    ObsGroupStateRepository groupRepo,
    BrevoJourneyConfigRepository journeyConfigRepo,
    JourneyPlanService journeyPlanService,
    ObsRuntimeProperties props,
    ObjectMapper objectMapper,
    TransactionalOperator tx
  ) {
    this.appRepo = appRepo;
    this.sessionRepo = sessionRepo;
    this.groupRepo = groupRepo;
    this.journeyConfigRepo = journeyConfigRepo;
    this.journeyPlanService = journeyPlanService;
    this.props = props;
    this.objectMapper = objectMapper;
    this.tx = tx;
  }

  /**
   * Start/resume:
   * - warm plan cache for journeyCode
   * - resolve journeyId
   * - find/create IN_PROGRESS application
   * - create ACTIVE session (sliding TTL enforced elsewhere)
   * - return a snapshot (groups + statuses) for the UI
   */
  public Mono<StartSessionResult> startSession(String journeyCode, String customerRef) {
    OffsetDateTime now = OffsetDateTime.now();

    return journeyPlanService.warmUp(journeyCode)
      .then(journeyConfigRepo.findJourneyIdByCode(journeyCode))
      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid journeyCode")))
      .flatMap(journeyId ->
        appRepo.findOpenByCustomerRef(customerRef, journeyId)
          .switchIfEmpty(Mono.defer(() -> createNewApplication(journeyId, journeyCode, customerRef)))
      )
      .flatMap(app ->
        sessionRepo.insertNew(
            UUID.randomUUID(),
            app.applicationId(),
            "ACTIVE",
            now.plus(props.sessionTtl())
          )
          .map(sess -> new AppAndSession(app, sess))
      )
      .flatMap(pair -> buildSnapshot(pair.app(), pair.sess(), journeyCode));
  }

  /**
   * Current view for the UI:
   * - require active session
   * - touch session TTL
   * - return snapshot
   */
  public Mono<CurrentResult> getCurrent(UUID sessionId) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime newExpiresAt = now.plus(props.sessionTtl());

    return sessionRepo.findActive(sessionId)
      .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.GONE, "Session expired")))
      .flatMap(sess ->
        appRepo.findById(sess.applicationId())
          .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")))
          .flatMap(app ->
            sessionRepo.touch(sessionId, newExpiresAt)
              .thenReturn(new AppAndSession(app, sess))
          )
      )
      .flatMap(pair -> buildSnapshot(pair.app(), pair.sess(), pair.app().journeyCode()))
      .map(s -> new CurrentResult(
        s.sessionId(),
        s.applicationId(),
        s.journeyCode(),
        s.currentGroupNo(),
        s.applicationStatus(),
        s.sessionExpiresAt(),
        s.nextGroupNo(),
        s.groups()
      ));
  }

  /**
   * Runtime-only submit (no real validation yet):
   * - enforce journey order using cached plan (no repeated brevo_config queries)
   * - store payload envelope as jsonb
   * - mark group VALIDATED
   * - if back-edit, invalidate later groups (by journey position)
   * - update application.current_group_no and status
   * - touch session TTL
   *
   * Returns boolean validateResult:
   * - true  => VALIDATED stored
   * - errors => thrown as HTTP status (expired session / not allowed / etc.)
   */
//  public Mono<Boolean> submitGroup(UUID sessionId, String groupCode, JsonNode payload, boolean forceInvalid) {
//    final int groupNo;
//    try {
//      groupNo = Integer.parseInt(groupCode);
//    } catch (Exception e) {
//      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupCode must be numeric for now"));
//    }
//
//    OffsetDateTime now = OffsetDateTime.now();
//    OffsetDateTime newExpiresAt = now.plus(props.sessionTtl());
//    String payloadJson = payload == null ? "null" : payload.toString();
//
//    return tx.transactional(
//      sessionRepo.findActive(sessionId)
//        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.GONE, "Session expired")))
//        .flatMap(sess ->
//          appRepo.lockById(sess.applicationId())
//            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")))
//            .flatMap(app ->
//              journeyPlanService.getPlan(app.journeyCode())
//                .flatMap(plan -> {
//                  Integer submittedPos = plan.posByGroupNo().get(groupNo);
//                  if (submittedPos == null) {
//                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group is not part of this journey"));
//                  }
//
//                  int currentGroupNo = app.currentGroupNo() == null ? 0 : app.currentGroupNo();
//                  int currentPos = (currentGroupNo == 0)
//                    ? 0
//                    : plan.posByGroupNo().getOrDefault(currentGroupNo, 0);
//
//                  // allow: back/edit (<= currentPos) or next step (= currentPos + 1)
//                  if (submittedPos > currentPos + 1) {
//                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Group not allowed yet (cannot skip ahead)"));
//                  }
//
//                  boolean isBackEdit = submittedPos <= currentPos;
//
//                  String newStatus = (submittedPos == plan.lastPos())
//                    ? "READY_FOR_FINALISATION"
//                    : "IN_PROGRESS";
//
//                  Mono<Void> save = groupRepo.upsertValidated(app.applicationId(), groupNo, payloadJson);
//                  Mono<Void> invalidateLater = isBackEdit
//                    ? invalidateLaterGroups(app.applicationId(), plan, submittedPos)
//                    : Mono.empty();
//                  Mono<Integer> updateApp = appRepo.updateProgressSimple(app.applicationId(), newStatus, groupNo);
//                  Mono<Integer> touch = sessionRepo.touch(sessionId, newExpiresAt);
//
//                  return updateApp
//                    .then(save)
//                    .then(invalidateLater)
//                    .then(touch)
//                    .thenReturn(true);
//                })
//            )
//        )
//    );
//  }

  /**
   * Runtime-only submit:
   * - Always enforces journey order (cannot skip ahead)
   * - Stores payload envelope as jsonb
   * <p>
   * DEV-ONLY:
   * - forceInvalid=true simulates validation failure:
   * saves as INVALIDATED, does NOT advance application progress.
   */
//  public Mono<Boolean> submitGroup(UUID sessionId, String groupCode, JsonNode payload, boolean forceInvalid) {
//    final int groupNo;
//    try {
//      groupNo = Integer.parseInt(groupCode);
//    } catch (Exception e) {
//      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupCode must be numeric for now"));
//    }
//
//    OffsetDateTime now = OffsetDateTime.now();
//    OffsetDateTime newExpiresAt = now.plus(props.sessionTtl());
//    String payloadJson = payload == null ? "null" : payload.toString();
//
//    return tx.transactional(
//      sessionRepo.findActive(sessionId)
//        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.GONE, "Session expired")))
//        .flatMap(sess ->
//          appRepo.lockById(sess.applicationId())
//            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")))
//            .flatMap(app ->
//              journeyPlanService.getPlan(app.journeyCode())
//                .flatMap(plan -> {
//                  // --- 1) Journey membership check (always) ---
//                  Integer submittedPos = plan.posByGroupNo().get(groupNo);
//                  if (submittedPos == null) {
//                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group is not part of this journey"));
//                  }
//
//                  // --- 2) Order check (always) ---
//                  int currentGroupNo = app.currentGroupNo() == null ? 0 : app.currentGroupNo();
//                  int currentPos = (currentGroupNo == 0)
//                    ? 0
//                    : plan.posByGroupNo().getOrDefault(currentGroupNo, 0);
//
//                  // allow: back/edit (<= currentPos) or next step (= currentPos + 1)
//                  if (submittedPos > currentPos + 1) {
//                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Group not allowed yet (cannot skip ahead)"));
//                  }
//
//                  boolean isBackEdit = submittedPos <= currentPos;
//
//                  // --- 3) Save group state based on forced invalid ---
//                  Mono<Void> save = forceInvalid
//                    ? groupRepo.upsertInvalidated(app.applicationId(), groupNo, payloadJson)
//                    : groupRepo.upsertValidated(app.applicationId(), groupNo, payloadJson);
//
//                  // --- 4) Invalidate later groups only if we accepted (VALIDATED) a back-edit ---
//                  Mono<Void> invalidateLater = (!forceInvalid && isBackEdit)
//                    ? invalidateLaterGroups(app.applicationId(), plan, submittedPos)
//                    : Mono.empty();
//
//                  // --- 5) Update application progress only when VALIDATED ---
//                  Mono<Integer> updateApp;
//                  if (forceInvalid) {
//                    updateApp = Mono.just(0); // no-op: keep current_group_no unchanged
//                  } else {
//                    String newStatus = (submittedPos == plan.lastPos())
//                      ? "READY_FOR_FINALISATION"
//                      : "IN_PROGRESS";
//
//                    updateApp = appRepo.updateProgressSimple(app.applicationId(), newStatus, groupNo);
//                  }
//
//                  // --- 6) Touch session TTL on accepted request (even if INVALIDATED) ---
//                  Mono<Integer> touch = sessionRepo.touch(sessionId, newExpiresAt);
//
//                  return updateApp
//                    .then(save)
//                    .then(invalidateLater)
//                    .then(touch)
//                    .thenReturn(!forceInvalid); // validateResult: true if VALIDATED, false if INVALIDATED
//                })
//            )
//        )
//    );
//  }
  public Mono<SubmitOutcome> submitGroup(UUID sessionId, String groupCode, JsonNode payload, boolean forceInvalid) {
    final int groupNo;
    try {
      groupNo = Integer.parseInt(groupCode);
    } catch (Exception e) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupCode must be numeric for now"));
    }

    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime newExpiresAt = now.plus(props.sessionTtl());
    String payloadJson = payload == null ? "null" : payload.toString();

    return tx.transactional(
      requireActiveOrRenew(sessionId)
        .flatMap(ctx -> {
          UUID effectiveSessionId = ctx.session().sessionId();

          return appRepo.lockById(ctx.session().applicationId())
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")))
            .flatMap(app ->
              journeyPlanService.getPlan(app.journeyCode())
                .flatMap(plan -> {
                  Integer submittedPos = plan.posByGroupNo().get(groupNo);
                  if (submittedPos == null) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group is not part of this journey"));
                  }

                  int currentGroupNo = app.currentGroupNo() == null ? 0 : app.currentGroupNo();
                  int currentPos = (currentGroupNo == 0) ? 0 : plan.posByGroupNo().getOrDefault(currentGroupNo, 0);

                  if (submittedPos > currentPos + 1) {
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Group not allowed yet (cannot skip ahead)"));
                  }

                  boolean isBackEdit = submittedPos <= currentPos;

                  Mono<Void> save = forceInvalid
                    ? groupRepo.upsertInvalidated(app.applicationId(), groupNo, payloadJson)
                    : groupRepo.upsertValidated(app.applicationId(), groupNo, payloadJson);

                  Mono<Void> invalidateLater = (!forceInvalid && isBackEdit)
                    ? invalidateLaterGroups(app.applicationId(), plan, submittedPos)
                    : Mono.empty();

                  Mono<Integer> updateApp = forceInvalid
                    ? Mono.just(0)
                    : appRepo.updateProgressSimple(
                    app.applicationId(),
                    (submittedPos == plan.lastPos()) ? "READY_FOR_FINALISATION" : "IN_PROGRESS",
                    groupNo
                  );

                  // Touch the *effective* session
                  Mono<Integer> touch = sessionRepo.touch(effectiveSessionId, newExpiresAt);

                  return updateApp
                    .then(save)
                    .then(invalidateLater)
                    .then(touch)
                    .thenReturn(new SubmitOutcome(
                      !forceInvalid,
                      effectiveSessionId,
                      newExpiresAt,
                      ctx.renewed()
                    ));
                })
            );
        })
    );
  }

  // --------------------------------------------------------------------------
  // Restart
  // --------------------------------------------------------------------------
  public Mono<StartSessionResult> restartSession(String journeyCode, String customerRef) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime newExpiresAt = now.plus(props.sessionTtl());

    return tx.transactional(
      journeyPlanService.warmUp(journeyCode)
        .then(journeyConfigRepo.findJourneyIdByCode(journeyCode))
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid journeyCode")))
        .flatMap(journeyId ->
          appRepo.findLatestOpenByCustomerRef(customerRef, journeyId)
            .flatMap(oldApp ->
              appRepo.updateStatus(oldApp.applicationId(), "SUPERSEDED")
                .then(sessionRepo.expireAllActiveByApplicationId(oldApp.applicationId()))
                .thenReturn(true)
            )
            .switchIfEmpty(Mono.just(false))
            .then(appRepo.insertNew(
              UUID.randomUUID(),
              journeyId,
              journeyCode,
              customerRef,
              "IN_PROGRESS",
              0,
              0
            ))
        )
        .flatMap(newApp ->
          sessionRepo.insertNew(UUID.randomUUID(), newApp.applicationId(), "ACTIVE", newExpiresAt)
            .flatMap(sess -> buildSnapshot(newApp, sess, journeyCode))
        )
    );
  }

  private Mono<ObsApplicationEntity> createNewApplication(UUID journeyId, String journeyCode, String customerRef) {
    return appRepo.insertNew(
      UUID.randomUUID(),
      journeyId,
      journeyCode,
      customerRef,
      "IN_PROGRESS",
      0,
      0
    );
  }


  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  private Mono<StartSessionResult> buildSnapshot(ObsApplicationEntity app, ObsSessionEntity sess, String journeyCode) {
    return journeyPlanService.getPlan(journeyCode)
      .zipWith(groupRepo.findAllByApplicationId(app.applicationId()).collectList())
      .map(tuple -> {
        JourneyPlanService.JourneyPlan plan = tuple.getT1();
        List<ObsGroupStateEntity> states = tuple.getT2();

        Map<Integer, ObsGroupStateEntity> byGroupNo = states.stream()
          .collect(Collectors.toMap(ObsGroupStateEntity::groupNo, s -> s, (a, b) -> a));

        List<GroupSnapshotRow> rows = new ArrayList<>();
        for (int gno : plan.groupNosInOrder()) {
          ObsGroupStateEntity s = byGroupNo.get(gno);
          if (s == null) {
            rows.add(new GroupSnapshotRow(gno, "MISSING", null, null));
            continue;
          }

          boolean ok = "VALIDATED".equalsIgnoreCase(s.status());
          JsonNode payloadNode = safeReadJson(s.payloadJson());

          rows.add(new GroupSnapshotRow(
            gno,
            s.status(),
            ok ? Boolean.TRUE : Boolean.FALSE,
            payloadNode
          ));
        }

        Integer nextGroupNo = computeNextGroupNo(app.currentGroupNo(), plan);

        return new StartSessionResult(
          sess.sessionId(),
          app.applicationId(),
          journeyCode,
          app.currentGroupNo() == null ? 0 : app.currentGroupNo(),
          app.status(),
          sess.expiresAt(),
          nextGroupNo,
          rows
        );
      });
  }

  private JsonNode safeReadJson(String json) {
    if (json == null) return null;
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      return null;
    }
  }

  private Integer computeNextGroupNo(Integer currentGroupNo, JourneyPlanService.JourneyPlan plan) {
    int current = (currentGroupNo == null) ? 0 : currentGroupNo;
    if (current == 0) return plan.groupNosInOrder().get(0);

    Integer pos = plan.posByGroupNo().get(current);
    if (pos == null) return plan.groupNosInOrder().get(0);

    int nextPos = pos + 1;
    for (Map.Entry<Integer, Integer> e : plan.posByGroupNo().entrySet()) {
      if (e.getValue() == nextPos) return e.getKey();
    }
    return null;
  }

  private Mono<Void> invalidateLaterGroups(UUID applicationId, JourneyPlanService.JourneyPlan plan, int editedPos) {
    List<Integer> later = plan.posByGroupNo().entrySet().stream()
      .filter(e -> e.getValue() > editedPos)
      .map(Map.Entry::getKey)
      .sorted()
      .toList();

    if (later.isEmpty()) return Mono.empty();

    return Flux.fromIterable(later)
      .concatMap(gno -> groupRepo.invalidateOne(applicationId, gno))
      .then();
  }

  /**
   * Returns an ACTIVE session. If the provided sessionId is expired, it "auto-resumes":
   * - marks the old session expired (best effort)
   * - creates a new ACTIVE session for the same application
   * <p>
   * NOTE: This is convenience behaviour. In production you usually want auth checks.
   */
  private Mono<SessionCtx> requireActiveOrRenew(UUID sessionId) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime expiresAt = now.plus(props.sessionTtl());

    return sessionRepo.findActive(sessionId)
      .map(s -> new SessionCtx(s, false))
      .switchIfEmpty(
        // session is not active (expired or not ACTIVE). Try to find it anyway:
        sessionRepo.findById(sessionId)
          .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.GONE, "Session expired")))
          .flatMap(old -> {
            // Best-effort: mark old expired (no-op if already)
            Mono<Void> expireOld = sessionRepo.markExpired(old.sessionId()).then();

            // Create a fresh session pointing to the same application
            return expireOld
              .then(sessionRepo.insertNew(UUID.randomUUID(), old.applicationId(), "ACTIVE", expiresAt))
              .map(newSess -> new SessionCtx(newSess, true));
          })
      );
  }

  public record SubmitOutcome(
    boolean validateResult,
    UUID effectiveSessionId,
    OffsetDateTime sessionExpiresAt,
    boolean sessionRenewed
  ) {
  }

  private record SessionCtx(ObsSessionEntity session, boolean renewed) {
  }


  private record AppAndSession(ObsApplicationEntity app, ObsSessionEntity sess) {
  }

  // ---------------------------------------------------------------------------
  // Service DTOs (controller maps these)
  // ---------------------------------------------------------------------------

  public record StartSessionResult(
    UUID sessionId,
    UUID applicationId,
    String journeyCode,
    int currentGroupNo,
    String applicationStatus,
    OffsetDateTime sessionExpiresAt,
    Integer nextGroupNo,
    List<GroupSnapshotRow> groups
  ) {
  }

  public record CurrentResult(
    UUID sessionId,
    UUID applicationId,
    String journeyCode,
    int currentGroupNo,
    String applicationStatus,
    OffsetDateTime sessionExpiresAt,
    Integer nextGroupNo,
    List<GroupSnapshotRow> groups
  ) {
  }

  public record GroupSnapshotRow(
    int groupNo,
    String status,
    Boolean validateResult,
    JsonNode payload
  ) {
  }
}
