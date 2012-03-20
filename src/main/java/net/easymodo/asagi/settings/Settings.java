package net.easymodo.asagi.settings;

import java.util.Map;

public class Settings {
    private Map<String, BoardSettings> settings;

    public Map<String, BoardSettings> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, BoardSettings> settings) {
        this.settings = settings;
    }
}
