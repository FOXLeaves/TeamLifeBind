package com.teamlifebind.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

public final class TeamLifeBindLanguage {

    public static final String DEFAULT_LANGUAGE = "zh_cn";

    private static final String LANGUAGE_CONFIG_FILE = "language.properties";
    private static final String LANGUAGE_DIRECTORY = "lang";
    private static final String RESOURCE_ROOT = "teamlifebind/lang/";
    private static final List<String> BUNDLED_LANGUAGES = List.of(DEFAULT_LANGUAGE, "en_us");

    private final Map<String, String> entries;

    private TeamLifeBindLanguage(Map<String, String> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static TeamLifeBindLanguage load(Path baseDirectory, Consumer<String> warningLogger) {
        Consumer<String> logger = warningLogger != null ? warningLogger : ignored -> {
        };
        Path languageDirectory = baseDirectory.resolve(LANGUAGE_DIRECTORY);
        Path languageConfigFile = baseDirectory.resolve(LANGUAGE_CONFIG_FILE);

        ensureDirectory(baseDirectory, logger);
        ensureDirectory(languageDirectory, logger);
        ensureLanguageConfig(languageConfigFile, logger);
        for (String bundledLanguage : BUNDLED_LANGUAGES) {
            ensureBundledLanguageFile(languageDirectory, bundledLanguage, logger);
        }

        String configuredLanguage = readConfiguredLanguage(languageConfigFile, logger);
        Map<String, String> resolvedEntries = new LinkedHashMap<>();
        mergeInto(resolvedEntries, loadBundledLanguage(DEFAULT_LANGUAGE, logger));
        mergeInto(resolvedEntries, loadExternalLanguage(languageDirectory.resolve(DEFAULT_LANGUAGE + ".properties"), logger));

        if (!DEFAULT_LANGUAGE.equals(configuredLanguage)) {
            Map<String, String> selectedBundled = loadBundledLanguage(configuredLanguage, logger);
            Path selectedExternalPath = languageDirectory.resolve(configuredLanguage + ".properties");
            if (selectedBundled.isEmpty() && !Files.exists(selectedExternalPath)) {
                logger.accept(
                    "Language '" + configuredLanguage + "' was not found under " + languageDirectory + ". Falling back to " + DEFAULT_LANGUAGE + "."
                );
            } else {
                mergeInto(resolvedEntries, selectedBundled);
                mergeInto(resolvedEntries, loadExternalLanguage(selectedExternalPath, logger));
            }
        }

        return new TeamLifeBindLanguage(resolvedEntries);
    }

    public String text(String key, Object... args) {
        String template = entries.getOrDefault(key, "[" + key + "]");
        if (args == null || args.length == 0) {
            return template;
        }

        String formatted = template;
        for (int i = 0; i < args.length; i++) {
            formatted = formatted.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return formatted;
    }

    public String teamLabel(int team) {
        return text("team.label", team);
    }

    public String healthPresetName(HealthPreset preset) {
        Objects.requireNonNull(preset, "preset");
        return text("health_preset." + preset.name());
    }

    public String state(boolean enabled) {
        return text(enabled ? "state.enabled" : "state.disabled");
    }

    private static void ensureDirectory(Path directory, Consumer<String> logger) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            logger.accept("Failed to create directory " + directory + ": " + ex.getMessage());
        }
    }

    private static void ensureLanguageConfig(Path configFile, Consumer<String> logger) {
        if (Files.exists(configFile)) {
            return;
        }

        String content = """
            # TeamLifeBind language selector
            # Put custom language files in ./lang/<code>.properties
            language=zh_cn
            """;
        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            writer.write(content);
        } catch (IOException ex) {
            logger.accept("Failed to write language config " + configFile + ": " + ex.getMessage());
        }
    }

    private static void ensureBundledLanguageFile(Path languageDirectory, String languageCode, Consumer<String> logger) {
        Path target = languageDirectory.resolve(languageCode + ".properties");
        if (Files.exists(target)) {
            return;
        }

        String resourceName = RESOURCE_ROOT + languageCode + ".properties";
        try (InputStream inputStream = TeamLifeBindLanguage.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                logger.accept("Bundled language resource was not found: " + resourceName);
                return;
            }
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            logger.accept("Failed to copy bundled language " + resourceName + " to " + target + ": " + ex.getMessage());
        }
    }

    private static String readConfiguredLanguage(Path configFile, Consumer<String> logger) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException ex) {
            logger.accept("Failed to read language config " + configFile + ": " + ex.getMessage());
            return DEFAULT_LANGUAGE;
        }

        String configuredLanguage = normalizeLanguageCode(properties.getProperty("language"));
        return configuredLanguage != null ? configuredLanguage : DEFAULT_LANGUAGE;
    }

    private static Map<String, String> loadBundledLanguage(String languageCode, Consumer<String> logger) {
        String resourceName = RESOURCE_ROOT + languageCode + ".properties";
        try (InputStream inputStream = TeamLifeBindLanguage.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                return Map.of();
            }

            try (Reader reader = new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return readProperties(reader, resourceName, logger);
            }
        } catch (IOException ex) {
            logger.accept("Failed to load bundled language " + resourceName + ": " + ex.getMessage());
            return Map.of();
        }
    }

    private static Map<String, String> loadExternalLanguage(Path languageFile, Consumer<String> logger) {
        if (!Files.exists(languageFile)) {
            return Map.of();
        }

        try (Reader reader = Files.newBufferedReader(languageFile, StandardCharsets.UTF_8)) {
            return readProperties(reader, languageFile.toString(), logger);
        } catch (IOException ex) {
            logger.accept("Failed to read language file " + languageFile + ": " + ex.getMessage());
            return Map.of();
        }
    }

    private static Map<String, String> readProperties(Reader reader, String sourceName, Consumer<String> logger) {
        Properties properties = new Properties();
        try {
            properties.load(reader);
        } catch (IOException ex) {
            logger.accept("Failed to parse language file " + sourceName + ": " + ex.getMessage());
            return Map.of();
        }

        Map<String, String> loaded = new LinkedHashMap<>();
        for (String propertyName : properties.stringPropertyNames()) {
            loaded.put(propertyName, properties.getProperty(propertyName));
        }
        return loaded;
    }

    private static void mergeInto(Map<String, String> target, Map<String, String> source) {
        target.putAll(source);
    }

    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null || rawLanguageCode.isBlank()) {
            return null;
        }
        return rawLanguageCode.trim().toLowerCase().replace('-', '_');
    }
}
