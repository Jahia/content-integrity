package org.jahia.modules.contentintegrity.jcrcommands;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.Row;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.settings.SettingsBean;
import org.json.JSONException;
import org.json.JSONObject;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.UUID;

// TODO : this is a fork from org.jahia.bundles.jcrcommands , which doesn't export a required package
public class JCRCommandSupport {

    public static String WORKSPACE = "JcrCommand.WORKSPACE";
    public static String PATH = "JcrCommand.PATH";
    private static final int DEFAULT_LIMIT = 20;

    protected String getCurrentPath(Session session) {
        String path = (String) session.get(PATH);
        if (path == null) {
            path = "/";
            setCurrentPath(session, path);
        }
        return path;
    }

    protected void setCurrentPath(Session session, String path) {
        session.put(PATH, path);
    }

    protected String getCurrentWorkspace(Session session) {
        String workspace = (String) session.get(WORKSPACE);
        if (workspace == null) {
            workspace = "default";
            setCurrentWorkspace(session, workspace);
        }
        return workspace;
    }

    protected void setCurrentWorkspace(Session session, String workspace) {
        session.put(WORKSPACE, workspace);
    }

    protected JCRNodeWrapper getNode(JCRSessionWrapper jcrsession, String path, Session session) throws RepositoryException {
        if (path == null) {
            return jcrsession.getNode(getCurrentPath(session));
        } else if (path.startsWith("/")) {
            return jcrsession.getNode(path);
        } else if (path.equals("..")) {
            JCRNodeWrapper n = jcrsession.getNode(getCurrentPath(session));
            return n.getParent();
        } else {
            try {
                JCRNodeWrapper n = jcrsession.getNode(getCurrentPath(session));
                return n.getNode(path);
            } catch (PathNotFoundException e) {
                try {
                    // Try with UUID
                    UUID.fromString(path);
                    return jcrsession.getNodeByIdentifier(path);
                } catch (IllegalArgumentException e1) {
                    throw e;
                }
            }
        }
    }

    protected void printContentIntegrityErrors(ContentIntegrityResults results, String limitStr) throws JSONException {
        printContentIntegrityErrors(results, limitStr, true);
    }

    protected void printContentIntegrityErrors(ContentIntegrityResults results, String limitStr, boolean printFixedErrors) throws JSONException {
        if (results == null) {
            System.out.println("An error occured while testing the content integrity");
            return;
        }
        System.out.println(String.format("Content integrity tested in %s", results.getFormattedTestDuration()));
        final List<ContentIntegrityError> errors = results.getErrors();
        if (CollectionUtils.isEmpty(errors)) {
            System.out.println("No error found");
            return;
        }
        final ShellTable table = new ShellTable();
        table.column(new Col("ID"));
        table.column(new Col("Fixed"));
        table.column(new Col("Error"));
        table.column(new Col("Workspace"));
        //table.column(new Col("Path"));
        table.column(new Col("UUID"));
        table.column(new Col("Node type"));
        table.column(new Col("Locale"));
        table.column(new Col("Message"));

        int errorID = 0;
        int nbPrintedErrors = 0;
        final int limit = NumberUtils.toInt(limitStr, DEFAULT_LIMIT);
        for (ContentIntegrityError error : errors) {
            if (nbPrintedErrors >= limit) continue;
            final boolean fixed = error.isFixed();
            if (printFixedErrors || !fixed) {
                nbPrintedErrors++;
                final Row row = table.addRow();
                final JSONObject json = error.toJSON();
                row.addContent(errorID);
                row.addContent(fixed ? "X" : "");
                row.addContent(json.get("errorType"));
                row.addContent(json.get("workspace"));
                //row.addContent(json.get("path"));
                row.addContent(json.get("uuid"));
                row.addContent(json.get("nt"));
                row.addContent(json.get("locale"));
                row.addContent(json.get("message"));
            }
            errorID++;
        }
        table.print(System.out, true);
        final int errorsCount = errors.size();
        if (errorsCount > errorID)
            System.out.println(String.format("Printed the first %s errors. Total number of errors: %s", nbPrintedErrors, errorsCount));
    }

    protected static String getProperty(String name) {
        final String property = SettingsBean.getInstance().getPropertiesFile().getProperty(name);
        return property != null ? property : System.getProperty(name);
    }
}
