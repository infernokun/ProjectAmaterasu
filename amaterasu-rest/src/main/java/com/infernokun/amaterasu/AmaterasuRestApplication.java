package com.infernokun.amaterasu;

import com.infernokun.amaterasu.templates.AmaterasuRestTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AmaterasuRestApplication implements CommandLineRunner {

	private final AmaterasuRestTemplate amaterasuRestTemplate;

    public AmaterasuRestApplication(AmaterasuRestTemplate amaterasuRestTemplate) {
        this.amaterasuRestTemplate = amaterasuRestTemplate;
    }

    public static void main(String[] args) {
		SpringApplication.run(AmaterasuRestApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("Docker Containers: " + amaterasuRestTemplate.getDockerContainers());
	}
}
