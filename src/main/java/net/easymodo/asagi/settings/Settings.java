package net.easymodo.asagi.settings;

import java.util.Map;

@SuppressWarnings("UnusedDeclaration")
public class Settings {
    private String dumperEngine;
    private String sourceEngine;
    private SiteSettings siteSettings;
    private Map<String, BoardSettings> boardSettings;

    public SiteSettings getSiteSettings() {
        return siteSettings;
    }

    public void setSiteSettings(SiteSettings siteSettings) {
        this.siteSettings = siteSettings;
    }

    public Map<String, BoardSettings> getBoardSettings() {
        return boardSettings;
    }

    public void setBoardSettings(Map<String, BoardSettings> boardSettings) {
        this.boardSettings = boardSettings;
    }

    public String getDumperEngine() {
        return dumperEngine;
    }

    public void setDumperEngine(String dumperEngine) {
        this.dumperEngine = dumperEngine;
    }

    public String getSourceEngine() {
        return sourceEngine;
    }

    public void setSourceEngine(String sourceEngine) {
        this.sourceEngine = sourceEngine;
    }
}
