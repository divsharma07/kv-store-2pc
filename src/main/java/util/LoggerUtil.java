package util;

import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logging Utility class.
 */
public class LoggerUtil extends SecurityManager {
  private static Logger getLogger() {
    String className = new LoggerUtil().getClassName();
    return Logger.getLogger(className);
  }

  /**
   * Writes logs
   * @param level level of log
   * @param message the message
   */
  public static void writeLog(Level level, String message) {
    Logger logger = getLogger();
    logger.log(level, message + "    " + new Timestamp(System.currentTimeMillis()));
  }

  private String getClassName() {
    return getClassContext()[2].getName();
  }
}