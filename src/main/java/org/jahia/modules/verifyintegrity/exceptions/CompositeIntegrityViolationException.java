package org.jahia.modules.verifyintegrity.exceptions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CompositeIntegrityViolationException {
	private List<IntegrityViolationException> errors = new ArrayList();

	public CompositeIntegrityViolationException() {
	}

	public int size() {
		return errors.size();
	}

	public void addException(IntegrityViolationException exception) {
		this.errors.add(exception);
	}

	public void addExceptions(List<IntegrityViolationException> listOfExceptions) {
		this.errors.addAll(listOfExceptions);
	}

	public String getMessage() {
		StringBuilder sb = new StringBuilder(512);
		Iterator i$ = this.errors.iterator();

		sb.append("[");
		for (int i=0; i<size(); i++) {
			IntegrityViolationException error = errors.get(i);
			sb.append(error.toString());
			if (i != (size() - 1)) {
				sb.append(",");
			}
		}
		sb.append("]");

		return sb.toString();
	}

	public List<IntegrityViolationException> getErrors() {
		return this.errors;
	}
}
