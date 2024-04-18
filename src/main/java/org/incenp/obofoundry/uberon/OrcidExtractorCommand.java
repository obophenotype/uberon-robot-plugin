package org.incenp.obofoundry.uberon;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;

public class OrcidExtractorCommand extends BasePlugin {

    private final static IRI CONTRIBUTOR = IRI.create("http://purl.org/dc/terms/contributor");

    public OrcidExtractorCommand() {
        super("extract-orcids", "extract ORCIDs referenced in ontology",
                "robot extract-orcids -i <INPUT> [--orcid-file <FILE>] [--orcid-module <FILE>]");

        options.addOption(null, "orcid-file", true, "Extract ORCIDs from this ontology");
        options.addOption(null, "orcid-module", true, "Save extracted ORCIDs to that file");
        options.addOption(null, "merge", false, "Merge the extracted ORCIDs into the current ontology");
        options.addOption(null, "property", true, "Extract ORCIDs referenced in this property");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology source = state.getOntology();
        OWLOntologyManager mgr = source.getOWLOntologyManager();
        OWLDataFactory factory = mgr.getOWLDataFactory();

        HashSet<IRI> properties = new HashSet<IRI>();
        if ( line.hasOption("property") ) {
            for ( String p : line.getOptionValues("property") ) {
                properties.add(getIRI(p, "property"));
            }
        }
        else {
            properties.add(CONTRIBUTOR);
        }

        HashSet<IRI> refs = new HashSet<IRI>();
        for ( OWLAnnotation annot : source.getAnnotations() ) {
            processAnnotation(annot, refs, properties);
        }
        for ( OWLAxiom ax : source.getAxioms(Imports.INCLUDED) ) {
            if ( ax instanceof OWLAnnotationAssertionAxiom ) {
                processAnnotation(((OWLAnnotationAssertionAxiom) ax).getAnnotation(), refs, properties);
            }
            for ( OWLAnnotation annot : ax.getAnnotations() ) {
                processAnnotation(annot, refs, properties);
            }
        }

        OWLOntology orcidOnt = null;
        if ( line.hasOption("orcid-file") ) {
            // Get the ORCID individuals from a separate file
            orcidOnt = getIOHelper().loadOntology(line.getOptionValue("orcid-file"));
        } else {
            // Get them from the current ontology (assuming ORCIDIO has already been merged
            // in at this point)
            orcidOnt = source;
        }

        HashSet<OWLAxiom> axioms = new HashSet<OWLAxiom>();
        for ( IRI orcid : refs ) {
            if ( orcidOnt.containsIndividualInSignature(orcid) ) {
                axioms.addAll(orcidOnt.getAxioms(factory.getOWLNamedIndividual(orcid), Imports.INCLUDED));
                axioms.addAll(orcidOnt.getAnnotationAssertionAxioms(orcid));
            }
        }

        if ( line.hasOption("orcid-module") ) {
            // Save the extracted ORCID individuals to a separate file
            OWLOntology module = mgr.createOntology();
            mgr.addAxioms(module, axioms);
            getIOHelper().saveOntology(module, line.getOptionValue("orcid-module"));
        }

        if ( line.hasOption("merge") ) {
            // Merge them into the current ontology; only makes sense with --orcid-file
            mgr.addAxioms(source, axioms);
        }
    }

    private void processAnnotation(OWLAnnotation annotation, Set<IRI> refs, Set<IRI> properties) {
        if ( properties.contains(annotation.getProperty().getIRI()) ) {
            if ( annotation.getValue().isIRI() ) {
                refs.add(annotation.getValue().asIRI().get());
            } else if ( annotation.getValue().isLiteral() ) {
                refs.add(IRI.create(annotation.getValue().asLiteral().get().getLiteral()));
            }
        }
    }
}
