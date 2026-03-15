package io.github.wboult.esrunner.gradle;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElasticClusterSpecParityTest {
    @Test
    void buildServiceParamsMirrorClusterSpecGetters() {
        assertEquals(getterNames(ElasticClusterSpec.class), getterNames(ElasticClusterService.Params.class));
    }

    private Set<String> getterNames(Class<?> type) {
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().startsWith("get"))
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> !"getClass".equals(method.getName()))
                .filter(method -> declaresGetter(type, method))
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private boolean declaresGetter(Class<?> type, Method method) {
        try {
            type.getDeclaredMethod(method.getName());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
