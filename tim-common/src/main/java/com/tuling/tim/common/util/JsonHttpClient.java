package com.tuling.tim.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import java.io.IOException;

/**
 * Sends JSON requests to an explicitly declared HTTP endpoint.
 */
public final class JsonHttpClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonHttpClient() {
    }

    public static Response post(OkHttpClient okHttpClient, String baseUrl, String path, Object request)
            throws IOException {
        try {
            return HttpClient.call(okHttpClient, OBJECT_MAPPER.writeValueAsString(request), endpoint(baseUrl, path));
        } catch (JsonProcessingException e) {
            throw new IOException("Unable to serialize HTTP request for " + path, e);
        }
    }

    private static String endpoint(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBaseUrl + normalizedPath;
    }
}
