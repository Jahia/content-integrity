package org.jahia.modules.verifyintegrity.jcrcommands;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.Row;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityService;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

@Command(scope = "jcr", name = "checkIntegrity")
@Service
public class CheckIntegrityCommand extends JCRCommandSupport implements Action {

    @Reference
    Session session;

    @Override
    public Object execute() throws Exception {
        final String currentPath = StringUtils.defaultString(getCurrentPath(session), "/");
        final List<ContentIntegrityError> errors = ContentIntegrityService.getInstance().validateIntegrity(currentPath, getCurrentWorkspace(session));
        if (CollectionUtils.isNotEmpty(errors)) {
            final ShellTable table = new ShellTable();
            table.column(new Col("Error"));
            table.column(new Col("Workspace"));
            //table.column(new Col("Path"));
            table.column(new Col("UUID"));
            table.column(new Col("Node type"));
            table.column(new Col("Locale"));
            table.column(new Col("Message"));

            for (ContentIntegrityError error : errors) {
                final Row row = table.addRow();
                final JSONObject json = error.toJSON();
                final Iterator keys = json.keys();
                row.addContent(json.get("errorType"));
                row.addContent(json.get("workspace"));
                //row.addContent(json.get("path"));
                row.addContent(json.get("uuid"));
                row.addContent(json.get("nt"));
                row.addContent(json.get("locale"));
                row.addContent(json.get("message"));
            }
            table.print(System.out, true);
        }
        return null;
    }
}
