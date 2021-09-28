package org.jahia.modules.aclcleanup;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ScriptLogger {

    private final Logger logger;
    private final List<String> buffer;

    public ScriptLogger(Logger logger) {
        this.logger = logger;
        buffer = new ArrayList<>();
    }

    public List<String> getBuffer() {
        return getBuffer(true);
    }

    public List<String> getBuffer(boolean clear) {
        final ArrayList<String> copy = new ArrayList<>(buffer);
        if (clear)
            buffer.clear();
        return copy;
    }

    public void clear() {
        buffer.clear();
    }

    public void info(String msg) {
        info(msg, null);
    }

    public void info(String msg, Throwable t) {
        if (!logger.isInfoEnabled()) return;
        addToBuffer(msg, t);
        if (t == null) {
            logger.info(msg);
        } else {
            logger.info(msg, t);
        }
    }

    public void error(String msg) {
        error(msg, null);
    }

    public void error(String msg, Throwable t) {
        if (!logger.isErrorEnabled()) return;
        addToBuffer(msg, t);
        if (t == null) {
            logger.error(msg);
        } else {
            logger.error(msg, t);
        }
    }

    public void debug(String msg) {
        debug(msg, null);
    }

    public void debug(String msg, Throwable t) {
        if (!logger.isDebugEnabled()) return;
        addToBuffer(msg, t);
        if (t == null) {
            logger.debug(msg);
        } else {
            logger.debug(msg, t);
        }
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void warn(String msg) {
        warn(msg, null);
    }

    public void warn(String msg, Throwable t) {
        if (!logger.isWarnEnabled()) return;
        addToBuffer(msg, t);
        if (t == null) {
            logger.warn(msg);
        } else {
            logger.warn(msg, t);
        }
    }

    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    private void addToBuffer(String msg, Throwable t) {
        buffer.add(getMessage(msg, t));
    }
    private String getMessage(String msg, Throwable t) {
        if (StringUtils.isNotBlank(msg)) return msg;
        if (t == null) return StringUtils.EMPTY;
        return StringUtils.defaultIfBlank(t.getMessage(), StringUtils.EMPTY);
    }
}
