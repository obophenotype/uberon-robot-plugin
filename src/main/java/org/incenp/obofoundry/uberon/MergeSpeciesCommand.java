package org.incenp.obofoundry.uberon;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.IRI;

public class MergeSpeciesCommand extends BasePlugin {

    public MergeSpeciesCommand() {
        super("merge-species", "create a composite cross-species ontology",
                "robot merge-species -i <FILE> -t TAXON [-s SUFFIX] -o <FILE>");
        options.addOption("t", "taxon", true, "unfoled for specified taxon");
        options.addOption("p", "property", true, "unfold on specified property");
        options.addOption("s", "suffix", true, "suffix to append to class labels");
        options.addOption("q", "include-property", true, "object property to include");
        options.addOption("x", "translate-oio-expr", false, "enable translation of ObjectIntersectionOf expressions");
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
            merger.setTranslateObjectIntersectionOf(true);
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
