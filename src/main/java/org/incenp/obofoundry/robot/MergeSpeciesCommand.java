package org.incenp.obofoundry.robot;

import org.apache.commons.cli.CommandLine;
import org.incenp.obofoundry.helpers.CurieManager;
import org.incenp.obofoundry.helpers.SpeciesMerger;
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
        options.addOption("x", "translate-oio-expr", false, "enable translation of ObjectIntersectionOf expressions");
        options.addOption("r", "reasoner", true, "reasoner to use");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        if ( !line.hasOption('t') ) {
            throw new IllegalArgumentException("Missing --taxon argument");
        }

        IRI taxonIRI = CurieManager.getInstance().expand(line.getOptionValue('t'));
        IRI propertyIRI = CurieManager.getInstance().expand(line.getOptionValue("p", "BFO:0000050"));
        String suffix = line.getOptionValue("s", "species specific");

        SpeciesMerger merger = new SpeciesMerger(state.getOntology(), CommandLineHelper.getReasonerFactory(line),
                propertyIRI);

        if ( line.hasOption('x') ) {
            merger.setTranslateObjectIntersectionOf(true);
        }

        merger.merge(taxonIRI, suffix);
    }

}
