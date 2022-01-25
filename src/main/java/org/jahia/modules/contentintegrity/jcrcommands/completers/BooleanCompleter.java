package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Completer for String options taking a boolean value. Should be used when it makes a difference between setting the option to true/false or not defining it.
 * If the option is considered as true or false when not defined, then a real Boolean option should be used.
 */
@Service
public class BooleanCompleter extends SimpleCompleter {

    private static final Logger logger = LoggerFactory.getLogger(BooleanCompleter.class);

    private static final List<String> VALUES = Arrays.asList("true", "false");

    @Override
    public List<String> getAllowedValues(Session session, CommandLine commandLine) {
        return VALUES;
    }

}
