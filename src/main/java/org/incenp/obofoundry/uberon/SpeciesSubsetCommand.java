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

package org.incenp.obofoundry.uberon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.incenp.obofoundry.uberon.util.DefaultSpeciesSubsetter;
import org.incenp.obofoundry.uberon.util.ISpeciesSubsetStrategy;
import org.incenp.obofoundry.uberon.util.PreciseSpeciesSubsetter;
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
import org.semanticweb.owlapi.util.OWLEntityRemover;

/**
 * A command to create a taxon-specific subset of an ontology, that is a subset
 * containing only the classes that are valid for a given taxon.
 * <p>
 * This command is intended to replace OWLTools’
 * <code>--make-species-subset</code>. It provides two different strategies to
 * create the subset:
 * <ul>
 * <li>the strategy used by the original OWLTools command
 * (<code>default</code>), which basically consists in asserting
 * <code>owl:Thing in_taxon some THE_TAXON</code> and excluding all classes that
 * are unsatisfiable as a result of that assertion;</li>
 * <li>an alternative strategy (<code>precise</code>), which basically consists
 * in including each class for which the expression
 * <code>THE_CLASS and in_taxon some THE_TAXON</code> is satisfiable.
 */
public class SpeciesSubsetCommand extends BasePlugin {

    private static final IRI IN_SUBSET = IRI.create("http://www.geneontology.org/formats/oboInOwl#inSubset");
    private static final IRI SUBSET_PROPERTY = IRI
            .create("http://www.geneontology.org/formats/oboInOwl#SubsetProperty");

    public SpeciesSubsetCommand() {
        super("create-species-subset", "create a subset for a given taxon",
                "robot create-species-subset -i <FILE> -t TAXON -o <FILE>");
        options.addOption("t", "taxon", true, "the taxon to create a subset for");
        options.addOption("r", "reasoner", true, "reasoner to use");
        options.addOption(null, "strategy", true, "subsetting strategy to use (default|precise)");
        options.addOption(null, "root", true, "set the root(s) to start from (default: owl:Thing)");
        options.addOption(null, "subset-name", true, "IRI to use to tag in-subset classes");
        options.addOption(null, "only-tag-in", true, "only tag classes in the specified prefixes");
        options.addOption(null, "write-tags-to", true, "write in-subset tags to specified file");
        options.addOption(null, "remove", false, "remove all classes not in the subset from the output ontology");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        if ( !line.hasOption('t') ) {
            throw new IllegalArgumentException("Missing --taxon argument");
        }

        OWLOntology ontology = state.getOntology();
        OWLOntologyManager mgr = ontology.getOWLOntologyManager();
        OWLReasoner reasoner = CommandLineHelper.getReasonerFactory(line).createReasoner(ontology);

        IRI taxonID = getIRI(line.getOptionValue('t'), "taxon");

        ArrayList<IRI> roots = null;
        if ( line.hasOption("root") ) {
            roots = new ArrayList<IRI>();
            for ( String root : line.getOptionValues("root") ) {
                roots.add(getIRI(root, "root"));
            }
        }

        Set<OWLClass> subset = getStrategy(line).getSubset(ontology, reasoner, roots, taxonID);

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
                mgr.removeOntology(output);
            } else {
                mgr.addAxioms(ontology, annotations);
            }
        }

        if ( line.hasOption("remove") ) {
            Set<OWLClass> excluded = ontology.getClassesInSignature(Imports.INCLUDED);
            excluded.removeAll(subset);

            OWLEntityRemover remover = new OWLEntityRemover(mgr.getOntologies());
            for ( OWLClass c : excluded ) {
                if ( !c.isTopEntity() && !c.isBottomEntity() ) {
                    c.accept(remover);
                }
            }
            mgr.applyChanges(remover.getChanges());
        }
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

    private ISpeciesSubsetStrategy getStrategy(CommandLine line) {
        String strategy = line.getOptionValue("strategy", "default");
        if ( strategy.equals("precise") ) {
            return new PreciseSpeciesSubsetter();
        } else {
            return new DefaultSpeciesSubsetter();
        }
    }
}
