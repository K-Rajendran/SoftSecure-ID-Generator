package com.softsecure.id.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuration is explicit; missing secrets fail closed. Never log this object. */
public final class AppConfig {
    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    public static AppConfig load() throws IOException {
        Properties properties = new Properties();
        Path path = Path.of(System.getProperty("softsecure.config", "config/application.properties"));
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return new AppConfig(properties);
    }

    public String value(String key, String fallback) {
        String environment = System.getenv("SOFTSECURE_" + key.replace('.', '_').toUpperCase(java.util.Locale.ROOT));
        return environment != null ? environment : properties.getProperty(key, fallback);
    }

    public String required(String key) {
        String value = value(key, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing configuration: " + key);
        }
        return value;
    }

    public boolean flag(String key) {
        String value = value(key, "false");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Invalid boolean configuration: " + key);
        }
        return Boolean.parseBoolean(value);
    }

    public int number(String key, int fallback, int minimum, int maximum) {
        int number = Integer.parseInt(value(key, Integer.toString(fallback)));
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException("Out-of-range configuration: " + key);
        }
        return number;
    }
}
