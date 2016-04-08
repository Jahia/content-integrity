package org.jahia.modules.verifyintegrity.exceptions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CompositeIntegrityViolationException {
	private List<Exception> errors = new ArrayList();

	public CompositeIntegrityViolationException() {
	}

	public void addException(Exception exception) {
		this.errors.add(exception);
	}

	public String getMessage() {
		StringBuilder sb = new StringBuilder(512);
		Iterator i$ = this.errors.iterator();

		while (i$.hasNext()) {
			Exception error = (Exception) i$.next();
			sb.append(error.getMessage()).append('\n');
		}

		return sb.toString();
	}

	public List<Exception> getErrors() {
		return this.errors;
	}
}
