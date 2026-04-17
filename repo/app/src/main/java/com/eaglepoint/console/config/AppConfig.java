package com.eaglepoint.console.config;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static AppConfig instance;
    private final Properties props = new Properties();

    private AppConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("app.properties")) {
            if (is != null) props.load(is);
        } catch (Exception e) {
            // defaults will be used
        }
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) instance = new AppConfig();
        return instance;
    }

    public static void init() {
        getInstance();
    }

    /**
     * Resolve a config value by checking (in order):
     * <ol>
     *   <li>Each of the supplied {@code envKeys} in {@link System#getenv} —
     *       this accepts both the historical {@code APP_}-prefixed names and
     *       the bare names documented in {@code docker-compose.yml} / README.</li>
     *   <li>{@link System#getProperty} on {@code sysPropKey}.</li>
     *   <li>{@code app.properties} under {@code propsKey}.</li>
     *   <li>The supplied default.</li>
     * </ol>
     */
    private String resolve(String[] envKeys, String sysPropKey, String propsKey, String defaultValue) {
        for (String key : envKeys) {
            String v = System.getenv(key);
            if (v != null && !v.isEmpty()) return v;
        }
        String v = System.getProperty(sysPropKey);
        if (v != null && !v.isEmpty()) return v;
        v = props.getProperty(propsKey);
        if (v != null && !v.isEmpty()) return v;
        return defaultValue;
    }

    public int getApiPort() {
        return Integer.parseInt(resolve(new String[]{"API_PORT", "APP_API_PORT"},
            "api.port", "api.port", "8080"));
    }

    public String getDbPath() {
        return resolve(new String[]{"DB_PATH", "APP_DB_PATH"},
            "db.path", "db.path", "data/console.db");
    }

    public String getBackupDir() {
        return resolve(new String[]{"BACKUP_DIR", "APP_BACKUP_DIR"},
            "backup.dir", "backup.dir", "data/backups");
    }

    public String getLogDir() {
        return resolve(new String[]{"LOG_DIR", "APP_LOG_DIR"},
            "log.dir", "log.dir", "logs");
    }

    public String getVersion() {
        return props.getProperty("app.version", "1.0.0");
    }

    public boolean isHeadless() {
        String v = System.getProperty("app.headless");
        if (v != null) return "true".equalsIgnoreCase(v);
        v = System.getenv("APP_HEADLESS");
        if (v != null) return "true".equalsIgnoreCase(v);
        return "true".equalsIgnoreCase(props.getProperty("app.headless", "false"));
    }
}
