package com.tuling.tim.gateway.controller;

import com.tuling.tim.gateway.api.RouteApi;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteApiValidationContractTest {

    @Test
    void overridingMethodsKeepValidationConstraintsOnTheInterface() throws Exception {
        for (Method implementation : RouteController.class.getDeclaredMethods()) {
            if (implementation.getParameterCount() != 1) continue;
            Method contract = RouteApi.class.getMethod(implementation.getName(), implementation.getParameterTypes());
            assertEquals(
                    contract.getParameters()[0].isAnnotationPresent(Valid.class),
                    implementation.getParameters()[0].isAnnotationPresent(Valid.class),
                    implementation.getName() + " must not redefine Bean Validation parameter constraints"
            );
        }
    }
}
