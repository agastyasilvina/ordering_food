package com.bootleg.brevo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import com.bootleg.brevo.configuration.NativeResourcesHints;



@SpringBootApplication
@ImportRuntimeHints(NativeResourcesHints.class)
public class WebfluxRedisDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebfluxRedisDemoApplication.class, args);
	}

}
