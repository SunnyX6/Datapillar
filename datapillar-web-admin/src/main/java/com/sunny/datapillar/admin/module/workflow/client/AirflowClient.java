package com.sunny.datapillar.admin.module.workflow.client;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.admin.config.AirflowConfig;
import com.sunny.datapillar.admin.response.WebAdminErrorCode;
import com.sunny.datapillar.admin.response.WebAdminException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Airflow HTTP 客户端
 * 使用 OkHttp 封装与 Airflow 插件的所有 HTTP 交互
 *
 * @author sunny
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirflowClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AirflowConfig airflowConfig;
    private final ObjectMapper objectMapper;

    private OkHttpClient httpClient;
    private String cachedToken;
    private Instant tokenExpiry;

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(airflowConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(airflowConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(airflowConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 获取 JWT Token（带缓存）
     */
    private String getToken() {
        if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        try {
            String jsonBody = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
                    airflowConfig.getUsername(), airflowConfig.getPassword());

            Request request = new Request.Builder()
                    .url(airflowConfig.getTokenUrl())
                    .post(RequestBody.create(jsonBody, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonNode jsonNode = objectMapper.readTree(response.body().string());
                    if (jsonNode.has("access_token")) {
                        cachedToken = jsonNode.get("access_token").asText();
                        tokenExpiry = Instant.now().plusSeconds(23 * 3600);
                        log.info("Airflow token refreshed");
                        return cachedToken;
                    }
                }
                throw new WebAdminException(WebAdminErrorCode.AIRFLOW_AUTH_FAILED,
                        "Failed to get Airflow token, response code: " + response.code());
            }
        } catch (WebAdminException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to authenticate with Airflow: {}", e.getMessage());
            throw new WebAdminException(WebAdminErrorCode.AIRFLOW_AUTH_FAILED,
                    "Airflow authentication failed: " + e.getMessage());
        }
    }

    /**
     * GET 请求
     */
    public <T> T get(String path, Class<T> responseType) {
        String url = airflowConfig.getPluginUrl() + path;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + getToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return handleResponse(response, responseType, "GET", path);
        } catch (WebAdminException e) {
            throw e;
        } catch (Exception e) {
            log.error("Airflow GET {} failed: {}", path, e.getMessage());
            throw new WebAdminException(WebAdminErrorCode.AIRFLOW_REQUEST_FAILED,
                    "Airflow request failed: " + e.getMessage());
        }
    }

    /**
     * POST 请求
     */
    public <T, R> T post(String path, R body, Class<T> responseType) {
        String url = airflowConfig.getPluginUrl() + path;

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("Airflow POST {} body: {}", path, jsonBody);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(jsonBody, JSON))
                    .addHeader("Authorization", "Bearer " + getToken())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return handleResponse(response, responseType, "POST", path);
            }
        } catch (WebAdminException e) {
            throw e;
        } catch (Exception e) {
            log.error("Airflow POST {} failed: {}", path, e.getMessage());
            throw new WebAdminException(WebAdminErrorCode.AIRFLOW_REQUEST_FAILED,
                    "Airflow request failed: " + e.getMessage());
        }
    }

    /**
     * PATCH 请求
     */
    public <T, R> T patch(String path, R body, Class<T> responseType) {
        String url = airflowConfig.getPluginUrl() + path;

        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(jsonBody, JSON))
                    .addHeader("Authorization", "Bearer " + getToken())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return handleResponse(response, responseType, "PATCH", path);
            }
        } catch (WebAdminException e) {
            throw e;
        } catch (Exception e) {
            log.error("Airflow PATCH {} failed: {}", path, e.getMessage());
            throw new WebAdminException(WebAdminErrorCode.AIRFLOW_REQUEST_FAILED,
                    "Airflow request failed: " + e.getMessage());
        }
    }

    /**
     * DELETE 请求
     */
    public <T> T delete(String path, Class<T> responseType) {
        String url = airflowConfig.getPluginUrl() + path;

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer " + getToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return handleResponse(response, responseType, "DELETE", path);
        } catch (WebAdminException e) {
            throw e;
        } catch (Exception e) {
            log.error("Airflow DELETE {} failed: {}", path, e.getMessage());
            throw new WebAdminException(WebAdminErrorCode.AIRFLOW_REQUEST_FAILED,
                    "Airflow request failed: " + e.getMessage());
        }
    }

    /**
     * 处理响应
     */
    private <T> T handleResponse(Response response, Class<T> responseType, String method, String path) throws Exception {
        if (response.isSuccessful() && response.body() != null) {
            String responseBody = response.body().string();
            return objectMapper.readValue(responseBody, responseType);
        }

        String errorBody = response.body() != null ? response.body().string() : "";
        log.error("Airflow {} {} failed: {} {}", method, path, response.code(), errorBody);
        throw new WebAdminException(WebAdminErrorCode.AIRFLOW_REQUEST_FAILED,
                "Airflow request failed: " + response.code() + " " + errorBody);
    }
}
