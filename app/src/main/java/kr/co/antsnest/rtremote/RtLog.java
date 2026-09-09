package kr.co.antsnest.rtremote;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Logger;

public class RtLog {
    private static final Logger LOGGER = Logger.getLogger(RtLog.class.getName());

    public static void info(String msg) {
        LOGGER.info(msg);
    }
    
    public static void warning(String msg) {
        LOGGER.warning(msg);
    }
    
    public static void severe(String msg) {
        LOGGER.severe(msg);
    }
    
    public static void setFileHandler(String fileName) throws IOException {
        FileHandler handler = new FileHandler(fileName, 512 * 1024, 4, true);
        handler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(handler);
    }
}
