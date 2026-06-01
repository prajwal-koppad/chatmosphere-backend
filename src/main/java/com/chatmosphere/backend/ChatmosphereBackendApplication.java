package com.chatmosphere.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChatmosphereBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatmosphereBackendApplication.class, args);
	}

}
