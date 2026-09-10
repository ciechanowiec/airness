package eu.ciechanowiec.airness.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration(proxyBeanMethods = false)
@EnableWebMvc
final class StreamingWeb {

    @Bean
    StreamingEndpoints streamingEndpoints() {
        return new StreamingEndpoints();
    }
}
