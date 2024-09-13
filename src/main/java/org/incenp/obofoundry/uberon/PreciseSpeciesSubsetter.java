package org.incenp.obofoundry.uberon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class PreciseSpeciesSubsetter implements ISpeciesSubsetStrategy {

    @Override
    public Set<OWLClass> getSubset(OWLOntology ontology, OWLReasoner reasoner, Collection<IRI> roots, IRI taxon) {
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        HashSet<OWLClass> subset = new HashSet<OWLClass>();
        HashSet<OWLClass> unsats = new HashSet<OWLClass>();

        if ( roots == null ) {
            roots = new ArrayList<IRI>();
            roots.add(factory.getOWLThing().getIRI());
        }

        OWLClassExpression exp = factory.getOWLObjectSomeValuesFrom(factory.getOWLObjectProperty(IN_TAXON),
                factory.getOWLClass(taxon));

        for ( IRI root : roots ) {
            for ( OWLClass c : reasoner.getSubClasses(factory.getOWLClass(root), false).getFlattened() ) {
                if ( !unsats.contains(c) ) {
                    if ( reasoner.isSatisfiable(factory.getOWLObjectIntersectionOf(c, exp)) ) {
                        subset.add(c);
                    } else {
                        unsats.addAll(reasoner.getSubClasses(c, false).getFlattened());
                    }
                }
            }
        }

        return subset;
    }

}
