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

import org.apache.commons.cli.CommandLine;
import org.incenp.obofoundry.uberon.util.SpeciesMerger;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.IRI;

/**
 * A command to fold species-specific classes to form a “composite” ontology.
 * <p>
 * This command is the critical component of Uberon’s “composite-metazoan”
 * pipeline. It replaces OWLTools’s <code>--merge-species-ontology</code>
 * command.
 */
public class MergeSpeciesCommand extends BasePlugin {

    public MergeSpeciesCommand() {
        super("merge-species", "create a composite cross-species ontology",
                "robot merge-species -i <FILE> -t TAXON [-s SUFFIX] -o <FILE>");
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
        if ( !line.hasOption('t') ) {
            throw new IllegalArgumentException("Missing --taxon argument");
        }

        IRI taxonIRI = getIRI(line.getOptionValue("taxon"), "taxon");
        IRI propertyIRI = getIRI(line.getOptionValue("property", "BFO:0000050"), "property");
        String suffix = line.getOptionValue("s", "species specific");

        SpeciesMerger merger = new SpeciesMerger(state.getOntology(), CommandLineHelper.getReasonerFactory(line),
                propertyIRI);

        if ( line.hasOption('x') ) {
            merger.setExtendedTranslation(true);
        }

        if ( line.hasOption('g') ) {
            merger.setGCAMode(SpeciesMerger.GCAMergeMode.TRANSLATE);
        } else if ( line.hasOption('G') ) {
            merger.setGCAMode(SpeciesMerger.GCAMergeMode.DELETE);
        }

        if ( line.hasOption('q') ) {
            for ( String item : line.getOptionValues("include-property") ) {
                merger.includeProperty(getIRI(item, "include-property"));
            }
        }

        if ( line.hasOption('d') ) {
            merger.setRemoveDeclarationAxiom(true);
        }

        merger.merge(taxonIRI, suffix);
    }
}
