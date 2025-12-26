package com.bootleg.brevo.runtime.service;

import com.bootleg.brevo.runtime.repo.BrevoJourneyPlanRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JourneyPlanService {

  private final BrevoJourneyPlanRepository repo;
  private final Map<String, Mono<JourneyPlan>> cache = new ConcurrentHashMap<>();

  public JourneyPlanService(BrevoJourneyPlanRepository repo) {
    this.repo = repo;
  }

  public Mono<JourneyPlan> getPlan(String journeyCode) {
    return cache.computeIfAbsent(journeyCode, jc ->
      repo.fetchPlan(jc)
        .collectList()
        .map(rows -> {
          if (rows.isEmpty()) throw new IllegalArgumentException("Journey has no groups: " + jc);

          Map<Integer, Integer> posByGroup = new HashMap<>();
          List<Integer> order = new ArrayList<>();
          int lastPos = 0;

          for (var r : rows) {
            posByGroup.put(r.groupNo(), r.position());
            order.add(r.groupNo());
            lastPos = Math.max(lastPos, r.position());
          }
          return new JourneyPlan(jc, List.copyOf(order), Map.copyOf(posByGroup), lastPos);
        })
        .cache()
    );
  }

  public Mono<Void> warmUp(String journeyCode) {
    return getPlan(journeyCode).then();
  }

  public record JourneyPlan(
    String journeyCode,
    List<Integer> groupNosInOrder,
    Map<Integer, Integer> posByGroupNo,
    int lastPos
  ) {
  }
}
