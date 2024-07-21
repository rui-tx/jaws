package org.ruitx.server.configs;

import org.slf4j.simple.SimpleLogger;

public class ProjectSystemSettings {

    public static void setProperties() {
        // SLF4J's SimpleLogger configurations
        System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true");
        System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH:mm:ss:SSS");
        System.setProperty(SimpleLogger.LEVEL_IN_BRACKETS_KEY, "true");
    }
}
