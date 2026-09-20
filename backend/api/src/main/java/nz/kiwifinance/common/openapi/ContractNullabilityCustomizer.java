package nz.kiwifinance.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import nz.kiwifinance.common.web.PageResponse;
import nz.kiwifinance.engine.explain.Explanation;
import nz.kiwifinance.engine.progress.Streaks;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Makes the contract precise about response fields so generated clients get exact types. Every
 * field of a response is always serialised, so all are marked required; fields whose type carries
 * JSpecify's {@code @Nullable} may be null. Request schemas keep the requirements springdoc derives
 * from their validation annotations.
 */
@Component
class ContractNullabilityCustomizer implements OpenApiCustomizer {

    private static final String BASE_PACKAGE = "nz.kiwifinance";
    private static final String REQUEST_SUFFIX = "Request";

    private final Map<String, Class<?>> typesBySchemaName = new HashMap<>();

    ContractNullabilityCustomizer() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(io.swagger.v3.oas.annotations.media.Schema.class));
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = ClassUtils.resolveClassName(
                    candidate.getBeanClassName(), getClass().getClassLoader());
            typesBySchemaName.put(
                    type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class)
                            .name(),
                    type);
        }
        typesBySchemaName.put("Explanation", Explanation.class);
        typesBySchemaName.put("Step", Explanation.Step.class);
        typesBySchemaName.put("Assumption", Explanation.Assumption.class);
        typesBySchemaName.put("Streaks", Streaks.class);
        typesBySchemaName.put("PageResponseTransaction", PageResponse.class);
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        openApi.getComponents().getSchemas().forEach((name, raw) -> {
            Schema<?> schema = raw;
            Class<?> type = typesBySchemaName.get(name);
            if (name.endsWith(REQUEST_SUFFIX) || schema.getProperties() == null || type == null || !type.isRecord()) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Schema<?>> properties = (Map<String, Schema<?>>) (Map<?, ?>) schema.getProperties();
            schema.setRequired(properties.keySet().stream().sorted().toList());
            for (String property : nullableComponents(type)) {
                Schema<?> current = properties.get(property);
                if (current != null) {
                    properties.put(property, nullable(current));
                }
            }
        });
    }

    private static Set<String> nullableComponents(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .filter(component -> component.getAnnotatedType().isAnnotationPresent(Nullable.class))
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static Schema<?> nullable(Schema<?> property) {
        if (property.get$ref() != null) {
            Schema<?> reference = new Schema<>().$ref(property.get$ref());
            Schema<?> none = new Schema<>();
            none.setTypes(new LinkedHashSet<>(List.of("null")));
            Schema<?> wrapper = new Schema<>();
            wrapper.setOneOf(new ArrayList<>(List.of(reference, none)));
            return wrapper;
        }
        Set<String> types = new LinkedHashSet<>();
        if (property.getTypes() != null) {
            types.addAll(property.getTypes());
        } else if (property.getType() != null) {
            types.add(property.getType());
        }
        types.add("null");
        property.setTypes(types);
        return property;
    }
}
