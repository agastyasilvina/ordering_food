package com.bootleg.brevo.runtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Reactive transaction wiring for R2DBC.
 * This lets us use SELECT ... FOR UPDATE safely in the submit flow.
 */
@Configuration
public class R2dbcTxConfig {

  @Bean
  public TransactionalOperator transactionalOperator(ReactiveTransactionManager txManager) {
    return TransactionalOperator.create(txManager);
  }
}
