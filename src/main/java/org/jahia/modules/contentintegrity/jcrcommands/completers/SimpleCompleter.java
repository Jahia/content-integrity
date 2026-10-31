package org.jahia.modules.contentintegrity.jcrcommands.completers;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;

import java.util.Arrays;
import java.util.List;

public abstract class SimpleCompleter implements Completer {

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        final List<String> allowedValues = getAllowedValues(session, commandLine);
        if (CollectionUtils.isEmpty(allowedValues)) return -1;
        final StringsCompleter delegate = new StringsCompleter();
        final String argument = commandLine.getCursorArgument();
        if (StringUtils.isBlank(argument)) {
            candidates.addAll(allowedValues);
        } else {
            for (String value : allowedValues) {
                if (value.startsWith(argument)) delegate.getStrings().add(value);
            }
        }
        return delegate.complete(session, commandLine, candidates);
    }

    public abstract List<String> getAllowedValues(Session session, CommandLine commandLine);

    /**
     * Returns the value typed after an option of the command line, specified by its name or one of its aliases, or null if
     * the option is not typed, or is not followed by a value
     */
    protected static String getOptionValue(CommandLine commandLine, String... names) {
        final List<String> arguments = Arrays.asList(commandLine.getArguments());
        for (String name : names) {
            final int index = arguments.indexOf(name);
            if (index >= 0 && index + 1 < arguments.size()) return StringUtils.trim(arguments.get(index + 1));
        }
        return null;
    }
}
