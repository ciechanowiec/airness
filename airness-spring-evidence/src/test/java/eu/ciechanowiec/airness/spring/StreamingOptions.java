package eu.ciechanowiec.airness.spring;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
final class StreamingOptions implements WebMvcConfigurer {

    private final AtomicInteger calls;

    StreamingOptions() {
        this.calls = new AtomicInteger();
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        this.calls.incrementAndGet();
        configurer.setDefaultTimeout(0);
    }

    int calls() {
        return this.calls.get();
    }
}
