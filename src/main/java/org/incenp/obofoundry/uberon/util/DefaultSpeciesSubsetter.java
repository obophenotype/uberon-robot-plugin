/*
 * Uberon ROBOT plugin
 * Copyright © 2024 Damien Goutte-Gattat
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of copyright holder nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.incenp.obofoundry.uberon.util;

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

/**
 * The “default” strategy to create a taxon-specific subset.
 * <p>
 * This strategy is the same as the one implemented by OWLTools’ <a href=
 * "https://github.com/owlcollab/owltools/blob/master/OWLTools-Core/src/main/java/owltools/mooncat/SpeciesSubsetterUtil.java">SpeciesSubsetterUtil</a>
 * class.
 * <p>
 * Basically, we assert <code>THE_ROOT(s) in_taxon some THE_TAXON</code>, and
 * exclude from the subset all classes that are unsatisfiable because of that
 * assertion.
 * <p>
 * This may cause some classes to be excluded because they are linked to an
 * unsatisfiable class, even though the link between the two classes does not
 * convey that one cannot exist in a given taxon if the other does not. For
 * example, if <code>C1 shares_ancestor_with some C2</code> and C2 is
 * unsatisfiable in the considered taxon, then C1 will be unsatisfiable as well,
 * even though it may very well be valid in that taxon. To avoid this, we
 * forcibly remove a handful of “cross-taxon” relationships from the ontology
 * before proceeding.
 */
public class DefaultSpeciesSubsetter implements ISpeciesSubsetStrategy {

    private static final String[] CROSS_TAXON_RELATIONS = new String[] { "http://purl.obolibrary.org/obo/RO_0002320",
            "http://purl.obolibrary.org/obo/RO_0002156", "http://purl.obolibrary.org/obo/RO_0002374",
            "http://purl.obolibrary.org/obo/RO_0002312", "http://purl.obolibrary.org/obo/RO_0002157",
            "http://purl.obolibrary.org/obo/RO_0002159", "http://purl.obolibrary.org/obo/RO_0002158" };

    @Override
    public Set<OWLClass> getSubset(OWLOntology ontology, OWLReasoner reasoner, Collection<IRI> roots, IRI taxon) {
        OWLOntologyManager mgr = ontology.getOWLOntologyManager();
        OWLDataFactory factory = mgr.getOWLDataFactory();
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
