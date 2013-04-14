package net.easymodo.asagi.settings;

import java.util.Map;

public class Settings {
    private String dumperEngine;
    private String sourceEngine;
    private Map<String, BoardSettings> boardSettings;


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