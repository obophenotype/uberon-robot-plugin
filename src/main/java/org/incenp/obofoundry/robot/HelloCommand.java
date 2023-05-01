package org.incenp.obofoundry.robot;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * A "hello world"-type example of a ROBOT pluggable command.
 * 
 * This command injects a "hello" message as an ontology annotation.
 */
public class HelloCommand extends BasePlugin {

    public HelloCommand() {
        super("hello", "insert a hello message into an ontology",
                "robot hello --input <FILE> [--target <TARGET>] --output <FILE>");
        options.addOption("r", "recipient", true, "recipient of the hello message");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) {
        OWLOntology ontology = state.getOntology();
        OWLDataFactory fac = ontology.getOWLOntologyManager().getOWLDataFactory();

        String recipient = line.getOptionValue('r', "world");

        OWLAnnotation annot = fac.getOWLAnnotation(fac.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI()),
                fac.getOWLLiteral(String.format("Hello, %s", recipient), ""));
        ontology.getOWLOntologyManager().applyChange(new AddOntologyAnnotation(ontology, annot));
    }
}
