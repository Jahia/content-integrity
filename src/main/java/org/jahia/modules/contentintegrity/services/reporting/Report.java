package org.jahia.modules.contentintegrity.services.reporting;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class Report {

    private static final List<String> DEFAULT_COLUMN_ITEMS = Collections.unmodifiableList(Arrays.asList("Check ID", "Fixed", "Error type", "Impact on XML import", "Workspace", "Node identifier", "Node path", "Site", "Node primary type", "Node mixins", "Locale", "Error message", "Extra information", "Specific extra information"));

    abstract public void write(OutputStream stream, List<ContentIntegrityError> errors) throws IOException;

    public final String getFileName(String signature) {
        return String.format("%s.%s", signature, getFileExtension());
    }

    abstract public String getFileExtension();

    abstract public String getFileContentType();

    protected final List<String> getColumns() {
        return DEFAULT_COLUMN_ITEMS;
    }

    public int getMaxNumberOfLines() {
        return Integer.MAX_VALUE;
    }

    protected static List<String> toTextElementsList(ContentIntegrityError error) {
        final List<String> list = new ArrayList<>();
        toTextElements(error, list::add);
        return list;
    }

    protected static void toTextElements(ContentIntegrityError error, Consumer<String> accumulator) {
        accumulator.accept(Objects.toString(error.getIntegrityCheckID()));
        accumulator.accept(Objects.toString(error.isFixed()));
        accumulator.accept(Objects.toString(error.getErrorType()));
        accumulator.accept(Optional.ofNullable(error.getErrorType()).map(ContentIntegrityErrorType::isBlockingImport).orElse(Boolean.FALSE).toString());
        accumulator.accept(error.getWorkspace());
        accumulator.accept(error.getUuid());
        accumulator.accept(error.getPath());
        accumulator.accept(error.getSite());
        accumulator.accept(error.getPrimaryType());
        accumulator.accept(error.getMixins());
        accumulator.accept(error.getLocale());
        accumulator.accept(error.getConstraintMessage());
        accumulator.accept(mapToString(error.getExtraInfos()));
        accumulator.accept(mapToString(error.getSpecificExtraInfos()));
    }

    private static String mapToString(Map map) {
        if (MapUtils.isEmpty(map)) return StringUtils.EMPTY;
        return Objects.toString(map);
    }
}
