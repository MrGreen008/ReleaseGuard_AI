package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class Flagsmith {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final JsonHttp http;
    private final String sdkUrl;
    private final String managementUrl;
    private final String environmentKey;
    private final String managementKey;
    private final String managementEnvironment;
    private final int projectId;

    Flagsmith(JsonHttp http, @Value("${release.flags.sdk-url}") String sdkUrl,
              @Value("${release.flags.management-url}") String managementUrl,
              @Value("${release.flags.environment-key}") String environmentKey,
              @Value("${release.flags.management-key}") String managementKey,
              @Value("${release.flags.management-environment}") String managementEnvironment,
              @Value("${release.flags.project-id}") int projectId) {
        this.http = http;
        this.sdkUrl = sdkUrl;
        this.managementUrl = managementUrl;
        this.environmentKey = environmentKey;
        this.managementKey = managementKey;
        this.managementEnvironment = managementEnvironment;
        this.projectId = projectId;
    }

    Cohort evaluate(String identity) {
        if (environmentKey.isBlank()) {
            throw ServiceFault.unavailable("Flagsmith SDK key is missing.");
        }
        var response = http.send("Flagsmith SDK", sdkUrl + "/identities/?identifier=" + identity,
            "GET", Map.of("X-Environment-Key", environmentKey), null, TIMEOUT).requireOk("Flagsmith SDK");
        var flags = response.path("flags");
        if (flags.isArray()) {
            for (var flag : flags) {
                if (PAYMENT.equals(flag.path("feature").path("name").asText())) {
                    var enabled = flag.path("enabled");
                    String value = flag.path("feature_state_value").asText();
                    if (enabled.isBoolean() && (!enabled.asBoolean() || "stable".equals(value))) {
                        return Cohort.STABLE;
                    }
                    if (enabled.isBoolean() && "experimental".equals(value)) {
                        return Cohort.EXPERIMENTAL;
                    }
                }
            }
        }
        throw ServiceFault.unavailable("Flagsmith did not return a recognized payment variant.");
    }

    List<FlagState> inventory() {
        var environment = management("/environments/" + managementEnvironment + "/", "GET", null);
        int environmentId = environment.path("id").asInt();
        if (environmentId < 1 || environment.path("project").asInt() != projectId
            || !environment.path("api_key").asText().equals(managementEnvironment)
            || !environment.path("use_v2_feature_versioning").isBoolean()
            || environment.path("use_v2_feature_versioning").asBoolean()
            || environment.path("hide_disabled_flags").asBoolean(true)
            || environment.path("minimum_change_request_approvals").asInt() > 0) {
            throw ServiceFault.unavailable("Expected an isolated legacy Flagsmith environment with visible disabled flags.");
        }
        JsonNode states = management(statesPath() + "?page_size=100", "GET", null);
        JsonNode features = management("/projects/" + projectId + "/features/?page_size=100", "GET", null);
        var defaultStates = items(states);
        var projectFeatures = items(features);
        var result = new ArrayList<FlagState>();
        for (var state : defaultStates) {
            if (!isDefault(state)) {
                continue;
            }
            int featureId = featureId(state);
            var feature = projectFeatures.stream().filter(item -> item.path("id").asInt() == featureId)
                .findFirst().orElseThrow(() -> ServiceFault.unavailable("Flagsmith feature metadata is missing."));
            String name = feature.path("name").asText();
            if (!name.matches("[a-zA-Z0-9_-]{1,80}")) {
                throw ServiceFault.unavailable("Flagsmith returned an unsupported flag name.");
            }
            int overrides = PAYMENT.equals(name) ? paymentOverrides(environmentId, featureId) : 0;
            var variants = new ArrayList<Variant>();
            for (var allocation : state.path("multivariate_feature_state_values")) {
                int optionId = allocation.path("multivariate_feature_option").asInt();
                for (var option : feature.path("multivariate_options")) {
                    if (option.path("id").asInt() == optionId) {
                        variants.add(new Variant(paymentValue(option.path("string_value").asText()),
                            allocation.path("percentage_allocation").asDouble()));
                    }
                }
            }
            int stateId = state.path("id").asInt();
            if (stateId < 1 || featureId < 1 || state.path("environment").asInt() != environmentId
                || !state.path("enabled").isBoolean()) {
                throw ServiceFault.unavailable("Flagsmith returned incomplete flag configuration.");
            }
            // Exclude upstream secrets and free text from domain output. Use configuration for stale checks.
            String revision = digest(managementUrl + "|" + managementEnvironment + "|" + state
                + "|" + variants + "|" + overrides);
            result.add(new FlagState(stateId, featureId, name,
                state.path("enabled").asBoolean(), PAYMENT.equals(name)
                    ? paymentValue(state.path("feature_state_value").asText()) : "redacted",
                List.copyOf(variants), overrides, revision));
        }
        return List.copyOf(result);
    }

    FlagState payment() {
        var matches = inventory().stream().filter(flag -> PAYMENT.equals(flag.name())).toList();
        if (matches.size() != 1) {
            throw ServiceFault.unavailable("Expected exactly one Flagsmith payment environment state.");
        }
        return matches.get(0);
    }

    void disable(FlagState expected) {
        if (!PAYMENT.equals(expected.name()) || expected.stateId() < 1 || expected.overrides() != 0) {
            throw ServiceFault.conflict("Flag or override configuration is outside rollback policy.");
        }
        management(statesPath() + expected.stateId() + "/", "PATCH", Map.of("enabled", false));
    }

    boolean managementConfigured() {
        return !managementKey.isBlank() && managementEnvironment.matches("[A-Za-z0-9_-]+") && projectId > 0;
    }

    private String statesPath() {
        return "/environments/" + managementEnvironment + "/featurestates/";
    }

    private int paymentOverrides(int environmentId, int featureId) {
        if (environmentId < 1 || featureId < 1) {
            throw ServiceFault.unavailable("Flagsmith did not identify the payment environment.");
        }
        var segments = management("/features/feature-segments/?environment=" + environmentId + "&feature=" + featureId,
            "GET", null);
        var identities = management(statesPath() + "?anyIdentity=true&feature=" + featureId, "GET", null);
        return items(segments).size() + items(identities).size();
    }

    private JsonNode management(String path, String method, Object data) {
        if (!managementConfigured()) {
            throw ServiceFault.unavailable("Flagsmith management connection is not configured.");
        }
        return http.send("Flagsmith management", managementUrl + path, method,
            Map.of("Authorization", "Api-Key " + managementKey), data, TIMEOUT).requireOk("Flagsmith management");
    }

    private static List<JsonNode> items(JsonNode response) {
        var values = response.isArray() ? response : response.path("results");
        if (!values.isArray() || (response.hasNonNull("next") && !response.path("next").asText().isBlank())) {
            throw ServiceFault.unavailable("Flagsmith returned an unsupported or incomplete inventory.");
        }
        var items = new ArrayList<JsonNode>();
        values.forEach(items::add);
        return items;
    }

    private static boolean isDefault(JsonNode state) {
        return !state.hasNonNull("feature_segment") && !state.hasNonNull("identity");
    }

    private static int featureId(JsonNode state) {
        var feature = state.path("feature");
        return feature.isObject() ? feature.path("id").asInt() : feature.asInt();
    }

    private static String paymentValue(String value) {
        return switch (value) {
            case "stable", "experimental" -> value;
            default -> "unrecognized";
        };
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
