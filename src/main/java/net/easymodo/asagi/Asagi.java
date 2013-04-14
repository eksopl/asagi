package net.easymodo.asagi;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.easymodo.asagi.exception.BoardInitException;
import net.easymodo.asagi.settings.BoardSettings;
import net.easymodo.asagi.settings.OuterSettings;
import net.easymodo.asagi.settings.Settings;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class Asagi {
    private static final String SETTINGS_FILE = "./asagi.json";
    private static final String DEBUG_FILE = "./debug.log";

    private static BufferedWriter debugOut = null;
    private static String dumperEngine;
    private static String sourceEngine;

    public static BufferedWriter getDebugOut() {
        return debugOut;
    }

    private static BoardSettings getBoardSettings(Settings settings, String boardName) {
        BoardSettings defaults = settings.getBoardSettings().get("default");
        BoardSettings bSet = settings.getBoardSettings().get(boardName);

        bSet.initSettings(defaults);

        bSet.initSetting("path", defaults.getPath() + "/" + boardName + "/");
        bSet.initSetting("table", boardName);
        bSet.initSetting("useOldDirectoryStructure", false);

        return bSet;
    }

    private static void spawnBoard(String boardName, Settings settings) throws BoardInitException {
        BoardSettings bSet = getBoardSettings(settings, boardName);

        int pageLimbo = bSet.getDeletedThreadsThresholdPage();
        boolean fullMedia = (bSet.getMediaThreads() != 0);


        // Init source board engine through reflection
        Board sourceBoard;
        try {
            Class<?> sourceBoardClass = Class.forName("net.easymodo.asagi." + sourceEngine);
            sourceBoard = (Board) sourceBoardClass.getConstructor(String.class).newInstance(boardName);
        } catch (ClassNotFoundException e) {
            throw new BoardInitException("Error initializing board engine " + sourceEngine + ", no such engine?");
        } catch (Exception e) {
            throw new BoardInitException("Error initializing board engine " + sourceEngine);
        }

        // Same for DB engine
        String boardEngine = bSet.getEngine() == null ? "Mysql" : bSet.getEngine();
        bSet.setEngine(boardEngine);

        Class<?> sqlBoardClass;
        Constructor<?> boardCnst;

        // Init two DB objects: one for topic insertion and another
        // for media insertion
        Object topicDbObj;
        Object mediaDbObj;

        try {
            sqlBoardClass = Class.forName("net.easymodo.asagi." + boardEngine);
            boardCnst = sqlBoardClass.getConstructor(String.class, BoardSettings.class);

            // For topics
            topicDbObj = boardCnst.newInstance(bSet.getPath(), bSet);

            // For media
            mediaDbObj = boardCnst.newInstance(bSet.getPath(), bSet);
        } catch(ClassNotFoundException e) {
            throw new BoardInitException("Could not find board engine for " + boardEngine);
        } catch(NoSuchMethodException e) {
            throw new BoardInitException("Error initializing board engine " + boardEngine);
        } catch(InstantiationException e) {
            throw new BoardInitException("Error initializing board engine " + boardEngine);
        } catch(IllegalAccessException e) {
            throw new BoardInitException("Error initializing board engine " + boardEngine);
        } catch(InvocationTargetException e) {
            if(e.getCause() instanceof BoardInitException)
                throw (BoardInitException)e.getCause();
            else if(e.getCause() instanceof RuntimeException)
                throw (RuntimeException)e.getCause();
            throw new BoardInitException("Error initializing board engine " + boardEngine);
        }

        // Making sure we got valid DB engines for post and media insertion
        DB topicDb = null;
        DB mediaDb = null;

        if(topicDbObj instanceof DB && mediaDbObj instanceof DB) {
            topicDb = (DB) topicDbObj;
            mediaDb = (DB) mediaDbObj;
        }

        if(topicDb == null) {
            throw new BoardInitException("Wrong engine specified for " + boardEngine);
        }

        Local topicLocalBoard = new Local(bSet.getPath(), bSet, topicDb);
        Local mediaLocalBoard = new Local(bSet.getPath(), bSet, mediaDb);

        // And the dumper, le sigh.
        AbstractDumper dumper;
        try {
            Class<?> dumperClass = Class.forName("net.easymodo.asagi." + dumperEngine);
            dumper = (AbstractDumper) dumperClass.getConstructor(String.class, Local.class, Local.class, Board.class, boolean.class, int.class)
                    .newInstance(boardName, topicLocalBoard, mediaLocalBoard, sourceBoard, fullMedia, pageLimbo);
        } catch (ClassNotFoundException e) {
            throw new BoardInitException("Error initializing dumper engine " + dumperEngine + ", no such engine?");
        } catch (Exception e) {
            throw new BoardInitException("Error initializing dumper engine " + dumperEngine);
        }
        dumper.initDumper(bSet);
    }

    public static void main(String[] args) {
        Settings fullSettings;
        String settingsJson;
        Gson gson = new Gson();
        String settingsFileName = SETTINGS_FILE;

        for(int i = 0; i < args.length; ++i) {
            if(args[i].equals("--config") && ++i < args.length) {
                settingsFileName = args[i];
            }
        }

        File debugFile = new File(DEBUG_FILE);
        try {
            debugOut = new BufferedWriter(Files.newWriterSupplier(debugFile, Charsets.UTF_8, true).getOutput());
        } catch(IOException e1) {
            System.err.println("WARN: Cannot write to debug file");
        }

        BufferedReader settingsReader;
        if(settingsFileName.equals("-")) {
            settingsReader = new BufferedReader(new InputStreamReader(System.in, Charsets.UTF_8));
        } else {
            File settingsFile = new File(settingsFileName);
            try {
                settingsReader = Files.newReader(settingsFile, Charsets.UTF_8);
            } catch(FileNotFoundException e) {
                System.err.println("ERROR: Can't find settings file ("+ settingsFile + ")");
                return;
            }
        }

        try {
            settingsJson = CharStreams.toString(settingsReader);
        } catch(IOException e) {
            System.err.println("ERROR: Error while reading settings file");
            return;
        }

        OuterSettings outerSettings;
        try {
            outerSettings = gson.fromJson(settingsJson, OuterSettings.class);
        } catch(JsonSyntaxException e) {
            System.err.println("ERROR: Settings file is malformed!");
            return;
        }

        fullSettings = outerSettings.getSettings();

        dumperEngine = fullSettings.getDumperEngine();
        sourceEngine = fullSettings.getSourceEngine();
        if(dumperEngine == null) dumperEngine = "DumperJSON";
        if(sourceEngine == null) sourceEngine = "YotsubaJSON";

        for(String boardName : fullSettings.getBoardSettings().keySet()) {
            if("default".equals(boardName)) continue;
            try {
                spawnBoard(boardName, fullSettings);
            } catch(BoardInitException e) {
                System.err.println("ERROR: Error initializing dumper for /" + boardName + "/:");
                System.err.println("  " + e.getMessage());
            }
        }
    }
}
