package com.bootleg.brevo;

import com.bootleg.brevo.configuration.NativeResourcesHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;


@SpringBootApplication
@ImportRuntimeHints(NativeResourcesHints.class)
public class BootlegRevolut {

  public static void main(String[] args) {
    SpringApplication.run(BootlegRevolut.class, args);
  }

}
