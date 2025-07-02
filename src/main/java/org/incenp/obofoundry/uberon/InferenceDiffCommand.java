/*
 * Uberon ROBOT plugin
 * Copyright © 2025 Damien Goutte-Gattat
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

import java.io.File;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.collections4.SetUtils;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.obolibrary.robot.OntologyHelper;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A command to produce a report on the consequences of logical changes between
 * two versions of the same ontology.
 */
public class InferenceDiffCommand extends BasePlugin {

    private static final Logger logger = LoggerFactory.getLogger(InferenceDiffCommand.class);

    private Set<String> basePrefixes = new HashSet<>();

    public InferenceDiffCommand() {
        super("inference-diff", "report inference differences between two ontologies",
                "robot inference-diff -i <HEAD> -b <BASE> -d <OUTPUT>");

        options.addOption("b", "base-file", true, "base ontology to compare against");
        options.addOption("B", "base-catalog", true, "catalog to use when loading the base ontology");
        options.addOption("r", "reasoner", true, "reasoner to use");
        options.addOption(null, "base-iri", true, "only check classes in the specified namespace(s)");
        options.addOption("d", "diff-output", true, "write report to the specified file");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        if ( !line.hasOption("base-file") ) {
            logger.warn("Missing --base-file, no check performed");
            return;
        }

        if ( line.hasOption("base-iri") ) {
            for ( String iri : line.getOptionValues("base-iri") ) {
                basePrefixes.add(getIRI(iri, "base-iri").toString());
            }
        }

        PrintStream out = new PrintStream(new File(line.getOptionValue("diff-output", "inference-diff.md")));

        OWLOntology baseOnt = getIOHelper().loadOntology(new File(line.getOptionValue("base-file")),
                new File(line.getOptionValue("base-catalog")));
        OWLOntology headOnt = state.getOntology();

        Set<OWLClass> impactedClasses = getImpactedClasses(baseOnt, headOnt);
        out.format("Number of classes with modified logical definitions: %d\n", impactedClasses.size());

        if ( !impactedClasses.isEmpty() ) {
            Function<OWLNamedObject, String> baseLabeller = OntologyHelper.getLabelFunction(baseOnt, true);
            Function<OWLNamedObject, String> headLabeller = OntologyHelper.getLabelFunction(headOnt, true);

            OWLReasonerFactory reasonerFactory = CommandLineHelper.getReasonerFactory(line);
            OWLReasoner headReasoner = reasonerFactory.createReasoner(headOnt);
            OWLReasoner baseReasoner = reasonerFactory.createReasoner(baseOnt);

            out.append('\n');
            for ( OWLClass klass : impactedClasses ) {
                out.format("## [%s](%s)\n", headLabeller.apply(klass), klass.getIRI());

                Set<OWLClass> baseSubClasses = baseReasoner.getSubClasses(klass, false).getFlattened();
                Set<OWLClass> headSubClasses = headReasoner.getSubClasses(klass, false).getFlattened();

                Set<OWLClass> removedSubClasses = SetUtils.difference(baseSubClasses, headSubClasses);
                Set<OWLClass> addedSubClasses = SetUtils.difference(headSubClasses, baseSubClasses);

                if ( removedSubClasses.isEmpty() && addedSubClasses.isEmpty() ) {
                    out.append("No changes in inferred subclasses.\n");
                } else {
                    if ( !removedSubClasses.isEmpty() ) {
                        out.format("Removed subclasses: %d\n", removedSubClasses.size());
                        for ( OWLClass subclass : removedSubClasses ) {
                            out.format("* [%s](%s)\n", baseLabeller.apply(subclass), subclass.getIRI());
                        }
                    }
                    if ( !addedSubClasses.isEmpty() ) {
                        if ( !removedSubClasses.isEmpty() ) {
                            out.append('\n');
                        }
                        out.format("Added subclasses: %d\n", addedSubClasses.size());
                        for ( OWLClass subclass : addedSubClasses ) {
                            out.format("* [%s](%s)\n", headLabeller.apply(subclass), subclass.getIRI());
                        }
                    }
                }
                out.append('\n');
            }
        }

        out.close();
    }

    private Set<OWLClass> getImpactedClasses(OWLOntology baseOnt, OWLOntology headOnt) {
        Set<OWLClass> klasses = new HashSet<>();
        Set<OWLAxiom> uniqueAxioms = SetUtils.disjunction(baseOnt.getAxioms(Imports.INCLUDED),
                headOnt.getAxioms(Imports.INCLUDED));

        logger.debug("Number of unique axioms on either side: {}", uniqueAxioms.size());

        for ( OWLAxiom axiom : uniqueAxioms ) {
            if ( axiom instanceof OWLEquivalentClassesAxiom ) {
                OWLEquivalentClassesAxiom eca = (OWLEquivalentClassesAxiom) axiom;
                for ( OWLClassExpression expr : eca.getClassExpressions() ) {
                    if ( expr instanceof OWLClass ) {
                        OWLClass klass = (OWLClass) expr;
                        if ( isInBase(klass) ) {
                            logger.debug("Impacted class: {}", klass.getIRI());
                            klasses.add(klass);
                        }
                    }
                }
            }
        }
        return klasses;
    }

    private boolean isInBase(OWLEntity entity) {
        if ( basePrefixes.isEmpty() ) {
            return true;
        }

        String iri = entity.getIRI().toString();
        for ( String base : basePrefixes ) {
            if ( iri.startsWith(base) ) {
                return true;
            }
        }
        return false;
    }
}
