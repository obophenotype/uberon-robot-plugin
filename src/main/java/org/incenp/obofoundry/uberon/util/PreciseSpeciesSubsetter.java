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
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * A “precise” strategy to create a taxon-specific subset, as an alternative to
 * the strategy implemented in {@link DefaultSpeciesSubsetter}.
 * <p>
 * For each class C of the ontology below the root(s), we test whether the
 * expression <code>C and in_taxon some THE_TAXON</code> is satisfiable; if it
 * is, then the class is included in the subset.
 * <p>
 * On Uberon, this is about 4 to 5 times slower than the “default” strategy, but
 * has the advantage that it does not require removing the “cross-taxon”
 * relationships beforehand.
 * <p>
 * On the other hand, this may fail to exclude classes that are linked to a
 * class that is invalid for the considered taxon, if there is a missing
 * property chain over the in_taxon relation. For example, if we have
 * <code>C1 located_in some C2</code>, and C2 is not valid for the taxon, C1
 * will still be considered valid because there is (as of September 2024) no
 * property chain between in_taxon and located_in.
 */
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
            subset.add(factory.getOWLClass(root));
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
