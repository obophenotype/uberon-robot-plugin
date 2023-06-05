package org.incenp.obofoundry.helpers;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;

public class ReasoningException extends Exception {

    private static final long serialVersionUID = -4867170036764099132L;

    public ReasoningException(String msg) {
        super(msg);
    }

    public ReasoningException(String msg, Set<OWLClass> classes) {
        super(formatMessage(msg, classes));
    }

    public static String formatMessage(String msg, Set<OWLClass> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append(msg);
        sb.append(':');
        for ( OWLClass c : classes ) {
            sb.append(' ');
            sb.append(c.getIRI().toString());
        }
        return sb.toString();
    }
}
