package com.bootleg.brevo.misc;


import org.springframework.boot.CommandLineRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

// Testing connection to the db
@Component
public class DbPing implements CommandLineRunner {

  private final DatabaseClient db;

  public DbPing(DatabaseClient db) {
    this.db = db;
  }

  @Override
  public void run(String... args) {
    db.sql("SELECT current_database() AS db, current_schema() AS schema")
      .fetch()
      .one()
      .doOnNext(m -> System.out.println("DB PING => " + m))
      .doOnError(e -> e.printStackTrace())
      .subscribe();
  }
}
