package com.spawnerx.managers;

import com.spawnerx.SpawnerX;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gerencia estado e validação assíncrona da licença.
 */
public class LicenseManager {

    private static final String VERIFY_ENDPOINT = "https://api-pluginsx.vercel.app/license/verify?code=";

    public enum LicenseStatus {
        INVALID,
        VALIDATING,
        VALID
    }

    private final SpawnerX plugin;
    private final HttpClient httpClient;
    private final AtomicReference<LicenseStatus> status = new AtomicReference<>(LicenseStatus.INVALID);
    private final AtomicLong validationSequence = new AtomicLong(0L);

    public LicenseManager(SpawnerX plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public LicenseStatus getStatus() {
        return status.get();
    }

    public boolean isValid() {
        return status.get() == LicenseStatus.VALID;
    }

    public boolean isLocked() {
        return !isValid();
    }

    /**
     * Inicia validação da licença salva no config.yml.
     */
    public void validateSavedLicense() {
        long sequence = validationSequence.incrementAndGet();
        setStatus(sequence, LicenseStatus.VALIDATING, "validation-started");

        String code = plugin.getConfigManager().getLicenseKey();
        if (code.isBlank()) {
            finishValidation(sequence, LicenseStatus.INVALID, "missing-license-code", "license.invalid");
            return;
        }

        URI uri;
        try {
            uri = URI.create(VERIFY_ENDPOINT + URLEncoder.encode(code, StandardCharsets.UTF_8));
        } catch (Exception e) {
            finishValidation(sequence, LicenseStatus.INVALID, "invalid-license-request-uri", "license.error");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofMillis(plugin.getConfigManager().getLicenseRequestTimeoutMs()))
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .whenComplete((response, throwable) -> {
                if (throwable != null) {
                    finishValidation(sequence, LicenseStatus.INVALID,
                        "license-api-request-failed: " + throwable.getClass().getSimpleName(), "license.error");
                    return;
                }

                if (response == null) {
                    finishValidation(sequence, LicenseStatus.INVALID, "license-api-null-response", "license.error");
                    return;
                }

                String body = response.body() == null ? "" : response.body().trim();
                boolean valid = response.statusCode() == 200 && "true".equalsIgnoreCase(body);
                if (valid) {
                    finishValidation(sequence, LicenseStatus.VALID, "license-verified", "license.valid");
                    return;
                }

                finishValidation(sequence, LicenseStatus.INVALID,
                    "license-api-invalid-response: status=" + response.statusCode() + ", body=" + shortenBody(body),
                    "license.invalid");
            });
    }

    private void finishValidation(long sequence, LicenseStatus newStatus, String reason, String localeMessagePath) {
        runOnMainThread(() -> {
            if (sequence != validationSequence.get()) {
                return;
            }
            updateStatus(newStatus, reason);
            notifyAdmins(localeMessagePath);
        });
    }

    private void setStatus(long sequence, LicenseStatus newStatus, String reason) {
        runOnMainThread(() -> {
            if (sequence != validationSequence.get()) {
                return;
            }
            updateStatus(newStatus, reason);
        });
    }

    private void updateStatus(LicenseStatus newStatus, String reason) {
        LicenseStatus previousStatus = status.getAndSet(newStatus);
        if (previousStatus != newStatus) {
            plugin.getLogger().info("Licença alterada: " + previousStatus + " -> " + newStatus + " (" + reason + ")");
        }
        plugin.onLicenseStatusChanged(newStatus, reason);
    }

    private void notifyAdmins(String localeMessagePath) {
        if (plugin.getLocaleManager() == null || plugin.getLocaleManager().getLocaleConfig() == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("spawnerx.admin")) {
                continue;
            }
            player.sendMessage(plugin.getLocaleManager().getMessage(localeMessagePath));
        }
    }

    private void runOnMainThread(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private String shortenBody(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= 48) {
            return body;
        }
        return body.substring(0, 48) + "...";
    }
}
