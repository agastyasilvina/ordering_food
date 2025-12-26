package com.bootleg.brevo.runtime.controller;

import com.bootleg.brevo.preload.PreloadSnapshot;
import com.bootleg.brevo.preload.PreloadStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/config")
public class RuntimeConfigController {

  private final PreloadStore preloadStore;

  public RuntimeConfigController(PreloadStore preloadStore) {
    this.preloadStore = preloadStore;
  }

  /**
   * Rebuild snapshot from DB (one consistent global refresh).
   */
  @PostMapping("/refresh")
  public Mono<PreloadSnapshot> refreshAll() {
    return preloadStore.refreshAll();
  }

  /**
   * Return current snapshot (NO DB).
   */
  @GetMapping("/preloaded")
  public Mono<PreloadSnapshot> preloaded() {
    return Mono.just(preloadStore.current());
  }
}
