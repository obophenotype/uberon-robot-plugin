package org.incenp.obofoundry.uberon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.OWLAxiomVisitorAdapter;

/**
 * This class may be used to create multi-species ontologies following the
 * <a href=
 * "https://github.com/obophenotype/uberon/wiki/Multi-species-composite-ontologies">"composite/merger"
 * strategy</a> described in Uberon's wiki.
 * 
 * It is heavily based on Chris Mungall's <a href=
 * "https://github.com/owlcollab/owltools/blob/master/OWLTools-Core/src/main/java/owltools/mooncat/SpeciesMergeUtil.java">implementation</a>
 * of that strategy in OWLTools.
 */
public class SpeciesMerger extends OWLAxiomVisitorAdapter {

    private OWLClass taxClass;
    private OWLObjectProperty linkProperty;
    private Set<OWLObjectProperty> includedProperties = null;
    private String suffix;

    private OWLOntology ontology;
    private OWLOntologyManager manager;
    private OWLDataFactory factory;

    private OWLReasonerFactory reasonerFactory;
    private OWLReasoner reasoner;

    private OWLClass txRootClass;
    private Set<OWLClass> txClasses;
    private Map<OWLClass, OWLClass> ecMap;
    private Map<OWLClass, OWLClassExpression> exMap;
    private OWLAxiom translatedAxiom;
    private OWLClass subject;

    private boolean translateObjectIntersectionOf = false;

    /**
     * Creates a new instance with BFO:0000050 as the linking property.
     * 
     * @param ontology        The ontology to operate on.
     * @param reasonerFactory The reasoner factory to use.
     */
    public SpeciesMerger(OWLOntology ontology, OWLReasonerFactory reasonerFactory) {
        this(ontology, reasonerFactory, IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"));
    }

    /**
     * Creates a new instance.
     * 
     * @param ontology        The ontology to operate on.
     * @param reasonerFactory The reasoner factory to use.
     * @param linkProperty    The object property used to link taxon-specific
     *                        classes to their taxon-neutral equivalent.
     */
    public SpeciesMerger(OWLOntology ontology, OWLReasonerFactory reasonerFactory, IRI linkProperty) {
        this.ontology = ontology;
        this.reasonerFactory = reasonerFactory;
        manager = ontology.getOWLOntologyManager();
        factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        this.linkProperty = factory.getOWLObjectProperty(linkProperty);
    }

    /**
     * Enable or disable the translation of ObjectIntersectionOf class expressions.
     * This is disabled by default for compatibility with Chris Mungall's original
     * implementation.
     * 
     * @param b {@code true} to enable translating ObjectIntersectionOf expressions.
     */
    public void setTranslateObjectIntersectionOf(boolean b) {
        translateObjectIntersectionOf = b;
    }

    /**
     * add the specified object property to the list of properties to include in the
     * merged ontology.
     * 
     * @param p The IRI of the object property to include.
     */
    public void includeProperty(IRI p) {
        if ( includedProperties == null ) {
            includedProperties = new HashSet<OWLObjectProperty>();
        }

        includedProperties.add(factory.getOWLObjectProperty(p));
    }

    /**
     * Unfold classes for the specified taxon.
     * 
     * @param taxon  The taxon to unfold for.
     * @param suffix The suffix to append to the label of unfolded subclasses.
     */
    public void merge(IRI taxon, String suffix) throws ReasoningException {
        reasoner = reasonerFactory.createReasoner(ontology);
        this.suffix = suffix;

        taxClass = factory.getOWLClass(taxon);
        listTaxonSpecificClasses();
        createMaps();

        for ( OWLClass c : txClasses ) {
            if ( c.isBottomEntity() ) {
                continue;
            }

            if ( !reasoner.isSatisfiable(c) ) {
                throw new ReasoningException("Ontology contains unsatisfiable classes",
                        reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom());
            }

            Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
            axioms.addAll(ontology.getAxioms(c, Imports.EXCLUDED));
            axioms.addAll(ontology.getAnnotationAssertionAxioms(c.getIRI()));
            for ( OWLClass p : reasoner.getSuperClasses(c, true).getFlattened() ) {
                axioms.add(factory.getOWLSubClassOfAxiom(c, p));
            }

            Set<OWLAxiom> newAxioms = new HashSet<OWLAxiom>();
            subject = c;
            for ( OWLAxiom axiom : axioms ) {
                translatedAxiom = null;
                axiom.accept(this);
                
                if ( translatedAxiom != null && ecMap.containsKey(c) ) {
                    for ( OWLClass sc : translatedAxiom.getClassesInSignature() ) {
                        if (isSkippable(sc)) {
                            translatedAxiom = null;
                            break;
                        }
                    }
                }
                
                if ( translatedAxiom != null && !translatedAxiom.getClassesInSignature().contains(txRootClass) ) {
                    newAxioms.add(translatedAxiom);
                }
            }

            manager.removeAxioms(ontology, axioms);
            manager.addAxioms(ontology, newAxioms);
        }
    }

    /*
     * Prepare a flat list of all the taxon-specific classes (all inferred
     * subclasses of "<property> some <taxon>").
     */
    private void listTaxonSpecificClasses() {
        txRootClass = factory.getOWLClass(IRI.create(taxClass.getIRI().toString() + "-part"));
        OWLEquivalentClassesAxiom qax = factory.getOWLEquivalentClassesAxiom(txRootClass,
                factory.getOWLObjectSomeValuesFrom(linkProperty, taxClass));
        manager.addAxiom(ontology, qax);
        reasoner.flush();
        txClasses = reasoner.getSubClasses(txRootClass, false).getFlattened();
        manager.removeAxiom(ontology, qax);
        manager.removeAxiom(ontology, factory.getOWLDeclarationAxiom(txRootClass));
    }

    /*
     * Iterate over all the EquivalentClasses axioms of the form
     * "C equivalentTo N and (P some T)" and create two maps that associate the
     * taxon-specific class C to:
     * 
     * - the taxon-neutral class N (ecMap);
     * 
     * - the entire class expression "N and (P some T)" (exMap).
     */
    private void createMaps() {
        ecMap = new HashMap<OWLClass, OWLClass>();
        exMap = new HashMap<OWLClass, OWLClassExpression>();

        for ( OWLEquivalentClassesAxiom eca : ontology.getAxioms(AxiomType.EQUIVALENT_CLASSES, Imports.INCLUDED) ) {
            // Only get the axioms involving both the link property P and the taxon T.
            if ( !eca.getClassesInSignature().contains(taxClass)
                    || !eca.getObjectPropertiesInSignature().contains(linkProperty) ) {
                continue;
            }

            for ( OWLClass c : eca.getClassesInSignature() ) {
                if ( !txClasses.contains(c) ) {
                    continue;
                }

                // At this point c is the taxon-specific class. Now we get the expression it is
                // equivalent to.
                for ( OWLClassExpression x : eca.getClassExpressionsMinus(c) ) {
                    if ( x instanceof OWLObjectIntersectionOf ) {
                        OWLObjectIntersectionOf oio = (OWLObjectIntersectionOf) x;
                        for ( OWLClassExpression n : oio.getOperands() ) {
                            if ( n instanceof OWLClass ) {
                                ecMap.put(c, (OWLClass) n); /* C -> N */
                                exMap.put(c, x); /* C -> N and (P some T) */
                            }
                        }
                    }
                }
            }
        }
    }

    private OWLClassExpression translateExpression(OWLClassExpression x, boolean mustBeEquiv) {
        if ( !x.isAnonymous() ) {
            if ( mustBeEquiv ) {
                return exMap.getOrDefault(x, x);
            } else {
                return ecMap.getOrDefault(x, (OWLClass) x);
            }
        } else {
            if ( x instanceof OWLObjectSomeValuesFrom ) {
                OWLObjectSomeValuesFrom svf = (OWLObjectSomeValuesFrom) x;
                OWLClassExpression filler = translateExpression(svf.getFiller(), mustBeEquiv);
                if ( filler != null ) {
                    return factory.getOWLObjectSomeValuesFrom(svf.getProperty(), filler);
                }
            } else if ( x instanceof OWLObjectIntersectionOf && translateObjectIntersectionOf ) {
                OWLObjectIntersectionOf oio = (OWLObjectIntersectionOf) x;
                Set<OWLClassExpression> operands = new HashSet<OWLClassExpression>();
                for ( OWLClassExpression operand : oio.getOperands() ) {
                    OWLClassExpression translatedOperand = translateExpression(operand, mustBeEquiv);
                    if ( translatedOperand != null ) {
                        operands.add(translatedOperand);
                    }
                }
                if ( operands.size() == oio.getOperands().size() ) {
                    return factory.getOWLObjectIntersectionOf(operands);
                }
            }
        }
        return null;
    }

    private boolean isSkippable(OWLClass c) {
        for ( OWLAnnotationAssertionAxiom ax : ontology.getAnnotationAssertionAxioms(c.getIRI()) ) {
            if ( !ax.getProperty().getIRI().toString().endsWith("inSubset") ) {
                continue;
            }

            OWLAnnotationValue v = ax.getValue();
            if ( v.isIRI() ) {
                String val = v.asIRI().toString();
                if ( val.contains("upper_level") || val.contains("non_informative")
                        || val.contains("early_development") ) {
                    return true;
                }
            }
        }

        return false;
    }


    @Override
    public void visit(OWLEquivalentClassesAxiom axiom) {
        // Equivalent classes axioms are translated by translating their component class
        // expressions.
        Set<OWLClassExpression> xs = new HashSet<OWLClassExpression>();
        for ( OWLClassExpression x : axiom.getClassExpressions() ) {
            OWLClassExpression tx = translateExpression(x, true);
            if ( tx == null ) {
                // If one class expression cannot be translated, the entire
                // equivalent axiom cannot be translated.
                return;
            }
            xs.add(tx);
        }

        translatedAxiom = factory.getOWLEquivalentClassesAxiom(xs);
    }

    @Override
    public void visit(OWLAnnotationAssertionAxiom axiom) {
        if ( ecMap.containsKey(subject) ) {
            // No translation needed for the unfolded classes.
            return;
        }

        if ( axiom.getProperty().isLabel() ) {
            // Translate the label by appending the taxon-specific suffix.
            OWLLiteral lit = axiom.getValue().asLiteral().get();
            String newLabel = lit.getLiteral() + " (" + suffix + ")";
            translatedAxiom = factory.getOWLAnnotationAssertionAxiom(axiom.getProperty(), axiom.getSubject(),
                    factory.getOWLLiteral(newLabel));
        } else {
            // Use other annotations as they are.
            translatedAxiom = axiom;
        }
    }

    @Override
    public void visit(OWLSubClassOfAxiom axiom) {
        OWLClassExpression trSub = translateExpression(axiom.getSubClass(), true);
        OWLClassExpression trSuper = translateExpression(axiom.getSuperClass(), false);

        // Both sides of the axiom need to be translatable.
        if ( trSub == null || trSuper == null ) {
            return;
        }

        // Avoid circular references.
        if ( trSub.getClassesInSignature().contains(trSuper) ) {
            return;
        }

        // No need for this SubClassOf axiom if the taxon-neutral class is already a
        // subclass of the translated superclass.
        if ( !trSub.equals(axiom.getSubClass()) ) {
            if ( reasoner.getSuperClasses(ecMap.get(axiom.getSubClass()), false).getFlattened().contains(trSuper) ) {
                return;
            }

            Set<OWLClass> ancs = new HashSet<OWLClass>();
            ancs.addAll(reasoner.getSuperClasses(ecMap.get(axiom.getSubClass()), false).getFlattened());
            ancs.addAll(reasoner.getEquivalentClasses(ecMap.get(axiom.getSubClass())).getEntities());
            for ( OWLClass p : ancs ) {
                for ( OWLSubClassOfAxiom sca : ontology.getSubClassAxiomsForSubClass(p) ) {
                    if ( sca.getSuperClass().equals(trSuper) ) {
                        return;
                    }
                }
            }

            if ( includedProperties != null && includedProperties.size() > 0 ) {
                boolean ok = false;
                for ( OWLObjectProperty p : axiom.getObjectPropertiesInSignature() ) {
                    if ( includedProperties.contains(p) ) {
                        ok = true;
                        break;
                    }
                }
                if ( !ok ) {
                    return;
                }
            }
        }

        translatedAxiom = factory.getOWLSubClassOfAxiom(trSub, trSuper);
    }
}
