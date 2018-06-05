package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;

import java.util.Arrays;
import java.util.List;

@Service
public class OutputLevelCompleter implements Completer {

    static final List<String> LEVELS = Arrays.asList("simple", "full");

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        final StringsCompleter delegate = new StringsCompleter();
        final String argument = commandLine.getCursorArgument();
        if (StringUtils.isBlank(argument)) {
            candidates.addAll(LEVELS);
        } else {
            for (String date : LEVELS) {
                if (date.startsWith(argument)) delegate.getStrings().add(date);
            }
        }
        return delegate.complete(session, commandLine, candidates);
    }
}
