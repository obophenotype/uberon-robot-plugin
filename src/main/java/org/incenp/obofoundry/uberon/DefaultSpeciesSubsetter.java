package org.incenp.obofoundry.uberon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class DefaultSpeciesSubsetter implements ISpeciesSubsetStrategy {

    private static final String[] CROSS_TAXON_RELATIONS = new String[] { "http://purl.obolibrary.org/obo/RO_0002320",
            "http://purl.obolibrary.org/obo/RO_0002156", "http://purl.obolibrary.org/obo/RO_0002374",
            "http://purl.obolibrary.org/obo/RO_0002312", "http://purl.obolibrary.org/obo/RO_0002157",
            "http://purl.obolibrary.org/obo/RO_0002159", "http://purl.obolibrary.org/obo/RO_0002158" };

    @Override
    public Set<OWLClass> getSubset(OWLOntology ontology, OWLReasoner reasoner, Collection<IRI> roots, IRI taxon) {
        OWLOntologyManager mgr = ontology.getOWLOntologyManager();
        OWLDataFactory factory = mgr.getOWLDataFactory();
        // Set<OWLClass> subset = ontology.getClassesInSignature(Imports.INCLUDED);
        HashSet<OWLClass> subset = new HashSet<OWLClass>();

        if ( roots == null ) {
            roots = new ArrayList<IRI>();
            roots.add(factory.getOWLThing().getIRI());
        }

        OWLClassExpression inTaxon = factory.getOWLObjectSomeValuesFrom(factory.getOWLObjectProperty(IN_TAXON),
                factory.getOWLClass(taxon));

        Set<OWLAxiom> crossTxAxioms = removeCrossTaxonRelationships(ontology, mgr);
        reasoner.flush();

        for ( IRI root : roots ) {
            Set<OWLClass> tmp = reasoner.getSubClasses(factory.getOWLClass(root), false).getFlattened();

            OWLAxiom ax = factory.getOWLSubClassOfAxiom(factory.getOWLClass(root), inTaxon);
            mgr.addAxiom(ontology, ax);
            reasoner.flush();

            tmp.removeAll(reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom());
            subset.addAll(tmp);

            mgr.removeAxiom(ontology, ax);
            reasoner.flush();
        }

        mgr.addAxioms(ontology, crossTxAxioms);

        return subset;
    }

    private Set<OWLAxiom> removeCrossTaxonRelationships(OWLOntology ontology, OWLOntologyManager mgr) {
        HashSet<OWLAxiom> axioms = new HashSet<OWLAxiom>();
        for (String r : CROSS_TAXON_RELATIONS) {
            OWLObjectProperty p = mgr.getOWLDataFactory().getOWLObjectProperty(IRI.create(r));
            for ( OWLAxiom ax : ontology.getAxioms(Imports.INCLUDED) ) {
                if ( ax.getObjectPropertiesInSignature().contains(p) ) {
                    axioms.add(ax);
                }
            }
        }

        mgr.removeAxioms(ontology, axioms);
        return axioms;
    }
}
