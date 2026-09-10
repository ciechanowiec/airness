package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitterReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBodyReturnValueHandler;

class SpringStreamingAdapterTest {

    private static final String UNSUPPORTED = "unsupported";

    @Test
    void rejectsMissingAndManuallyProvidedAdapters() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            assertEquals(UNSUPPORTED, SpringStreamingAdapter.inspect(context, List.of()).orElseThrow().status());
        }
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(RequestMappingHandlerAdapter.class);
            context.refresh();
            assertEquals(UNSUPPORTED, SpringStreamingAdapter.inspect(context, List.of()).orElseThrow().status());
        }
    }

    @Test
    void refusesAdditionalCustomDispatchWithoutInvokingIt() {
        try (
            AnnotationConfigWebApplicationContext context = StreamingWorld.context(
                StreamingWeb.class, StreamingOptions.class
            )
        ) {
            ProxyFactory proxy = new ProxyFactory(new RequestMappingHandlerAdapter());
            proxy.setInterfaces(HandlerAdapter.class);
            context.getBeanFactory().registerSingleton("customDispatch", proxy.getProxy());
            assertEquals(UNSUPPORTED, SpringStreamingAdapter.inspect(context, List.of()).orElseThrow().status());
        }
    }

    @Test
    void countsPrototypeAdaptersWithoutInstantiatingThem() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            RootBeanDefinition adapter = new RootBeanDefinition(RequestMappingHandlerAdapter.class);
            adapter.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            adapter.setInstanceSupplier(
                () -> {
                    throw new IllegalStateException("Inspection must not construct an adapter");
                }
            );
            context.registerBeanDefinition("prototypeAdapter", adapter);
            context.refresh();
            assertEquals(UNSUPPORTED, SpringStreamingAdapter.inspect(context, List.of()).orElseThrow().status());
        }
    }

    @Test
    void proxyAndUnknownFieldShapesAreNotAssessedAsStandard() {
        ProxyFactory proxy = new ProxyFactory(new RequestMappingHandlerAdapter());
        proxy.setProxyTargetClass(true);
        assertEquals(
            UNSUPPORTED, SpringStreamingAdapter.configured((RequestMappingHandlerAdapter) proxy.getProxy())
                .orElseThrow().status()
        );
        assertEquals(UNSUPPORTED, SpringStreamingAdapter.timeout(new Object()).orElseThrow().status());
        assertEquals(UNSUPPORTED, SpringStreamingAdapter.representation("changed field type").orElseThrow().status());
    }

    @Test
    void refusesAReplacedStreamingReturnPipeline() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(
            List.of(StreamingWorld.type(StreamingEndpoints.class, "none"))
        );
        try (
            AnnotationConfigWebApplicationContext context = StreamingWorld.context(
                StreamingWeb.class, StreamingOptions.class
            )
        ) {
            RequestMappingHandlerAdapter adapter = context.getBean(RequestMappingHandlerAdapter.class);
            assertTrue(
                SpringStreamingReturns.inspect(adapter, SpringStreamingEndpoints.registered(context, inputs)).isEmpty()
            );
            adapter.setReturnValueHandlers(
                List.of(new RequestResponseBodyMethodProcessor(adapter.getMessageConverters()))
            );
            assertEquals(
                UNSUPPORTED, SpringStreamingReturns.inspect(
                    adapter, SpringStreamingEndpoints.registered(context, inputs)
                )
                    .orElseThrow().status()
            );
            adapter.setReturnValueHandlers(List.of());
            assertEquals(
                UNSUPPORTED, SpringStreamingReturns.inspect(
                    adapter, SpringStreamingEndpoints.registered(context, inputs)
                )
                    .orElseThrow().status()
            );
        }
    }

    @Test
    void rejectsFrameworkWrappersWithoutInvokingTheirCustomHandlers() {
        HandlerMethodReturnValueHandler custom = (HandlerMethodReturnValueHandler) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {HandlerMethodReturnValueHandler.class},
            (_, _, _) -> {
                throw new IllegalStateException("Custom return handlers must not run during inspection");
            }
        );
        HandlerMethodReturnValueHandlerComposite wrapper = new HandlerMethodReturnValueHandlerComposite().addHandler(
            custom
        );
        try (
            AnnotationConfigWebApplicationContext context = StreamingWorld.context(
                StreamingWeb.class, StreamingOptions.class
            )
        ) {
            RequestMappingHandlerAdapter adapter = context.getBean(RequestMappingHandlerAdapter.class);
            adapter.setReturnValueHandlers(List.of(wrapper, new StreamingResponseBodyReturnValueHandler()));
            SpringStreamingInputs inputs = StreamingWorld.inputs(
                List.of(StreamingWorld.type(StreamingWeb.class, "none"))
            );
            assertEquals(
                UNSUPPORTED, SpringStreamingReturns.inspect(
                    adapter, SpringStreamingEndpoints.registered(context, inputs)
                ).orElseThrow().status()
            );
        }
    }

    @Test
    void refusesAnEmitterWithACustomReactiveRegistry() {
        ProxyFactory proxy = new ProxyFactory(new ReactiveAdapterRegistry());
        proxy.setProxyTargetClass(true);
        ReactiveAdapterRegistry registry = (ReactiveAdapterRegistry) proxy.getProxy();
        try (
            AnnotationConfigWebApplicationContext context = StreamingWorld.context(
                StreamingWeb.class, StreamingOptions.class
            )
        ) {
            RequestMappingHandlerAdapter adapter = context.getBean(RequestMappingHandlerAdapter.class);
            ResponseBodyEmitterReturnValueHandler emitter = new ResponseBodyEmitterReturnValueHandler(
                adapter.getMessageConverters(), registry, new SyncTaskExecutor(), new ContentNegotiationManager()
            );
            adapter.setReturnValueHandlers(List.of(emitter, new StreamingResponseBodyReturnValueHandler()));
            SpringStreamingInputs inputs = StreamingWorld.inputs(
                List.of(StreamingWorld.type(StreamingWeb.class, "none"))
            );
            assertEquals(
                UNSUPPORTED, SpringStreamingReturns.inspect(
                    adapter, SpringStreamingEndpoints.registered(context, inputs)
                ).orElseThrow().status()
            );
        }
    }
}
