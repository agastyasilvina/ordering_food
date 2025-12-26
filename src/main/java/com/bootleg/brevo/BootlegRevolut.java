package com.bootleg.brevo;

import com.bootleg.brevo.configuration.NativeResourcesHints;
import com.bootleg.brevo.runtime.config.ObsRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ImportRuntimeHints;


@SpringBootApplication
@ImportRuntimeHints(NativeResourcesHints.class)
@EnableConfigurationProperties(ObsRuntimeProperties.class)
public class BootlegRevolut {

  public static void main(String[] args) {
    SpringApplication.run(BootlegRevolut.class, args);
  }

}
