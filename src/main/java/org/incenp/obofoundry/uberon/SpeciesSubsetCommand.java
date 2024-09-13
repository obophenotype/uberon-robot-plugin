package org.incenp.obofoundry.uberon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class SpeciesSubsetCommand extends BasePlugin {

    private static final IRI IN_TAXON = IRI.create("http://purl.obolibrary.org/obo/RO_0002162");
    private static final IRI IN_SUBSET = IRI.create("http://www.geneontology.org/formats/oboInOwl#inSubset");
    private static final IRI SUBSET_PROPERTY = IRI
            .create("http://www.geneontology.org/formats/oboInOwl#SubsetProperty");

    public SpeciesSubsetCommand() {
        super("create-species-subset", "create a subset for a given taxon",
                "robot create-species-subset -i <FILE> -t TAXON -o <FILE>");
        options.addOption("t", "taxon", true, "the taxon to create a subset for");
        options.addOption("r", "reasoner", true, "reasoner to use");
        options.addOption(null, "subset-name", true, "IRI to use to tag in-subset classes");
        options.addOption(null, "only-tag-in", true, "Only tag classes in the specified prefixes");
        options.addOption(null, "write-tags-to", true, "Write in-subset tags to specified file");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        if ( !line.hasOption('t') ) {
            throw new IllegalArgumentException("Missing --taxon argument");
        }

        OWLOntology ontology = state.getOntology();
        OWLOntologyManager mgr = ontology.getOWLOntologyManager();

        IRI taxonID = getIRI(line.getOptionValue('t'), "taxon");
        OWLReasoner reasoner = CommandLineHelper.getReasonerFactory(line).createReasoner(ontology);

        Set<OWLClass> subset = getSubset(ontology, reasoner, taxonID);

        if (line.hasOption("subset-name")) {
            IRI subsetIRI = IRI.create(line.getOptionValue("subset-name"));
            ArrayList<String> prefixes = new ArrayList<String>();
            if ( line.hasOption("only-tag-in") ) {
                for ( String p : line.getOptionValues("only-tag-in") ) {
                    prefixes.add(getIRI(p, "only-tag-in").toString());
                }
            }
            Set<OWLAxiom> annotations = makeInSubsetAnnotations(ontology, subset, subsetIRI, prefixes);

            if ( line.hasOption("write-tags-to") ) {
                OWLOntology output = mgr.createOntology();
                mgr.addAxioms(output, annotations);
                getIOHelper().saveOntology(output, line.getOptionValue("write-tags-to"));
            } else {
                mgr.addAxioms(ontology, annotations);
            }
        }
    }

    private Set<OWLClass> getSubset(OWLOntology ontology, OWLReasoner reasoner, IRI taxon) {
        OWLOntologyManager mgr = ontology.getOWLOntologyManager();
        OWLDataFactory factory = mgr.getOWLDataFactory();

        OWLAxiom ax = factory.getOWLSubClassOfAxiom(factory.getOWLThing(),
                factory.getOWLObjectSomeValuesFrom(factory.getOWLObjectProperty(IN_TAXON), factory.getOWLClass(taxon)));
        mgr.addAxiom(ontology, ax);

        reasoner.flush();
        Set<OWLClass> allClasses = ontology.getClassesInSignature(Imports.INCLUDED);
        Set<OWLClass> unsats = reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom();

        allClasses.removeAll(unsats);

        mgr.removeAxiom(ontology, ax);

        return allClasses;
    }

    private Set<OWLAxiom> makeInSubsetAnnotations(OWLOntology ontology, Set<OWLClass> subset, IRI subsetIRI,
            Collection<String> prefixes) {
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLAnnotationProperty inSubset = factory.getOWLAnnotationProperty(IN_SUBSET);

        ArrayList<OWLAxiom> addAxioms = new ArrayList<OWLAxiom>();
        addAxioms.add(factory.getOWLSubAnnotationPropertyOfAxiom(factory.getOWLAnnotationProperty(subsetIRI),
                factory.getOWLAnnotationProperty(SUBSET_PROPERTY)));
        for ( OWLClass c : subset ) {
            String iri = c.getIRI().toString();
            boolean include = prefixes == null || prefixes.size() == 0;

            if ( !include && prefixes != null ) {
                for ( String prefix : prefixes ) {
                    if ( iri.startsWith(prefix) ) {
                        include = true;
                        continue;
                    }
                }
            }

            if ( include ) {
                addAxioms.add(factory.getOWLAnnotationAssertionAxiom(inSubset, c.getIRI(), subsetIRI));
            }
        }

        return new HashSet<OWLAxiom>(addAxioms);
    }
}
