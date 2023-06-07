package org.incenp.obofoundry.uberon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.obolibrary.obo2owl.OWLAPIOwl2Obo;
import org.obolibrary.obo2owl.Obo2OWLConstants.Obo2OWLVocabulary;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.OWLEntityRenamer;

/**
 * A helper class to merge classes that are inferred to be equivalent.
 * 
 * This is a port of Chris Mungall's <a href=
 * "https://github.com/owlcollab/owltools/blob/master/OWLTools-Core/src/main/java/owltools/mooncat/EquivalenceSetMergeUtil.java">implementation</a>
 * in OWLTools.
 */
public class EquivalenceSetMerger {
    private Map<String, Double> prefixScoreMap = new HashMap<String, Double>();
    private Map<OWLAnnotationProperty, Map<String, Double>> propertyPrefixScoreMap = new HashMap<OWLAnnotationProperty, Map<String, Double>>();
    private Set<String> noMergePrefixes = new HashSet<String>();
    private boolean addEquivalenceAxioms = true;

    /**
     * Set the priority score for a given prefix. When two (or more) classes are
     * merged, the merged class will use the IRI coming from the ontology associated
     * with the highest score.
     * 
     * @param prefix An ontology prefix.
     * @param score  The associated score. The higher the score, the more likely it
     *               is that IRIs with the specified prefix will be chosen.
     */
    public void setPrefixScore(String prefix, Double score) {
        prefixScoreMap.put(prefix, score);
    }

    /**
     * Set a property-specific score for a given prefix. When two (or more) classes
     * are merged, the merged class will keep the annotation property value that
     * came from the ontology associated with the highest score.
     * 
     * @param p      An annotation property.
     * @param prefix An ontology prefix.
     * @param score  The associated score.
     */
    public void setPropertyPrefixScore(OWLAnnotationProperty p, String prefix, Double score) {
        if ( !propertyPrefixScoreMap.containsKey(p)) {
            propertyPrefixScoreMap.put(p, new HashMap<String, Double>());
        }
        propertyPrefixScoreMap.get(p).put(prefix, score);
    }

    /**
     * Add a preserved prefix. Classes coming from an ontology with a preserved
     * prefix will not be merged.
     * 
     * @param prefix The prefix to preserve.
     */
    public void addPreservedPrefix(String prefix) {
        noMergePrefixes.add(prefix);
    }

    /**
     * Enable or disable the generation of cross-reference annotations. If this
     * option is enabled (it is by default), when two (or more) classes are merged,
     * the resulting merged class will be annotated with cross-reference axioms
     * (oboInOwl:hasDbXref) pointing to each of the original merged classes.
     * 
     * @param b {@code true} to enable the generation of cross-references axioms.
     */
    public void setAddEquivalenceAxioms(boolean b) {
        addEquivalenceAxioms = b;
    }

    /**
     * Merge inferred equivalent classes in an ontology.
     * 
     * @param ontology The ontology whose equivalent classes should be merged.
     * @param reasoner The reasoner to use to infer equivalences.
     * @throws ReasoningException If the ontology is inconsistent.
     */
    public void merge(OWLOntology ontology, OWLReasoner reasoner) throws ReasoningException {
        Set<Node<? extends OWLEntity>> nodes = new HashSet<Node<? extends OWLEntity>>();
        Map<OWLEntity, Node<? extends OWLEntity>> nodeByRep = new HashMap<OWLEntity, Node<? extends OWLEntity>>();
        Set<OWLClass> badClasses = new HashSet<OWLClass>();

        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLClass> unsats = reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom();
        if ( unsats.size() > 0 ) {
            throw new ReasoningException("Ontology contains unsatisfiable classes", unsats);
        }

        // Find sets of equivalent classes/individuals ("cliques")
        for ( OWLClass c : ontology.getClassesInSignature() ) {
            Node<OWLClass> n = reasoner.getEquivalentClasses(c);
            if ( n.getSize() > 1 ) {
                nodes.add(n);
                nodeByRep.put(c, n);
            }
        }
        for ( OWLNamedIndividual i : ontology.getIndividualsInSignature() ) {
            Node<OWLNamedIndividual> n = reasoner.getSameIndividuals(i);
            if ( n.getSize() > 1 ) {
                nodes.add(n);
                nodeByRep.put(i, n);
            }
        }

        OWLAnnotationProperty xrefProperty = factory
                .getOWLAnnotationProperty(Obo2OWLVocabulary.IRI_OIO_hasDbXref.getIRI());
        Map<OWLEntity, IRI> e2iri = new HashMap<OWLEntity, IRI>();
        Set<OWLEntity> seenClasses = new HashSet<OWLEntity>();
        Set<OWLAxiom> newAxiomsNoRewrite = new HashSet<OWLAxiom>();

        // Process each clique
        for ( Node<? extends OWLEntity> n : nodes ) {
            boolean isSeen = false;
            for ( OWLEntity c : n.getEntities() ) {
                if ( seenClasses.contains(c) ) {
                    isSeen = true;
                    break;
                }
                seenClasses.add(c);
            }
            if ( isSeen ) {
                continue;
            }

            // Find representative node ("clique leader") according to IRI priority scores
            OWLEntity cliqueLeader = null;
            Double best = null;
            for ( OWLEntity c : n.getEntities() ) {
                Double score = getScore(c, prefixScoreMap);
                if ( best == null || (score != null && score > best) ) {
                    cliqueLeader = c;
                    best = score;
                }
            }

            for ( OWLEntity c : n.getEntities() ) {
                if ( c.equals(cliqueLeader) ) {
                    continue;
                }

                // Replace node IRI with the representative IRI
                e2iri.put(c, cliqueLeader.getIRI());

                // Check we are not merging a node from a "preserved" prefix
                for ( String p : noMergePrefixes ) {
                    if ( hasPrefix(c, p) && hasPrefix(cliqueLeader, p) ) {
                        badClasses.add(c.asOWLClass());
                    }
                }

                // Add cross-reference to the clique leader
                if ( addEquivalenceAxioms ) {
                    OWLAxiom eca = null;
                    OWLAnnotationValue value = factory.getOWLLiteral(OWLAPIOwl2Obo.getIdentifier(c.getIRI()));
                    eca = factory.getOWLAnnotationAssertionAxiom(xrefProperty, cliqueLeader.getIRI(), value);
                    newAxiomsNoRewrite.add(eca);
                }
            }

            // For all properties for which we have set priorities, we remove the
            // corresponding annotation assertions unless they come from the ontology with
            // the highest priority.
            for ( OWLAnnotationProperty p : propertyPrefixScoreMap.keySet() ) {
                Map<String, Double> pmap = propertyPrefixScoreMap.get(p);

                // Find the representative node for this property. We do that on a per-clique
                // basis instead of once and for all because not all nodes in a clique may have
                // an annotation with the property, so the representative node for one clique
                // may come from a different ontology than the representative node for another
                // clique.
                OWLEntity representativeForProp = null;
                Double bestForProp = null;
                for ( OWLEntity c : n.getEntities() ) {
                    String v = getAnnotationValue(ontology, p, c);
                    if ( v == null || v.equals("") ) {
                        continue;
                    }
                    Double score = getScore(c, pmap);
                    if ( bestForProp == null || (score != null && score > bestForProp) ) {
                        representativeForProp = c;
                        bestForProp = score;
                    }
                }

                // Iterate again through the node, this time to remove the annotation assertions
                // except on the representative node.
                for ( OWLEntity c : n.getEntities() ) {
                    if ( c.equals(representativeForProp) ) {
                        continue;
                    }
                    Set<OWLAxiom> rmAxioms = new HashSet<OWLAxiom>();
                    for ( OWLAnnotationAssertionAxiom ax : ontology.getAnnotationAssertionAxioms(c.getIRI()) ) {
                        if ( ax.getProperty().equals(p) ) {
                            rmAxioms.add(ax);
                        }
                    }
                    ontology.getOWLOntologyManager().removeAxioms(ontology, rmAxioms);
                }
            }
        }

        if ( !badClasses.isEmpty() ) {
            throw new ReasoningException("Some preserved classes would be merged", badClasses);
        }

        // Replace the IRIs of merged nodes with their representative IRI.
        OWLEntityRenamer oer = new OWLEntityRenamer(ontology.getOWLOntologyManager(),
                ontology.getOWLOntologyManager().getOntologies());
        List<OWLOntologyChange> changes = oer.changeIRI(e2iri);
        ontology.getOWLOntologyManager().applyChanges(changes);

        // Add the cross-reference axioms
        ontology.getOWLOntologyManager().addAxioms(ontology, newAxiomsNoRewrite);

        // Cleaning up
        Set<OWLAxiom> rmAxioms = new HashSet<OWLAxiom>();
        for ( OWLSubClassOfAxiom a : ontology.getAxioms(AxiomType.SUBCLASS_OF) ) {
            // Removing reflexive assertions
            if ( a.getSubClass().equals(a.getSuperClass()) ) {
                rmAxioms.add(a);
            }
        }
        for ( OWLEquivalentClassesAxiom a : ontology.getAxioms(AxiomType.EQUIVALENT_CLASSES) ) {
            // Removing unary equivalent class expressions (A=A), which may happen as a
            // result of the merge
            if ( a.getClassExpressions().size() < 2 ) {
                rmAxioms.add(a);
            }
        }
        for ( OWLAnnotationAssertionAxiom a : ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION) ) {
            // Removing oboInOwl#id annotations.
            if ( a.getProperty().getIRI().equals(IRI.create("http://www.geneontology.org/formats/oboInOwl#id")) ) {
                rmAxioms.add(a);
            }
        }
        if ( rmAxioms.size() > 0 ) {
            System.err.printf("Removing %d axioms\n", rmAxioms.size());
            ontology.getOWLOntologyManager().removeAxioms(ontology, rmAxioms);
        }
    }

    private String getAnnotationValue(OWLOntology o, OWLAnnotationProperty p, OWLEntity e) {
        for ( OWLAnnotationAssertionAxiom ax : o.getAnnotationAssertionAxioms(e.getIRI()) ) {
            if ( ax.getProperty().equals(p) ) {
                return ax.getValue().asLiteral().toString();
            }
        }
        return null;
    }

    private Double getScore(OWLEntity c, Map<String, Double> pmap) {
        for ( String p : pmap.keySet() ) {
            if ( hasPrefix(c, p) ) {
                return pmap.get(p);
            }
        }

        return null;
    }

    private boolean hasPrefix(OWLEntity c, String p) {
        if ( p.startsWith("http") ) {
            return c.getIRI().toString().startsWith(p);
        }
        if ( c.getIRI().toString().contains("/" + p) ) {
            return true;
        }
        if ( c.getIRI().toString().contains("#" + p) ) {
            return true;
        }
        return false;
    }
}
