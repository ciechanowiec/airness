package com.example.starter;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The application that the container starts.
 */
@SpringBootApplication(proxyBeanMethods = false)
public final class Application {

    /**
     * Starts the application and hands the command line to the container.
     *
     * @param args arguments that the command line passes to the container
     */
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Answers the clock every instant and every date of the application is read from, in the zone the
     * deployment keeps. A date such as the day a task is due is a date in that zone, so whether it has
     * passed is asked of this clock rather than of the machine.
     *
     * @param zone the zone the deployment keeps, as an identifier such as {@code Europe/Warsaw}
     * @return the system clock in that zone
     */
    @Bean
    Clock clock(@Value("${app.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
