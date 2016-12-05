package org.wildfly.swarm.keycloak.internal;

import java.beans.PropertyDescriptor;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.representations.adapters.config.BaseAdapterConfig;
import org.keycloak.representations.adapters.config.BaseRealmConfig;

class KeycloakJsonGenerator {

    static final String PREFIX = "swarm.keycloak.adapter-config.";

    private static final Set<String> REQUESTED_ADAPTER_CONFIGS;

    static {
        REQUESTED_ADAPTER_CONFIGS = System.getProperties().keySet().stream()
                .map(Object::toString)
                .filter(key -> key.startsWith(PREFIX))
                .map(key -> key.substring(PREFIX.length()))
                .collect(Collectors.toSet());
    }

    static InputStream generate() {
        AdapterConfig adapterConfig = setupAdapterConfig();

        byte[] keycloakJson = null;
        try {
            keycloakJson = new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsBytes(adapterConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ByteArrayInputStream(keycloakJson);
    }

    private static AdapterConfig setupAdapterConfig() {
        AdapterConfig adapterConfig = new AdapterConfig();

        populateAllAdapterConfigs().forEach((json, field) -> {
            try {
                PropertyDescriptor property = new PropertyDescriptor(field.getName(), adapterConfig.getClass());
                set(adapterConfig, json, field, property.getWriteMethod());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return adapterConfig;
    }

    private static Map<String, Field> populateAllAdapterConfigs() {
        Map<String, Field> allAdapterConfigs = new HashMap<>();

        for (Field field : BaseRealmConfig.class.getDeclaredFields()) {
            allAdapterConfigs.put(field.getAnnotation(JsonProperty.class).value(), field);
        }
        for (Field field : BaseAdapterConfig.class.getDeclaredFields()) {
            allAdapterConfigs.put(field.getAnnotation(JsonProperty.class).value(), field);
        }
        for (Field field : AdapterConfig.class.getDeclaredFields()) {
            allAdapterConfigs.put(field.getAnnotation(JsonProperty.class).value(), field);
        }

        return allAdapterConfigs;
    }

    private static void set(AdapterConfig adapterConfig, String json, Field field, Method setter) throws IllegalAccessException, InvocationTargetException {
        if (! REQUESTED_ADAPTER_CONFIGS.contains(json)) {
            return;
        }

        if (field.getType() == boolean.class || field.getType() == Boolean.class) {
            setter.invoke(adapterConfig, Boolean.valueOf(System.getProperty(PREFIX + json)));
            return;
        }

        if (field.getType() == int.class  || field.getType() == Integer.class) {
            setter.invoke(adapterConfig, Integer.valueOf(System.getProperty(PREFIX + json)));
            return;
        }

        if (field.getType() == Map.class) {
            String[] pairs = System.getProperty(PREFIX + json).split(",");
            Map<String, Object> map = new HashMap<>();
            for (String pair : pairs) {
                map.put(pair.split("=")[0].trim(), pair.split("=")[1].trim());
            }
            setter.invoke(adapterConfig, map);
            return;
        }

        setter.invoke(adapterConfig, System.getProperty(PREFIX + json));
    }

}
