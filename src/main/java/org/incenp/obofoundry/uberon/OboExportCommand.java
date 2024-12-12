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

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.obo2owl.OWLAPIOwl2Obo;
import org.obolibrary.oboformat.model.OBODoc;
import org.obolibrary.oboformat.writer.OBOFormatWriter;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * A command to produce a customised OBO file.
 * <p>
 * By default, this produces the same output as
 * {@code convert --format obo --check false}. Additional options allow to
 * enable some OBO-specific customisations:
 * <ul>
 * <li>stripping of axioms that cannot be represented in pure OBO
 * ({@code --strip-owl-axioms});
 * <li>stripping of GCI axioms ({@code --strip-gci-axioms});
 * <li>fixing the presence of more than one comment on a class by (1) discarding
 * all comments on the class ({@code --extra-comments discard-all}), (2)
 * discarding all but one comment ({@code --extra-comments keep-one}), or (3)
 * merging the comments into a single one ({@code --extra-comments merge}).
 * </ul>
 * <p>
 * Customised OBO output is sent to the file indicated by {@code --obo-output}.
 * The ontology with (optionally) stripped GCI axioms and merge comments is sent
 * to any following command, if any.
 */
public class OboExportCommand extends BasePlugin {

    public OboExportCommand() {
        super("obo-export", "export the ontology in OBO format",
                "robot obo-export -i <FILE> [options] --obo-output <FILE>");
        options.addOption(null, "obo-output", true, "write output to the specified OBO file");
        options.addOption(null, "merge-comments", false, "merge comments into a single comment per class");
        options.addOption(null, "extra-comments", true,
                "how to handle more than one comment on a class (discard-all, keep-one, merge)");
        options.addOption(null, "strip-owl-axioms", false, "strip untranslatable OWL axioms");
        options.addOption(null, "strip-gci-axioms", false, "strip GCI axioms");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology ont = state.getOntology();

        ExtraCommentAction commentAction = null;
        if ( line.hasOption("extra-comments") ) {
            commentAction = ExtraCommentAction.fromString(line.getOptionValue("extra-comments"));
            if ( commentAction == null ) {
                throw new Exception("Invalid argument for option --extra-comments");
            }
        } else if ( line.hasOption("merge-comments") ) {
            commentAction = ExtraCommentAction.MERGE;
        }

        if ( commentAction != null ) {
            handleComments(ont, commentAction);
        }

        if ( line.hasOption("strip-gci-axioms") ) {
            stripGCI(ont);
        }

        if ( line.hasOption("obo-output") ) {
            OWLAPIOwl2Obo oboConverter = new OWLAPIOwl2Obo(ont.getOWLOntologyManager());
            if ( line.hasOption("strip-owl-axioms") ) {
                oboConverter.setDiscardUntranslatable(true);
            }
            oboConverter.setStrictConversion(false);
            OBODoc oboDoc = oboConverter.convert(ont);
            OBOFormatWriter oboWriter = new OBOFormatWriter();
            oboWriter.setCheckStructure(false);
            oboWriter.write(oboDoc, line.getOptionValue("obo-output"));
        }
    }

    private void handleComments(OWLOntology ont, ExtraCommentAction action) {
        OWLOntologyManager mgr = ont.getOWLOntologyManager();
        OWLDataFactory fac = mgr.getOWLDataFactory();

        for (OWLClass klass : ont.getClassesInSignature(Imports.INCLUDED)) {
            Set<OWLAnnotationAssertionAxiom> commentAxioms = new HashSet<OWLAnnotationAssertionAxiom>();

            for (OWLAnnotationAssertionAxiom ax : ont.getAnnotationAssertionAxioms(klass.getIRI())) {
                if (ax.getProperty().isComment()) {
                    commentAxioms.add(ax);
                }
            }

            if (commentAxioms.size() > 1 ) {
                OWLAnnotationAssertionAxiom toAdd = null;
                if ( action == ExtraCommentAction.KEEP_ONE) {
                    for (OWLAnnotationAssertionAxiom ax : commentAxioms) {
                        if (toAdd == null ) {
                            toAdd = ax;
                        }
                    }
                } else if (action == ExtraCommentAction.MERGE) {
                    StringBuilder sb = new StringBuilder();
                    Set<OWLAnnotation> annotations = new HashSet<OWLAnnotation>();
                    for (OWLAnnotationAssertionAxiom ax : commentAxioms) {
                        String comment = null;
                        if ( ax.getValue().isLiteral()) {
                            comment = ax.getValue().asLiteral().get().getLiteral();
                        } else {
                            // Huh? Non-literal comment?
                            comment = ax.getValue().toString();
                        }
                        if (sb.length() > 0 ) {
                            sb.append(' ');
                        }
                        sb.append(comment);
                        annotations.addAll(ax.getAnnotations());
                    }

                    toAdd = fac.getOWLAnnotationAssertionAxiom(
                            fac.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI()), klass.getIRI(),
                            fac.getOWLLiteral(sb.toString()), annotations);
                }

                mgr.removeAxioms(ont, commentAxioms);
                if ( toAdd != null ) {
                    mgr.addAxiom(ont, toAdd);
                }
            }
        }
    }

    private void stripGCI(OWLOntology ont) {
        OWLOntologyManager mgr = ont.getOWLOntologyManager();
        Set<OWLAxiom> gciAxioms = new HashSet<OWLAxiom>();

        for ( OWLSubClassOfAxiom ax : ont.getAxioms(AxiomType.SUBCLASS_OF, Imports.INCLUDED) ) {
            if ( ax.getSubClass().isAnonymous() ) {
                gciAxioms.add(ax);
            }
        }

        if ( !gciAxioms.isEmpty() ) {
            mgr.removeAxioms(ont, gciAxioms);
        }
    }

    private enum ExtraCommentAction {
        DISCARD_ALL,
        KEEP_ONE,
        MERGE;

        static ExtraCommentAction fromString(String s) {
            if ( s.equalsIgnoreCase("discard-all") ) {
                return DISCARD_ALL;
            } else if ( s.equalsIgnoreCase("keep-one") ) {
                return KEEP_ONE;
            } else if ( s.equalsIgnoreCase("merge") ) {
                return MERGE;
            }
            return null;
        }
    };
}
