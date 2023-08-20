package org.incenp.obofoundry.uberon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.obolibrary.robot.IOHelper;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.util.OWLObjectTransformer;

public class CurieExpanderCommand extends BasePlugin {

	public CurieExpanderCommand() {
        super("expand-curies", "expand CURIEs in annotations", "robot expand-curies -a <ANNOT>");
        options.addOption("a", "annotation", true, "expand CURIEs in annotations with the specified property");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology o = state.getOntology();
        OWLDataFactory factory = o.getOWLOntologyManager().getOWLDataFactory();

        Set<IRI> properties = new HashSet<IRI>();
        for ( String value : line.getOptionValues("annotation") ) {
            properties.add(getIRI(value, "annotation"));
        }

        if ( properties.size() == 0 ) {
            return;
        }

        IOHelper ioHelper = CommandLineHelper.getIOHelper(line);

        OWLObjectTransformer<OWLAnnotation> t = new OWLObjectTransformer<>((x) -> true, (input) -> {
            if ( properties.contains(input.getProperty().getIRI()) ) {
                if ( input.getValue().isLiteral() ) {
                    IRI iri = ioHelper.createIRI(input.getValue().asLiteral().get().getLiteral());
                    if ( iri != null ) {
                        return factory.getOWLAnnotation(input.getProperty(), iri);
                    }
                }
            }
            return input;
        }, factory, OWLAnnotation.class);

        List<OWLOntologyChange> changes = t.change(o);
        if ( changes.size() > 0 ) {
            o.getOWLOntologyManager().applyChanges(changes);
        }
    }
}
