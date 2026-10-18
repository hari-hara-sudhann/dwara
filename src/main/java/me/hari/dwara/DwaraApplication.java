package me.hari.dwara;

import me.hari.dwara.configs.SessionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SessionProperties.class)
public class DwaraApplication {

	public static void main(String[] args) {
		SpringApplication.run(DwaraApplication.class, args);
	}

}
