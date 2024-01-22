package org.incenp.obofoundry.uberon;

import java.util.HashSet;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class MetricsCommand extends BasePlugin {

    public MetricsCommand() {
        super("metrics", "compute metrics on the ontology", "metrics --input <file>");
        options.addOption("r", "reasoner", true, "reasoner to use");
        options.addOption("x", "include-imports", false, "include axioms from imported ontologies");
        options.addOption("n", "namespace", true, "Only consider classes in specified namespace");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology ontology = state.getOntology();

        Imports withImports = line.hasOption('x') ? Imports.INCLUDED : Imports.EXCLUDED;
        OWLReasoner reasoner = CommandLineHelper.getReasonerFactory(line).createReasoner(ontology);

        String namespace = line.hasOption('n') ? "http://purl.obolibrary.org/obo/" + line.getOptionValue('n') + "_"
                : null;

        int nClasses = 0;
        int nAllClassifications = 0;
        int nAssertedClassifications = 0;
        int nRelationships = 0;
        HashSet<OWLObjectProperty> usedProperties = new HashSet<OWLObjectProperty>();

        for ( OWLClass klass : ontology.getClassesInSignature(withImports) ) {
            if ( namespace == null || klass.getIRI().toString().startsWith(namespace) ) {
                nClasses += 1;

                nAllClassifications += reasoner.getSuperClasses(klass, true).getFlattened().size();

                for ( OWLAxiom axiom : ontology.getAxioms(klass, withImports) ) {
                    if ( axiom instanceof OWLSubClassOfAxiom ) {
                        OWLSubClassOfAxiom scoa = (OWLSubClassOfAxiom) axiom;
                        usedProperties.addAll(scoa.getObjectPropertiesInSignature());

                        if ( scoa.getSuperClass().isNamed() ) {
                            nAssertedClassifications += 1;
                        } else if ( scoa.getObjectPropertiesInSignature().size() > 0 ) {
                            nRelationships += 1;
                        }
                    } else if ( axiom instanceof OWLEquivalentClassesAxiom ) {
                        OWLEquivalentClassesAxiom eca = (OWLEquivalentClassesAxiom) axiom;
                        usedProperties.addAll(eca.getObjectPropertiesInSignature());
                    }
                }
            }
        }

        int nProperties = ontology.getObjectPropertiesInSignature(withImports).size();

        System.out.printf("Number of classes: %d\n", nClasses);
        System.out.printf("Number of classifications: %d\n", nAllClassifications);
        System.out.printf("Number of asserted classifications: %d\n", nAssertedClassifications);
        System.out.printf("Number of inferred classifications: %d\n", nAllClassifications - nAssertedClassifications);
        System.out.printf("Number of relationships: %d\n", nRelationships);
        System.out.printf("Number of declared properties: %d\n", nProperties);
        System.out.printf("Number of used properties: %d\n", usedProperties.size());
    }

}
