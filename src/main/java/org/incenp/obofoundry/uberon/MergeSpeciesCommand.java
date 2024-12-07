/*
 * Uberon ROBOT plugin
 * Copyright © 2023,2024 Damien Goutte-Gattat
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.incenp.obofoundry.uberon.util.SpeciesMerger;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A command to fold species-specific classes to form a “composite” ontology.
 * <p>
 * This command is the critical component of Uberon’s “composite-metazoan”
 * pipeline. It replaces OWLTools’s <code>--merge-species-ontology</code>
 * command.
 */
public class MergeSpeciesCommand extends BasePlugin {

    private static final Logger logger = LoggerFactory.getLogger(MergeSpeciesCommand.class);

    public MergeSpeciesCommand() {
        super("merge-species", "create a composite cross-species ontology",
                "robot merge-species -i <FILE> -t TAXON [-s SUFFIX] -o <FILE>");
        options.addOption("b", "batch-file", true, "batch file describing the merges to perform");
        options.addOption("t", "taxon", true, "unfoled for specified taxon");
        options.addOption("p", "property", true, "unfold on specified property");
        options.addOption("s", "suffix", true, "suffix to append to class labels");
        options.addOption("q", "include-property", true, "object property to include");
        options.addOption("x", "extended-translation", false, "enable translation of more class expressions");
        options.addOption("g", "translate-gcas", false, "enable translation of affected general class axioms");
        options.addOption("G", "remove-gcas", false, "remove general class axioms affected by merge");
        options.addOption("d", "remove-declarations", false,
                "enable removal of declaration axioms for translated classes");
        options.addOption("r", "reasoner", true, "reasoner to use");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        List<MergeOperation> ops = new ArrayList<MergeOperation>();

        if ( line.hasOption('b') ) {
            parseBatchFile(line.getOptionValue('b'), ops);
        } else {
            MergeOperation op = new MergeOperation();

            if ( !line.hasOption('t') ) {
                throw new IllegalArgumentException("Missing --taxon argument");
            }
            op.taxonId = getIRI(line.getOptionValue("taxon"), "taxon");
            op.taxonLabel = line.getOptionValue("s", "species specific");

            if ( line.hasOption("property") ) {
                for ( String property : line.getOptionValues("property") ) {
                    op.linkProperties.add(getIRI(property, "property"));
                }
            } else {
                op.linkProperties.add(getIRI("BFO:0000050", "property"));
            }

            if ( line.hasOption("include-property") ) {
                for ( String property : line.getOptionValues("include-property") ) {
                    op.includedProperties.add(getIRI(property, "include-property"));
                }
            }
            ops.add(op);
        }

        OWLOntology ontology = state.getOntology();
        OWLReasoner reasoner = CommandLineHelper.getReasonerFactory(line).createReasoner(ontology);
        SpeciesMerger merger = new SpeciesMerger(ontology, reasoner);

        if ( line.hasOption('x') ) {
            merger.setExtendedTranslation(true);
        }
        if ( line.hasOption('g') ) {
            merger.setGCAMode(SpeciesMerger.GCAMergeMode.TRANSLATE);
        } else if ( line.hasOption('G') ) {
            merger.setGCAMode(SpeciesMerger.GCAMergeMode.DELETE);
        }
        if ( line.hasOption('d') ) {
            merger.setRemoveDeclarationAxiom(true);
        }

        for ( MergeOperation op : ops ) {
            for ( IRI property : op.linkProperties ) {
                logger.info("Unfolding for species %s over %s links", op.taxonId, property);
                merger.merge(op.taxonId, property, op.taxonLabel, op.includedProperties);
            }
        }

        reasoner.dispose();
    }

    private void parseBatchFile(String file, List<MergeOperation> operations) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;

        while ( (line = reader.readLine()) != null ) {
            if ( line.isEmpty() || line.startsWith("#") ) {
                continue;
            }
            String[] items = line.split("\t");

            MergeOperation op = new MergeOperation();
            op.taxonId = getIRI(items[0], "taxon");
            op.taxonLabel = items.length > 1 ? items[1] : "species specific";

            if ( items.length > 2 ) {
                for ( String p : items[2].split(",") ) {
                    op.linkProperties.add(getIRI(p, "property"));
                }
            } else {
                op.linkProperties.add(getIRI("BFO:0000050", "property"));
            }

            if ( items.length > 3 ) {
                for ( String p : items[3].split(",") ) {
                    op.includedProperties.add(getIRI(p, "include-property"));
                }
            }

            operations.add(op);
        }

        reader.close();
    }

    private class MergeOperation {
        IRI taxonId;
        String taxonLabel;
        ArrayList<IRI> linkProperties = new ArrayList<IRI>();
        ArrayList<IRI> includedProperties = new ArrayList<IRI>();
    }
}
