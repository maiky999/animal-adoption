package com.wsb.animaladoption;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AnimalAdoptionApplication {

	public static void main(String[] args) {
		SpringApplication.run(AnimalAdoptionApplication.class, args);
	}

}
