package net.easymodo.asagi.settings;

import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class BoardSettings {
    private String engine;
    private String database;
    private String host;
    private String username;
    private String password;
    private String charset;
    private String table;
    private String path;
    private Boolean useOldDirectoryStructure;
    private Integer deletedThreadsThresholdPage;
    private String webserverGroup;
    private Boolean fullMedia;
    private Integer thumbThreads;
    private Integer mediaThreads;
    private Integer newThreadsThreads;
    private Integer threadRefreshRate;
    private Integer refreshDelay;
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
    public String getCharset() {
        return charset;
    }
    public void setCharset(String charset) {
        this.charset = charset;
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
    public Boolean getUseOldDirectoryStructure() {
        return useOldDirectoryStructure;
    }
    public void setUseOldDirectoryStructure(Boolean useOldDirectoryStructure) {
        this.useOldDirectoryStructure = useOldDirectoryStructure;
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
    public Integer getRefreshDelay() {
        return refreshDelay;
    }
    public void setRefreshDelay(Integer refreshDelay) {
        this.refreshDelay = refreshDelay;
    }
    public List<PageSettings> getPageSettings() {
        return pageSettings;
    }
    public void setPageSettings(List<PageSettings> pageSettings) {
        this.pageSettings = pageSettings;
    }
    public Integer getDeletedThreadsThresholdPage() {
        return deletedThreadsThresholdPage;
    }
    public void setDeletedThreadsThresholdPage(Integer deletedThreadsThresholdPage) {
        this.deletedThreadsThresholdPage = deletedThreadsThresholdPage;
    }
    public String getWebserverGroup() {
        return webserverGroup;
    }
    public void setWebserverGroup(String webserverGroup) {
        this.webserverGroup = webserverGroup;
    }

    public void initSettings(BoardSettings defaults) {
        try {
            new BeanUtilsNoOverwrite().copyProperties(this, defaults);
        } catch (Exception e) {
            throw new AssertionError("Error initing settings in BoardSettings");
        }
    }

    public void initSetting(String key, Object def) {
        try {
            if(BeanUtils.getProperty(this, key) == null)
                BeanUtils.setProperty(this, key, def);
        } catch (Exception e) {
            throw new AssertionError("Error initing settings in BoardSettings");
        }
    }
}
