package org.incenp.obofoundry.robot;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.incenp.obofoundry.helpers.EquivalenceSetMerger;
import org.obolibrary.obo2owl.Obo2OWLConstants;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

public class MergeEquivalentSetsCommand extends BasePlugin {

    public MergeEquivalentSetsCommand() {
        super("merge-equivalent-sets", "merge sets of equivalent classes",
                "robot merge-equivalent-sets [-s PREFIX,...] [-l PREFIX,...] [-c PREFIX,...] [-d PREFIX,...] [-P PREFIX,...]");

        options.addOption(Option.builder("s").longOpt("iri-priority").hasArgs().valueSeparator(',')
                .desc("Order of priority to determine the representative IRI").build());
        options.addOption(Option.builder("l").longOpt("label-priority").hasArgs().valueSeparator(',')
                .desc("Order of priority to determine which LABEL should be used post-merge").build());
        options.addOption(Option.builder("c").longOpt("comment-priority").hasArgs().valueSeparator(',')
                .desc("Order of priority to determine which COMMENT should be used post-merge").build());
        options.addOption(Option.builder("d").longOpt("definition-priority").hasArgs().valueSeparator(',')
                .desc("Order of priority to determine which DEFINITION should be used post-merge").build());
        options.addOption(Option.builder("p").longOpt("preserve").hasArgs().valueSeparator(',')
                .desc("Disallow merging classes with the specified prefixes").build());

        options.addOption("r", "reasoner", true, "reasoner to use");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        EquivalenceSetMerger merger = new EquivalenceSetMerger();
        OWLDataFactory factory = state.getOntology().getOWLOntologyManager().getOWLDataFactory();

        if ( line.hasOption("s") ) {
            setScores(null, line.getOptionValues('s'), merger);
        }

        if ( line.hasOption("l") ) {
            setScores(factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()), line.getOptionValues('l'),
                    merger);
        }

        if ( line.hasOption("c") ) {
            setScores(factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI()),
                    line.getOptionValues('c'), merger);
        }

        if ( line.hasOption("d") ) {
            setScores(factory.getOWLAnnotationProperty(Obo2OWLConstants.Obo2OWLVocabulary.IRI_IAO_0000115.getIRI()),
                    line.getOptionValues('d'), merger);
        }

        if ( line.hasOption('p') ) {
            for ( String prefix : line.getOptionValues('p') ) {
                merger.addPreservedPrefix(prefix);
            }
        }

        merger.merge(state.getOntology(),
                CommandLineHelper.getReasonerFactory(line).createReasoner(state.getOntology()));
    }

    private void setScores(OWLAnnotationProperty p, String[] prefixes, EquivalenceSetMerger merger) {
        int score = prefixes.length;
        for ( String prefix : prefixes ) {
            if ( p != null ) {
                merger.setPropertyPrefixScore(p, prefix, new Double(score--));
            } else {
                merger.setPrefixScore(prefix, new Double(score--));
            }
        }
    }
}
