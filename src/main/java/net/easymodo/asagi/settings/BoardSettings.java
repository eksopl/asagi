package net.easymodo.asagi.settings;

import java.util.List;

public class BoardSettings {
    private String engine;
    private String database;
    private String host;
    private String username;
    private String password;
    private String table;
    private String path;
    private String webserverGroup;
    private Boolean fullMedia;
    private Integer thumbThreads;
    private Integer mediaThreads;
    private Integer newThreadsThreads;
    private Integer threadRefreshRate;
    private List<PageSettings> pageSettings;
    
    public String getEngine() {
        return engine;
    }
    public void setEngine(String engine) {
        this.engine = engine;
    }
    public String getDatabase() {
        return database;
    }
    public void setDatabase(String database) {
        this.database = database;
    }
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getTable() {
        return table;
    }
    public void setTable(String table) {
        this.table = table;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public Boolean getFullMedia() {
        return fullMedia;
    }
    public void setFullMedia(Boolean fullMedia) {
        this.fullMedia = fullMedia;
    }
    public Integer getThumbThreads() {
        return thumbThreads;
    }
    public void setThumbThreads(Integer thumbThreads) {
        this.thumbThreads = thumbThreads;
    }
    public Integer getMediaThreads() {
        return mediaThreads;
    }
    public void setMediaThreads(Integer mediaThreads) {
        this.mediaThreads = mediaThreads;
    }
    public Integer getNewThreadsThreads() {
        return newThreadsThreads;
    }
    public void setNewThreadsThreads(Integer newThreadsThreads) {
        this.newThreadsThreads = newThreadsThreads;
    }
    public Integer getThreadRefreshRate() {
        return threadRefreshRate;
    }
    public void setThreadRefreshRate(Integer threadRefreshRate) {
        this.threadRefreshRate = threadRefreshRate;
    }
    public List<PageSettings> getPageSettings() {
        return pageSettings;
    }
    public void setPageSettings(List<PageSettings> pageSettings) {
        this.pageSettings = pageSettings;
    }
    public String getWebserverGroup() {
        return webserverGroup;
    }
    public void setWebserverGroup(String webserverGroup) {
        this.webserverGroup = webserverGroup;
    }
}
