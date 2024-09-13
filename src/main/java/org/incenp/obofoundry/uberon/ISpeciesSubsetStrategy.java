package org.incenp.obofoundry.uberon;

import java.util.Collection;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public interface ISpeciesSubsetStrategy {

    public static final IRI IN_TAXON = IRI.create("http://purl.obolibrary.org/obo/RO_0002162");

    public Set<OWLClass> getSubset(OWLOntology ontology, OWLReasoner reasoner, Collection<IRI> roots, IRI taxon);
}
