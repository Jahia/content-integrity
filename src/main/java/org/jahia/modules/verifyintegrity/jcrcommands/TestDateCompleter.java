package org.jahia.modules.verifyintegrity.jcrcommands;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.jahia.modules.verifyintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class TestDateCompleter implements Completer {

    private static final Logger logger = LoggerFactory.getLogger(TestDateCompleter.class);

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        final List<String> resultsDates = Utils.getContentIntegrityService().getTestResultsDates();
        if (CollectionUtils.isEmpty(resultsDates)) return -1;
        final StringsCompleter delegate = new StringsCompleter();
        final String argument = commandLine.getCursorArgument();
        if (StringUtils.isBlank(argument)) {
            candidates.addAll(resultsDates);
        } else {
            for (String date : resultsDates) {
                if (date.startsWith(argument)) delegate.getStrings().add(date);
            }
        }
        return delegate.complete(session, commandLine, candidates);
    }
}
