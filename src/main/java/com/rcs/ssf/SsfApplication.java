package com.rcs.ssf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.rcs.ssf.bootstrap.SchemaBootstrapConfiguration;

@SpringBootApplication
@Import(SchemaBootstrapConfiguration.class)
public class SsfApplication {

	public static void main(String[] args) {
		SpringApplication.run(SsfApplication.class, args);
	}

}
