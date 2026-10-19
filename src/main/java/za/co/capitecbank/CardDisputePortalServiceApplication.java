package za.co.capitecbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class CardDisputePortalServiceApplication {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    public static void main(final String[] args) {
        SpringApplication.run(CardDisputePortalServiceApplication.class, args);
    }
}
