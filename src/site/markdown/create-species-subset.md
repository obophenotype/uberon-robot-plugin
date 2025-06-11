Creating taxon-specific subsets
===============================

The `uberon:create-species-subset` command is intended to replace
OWLTools’s `--make-species-subset` command.

Given a NCBITaxon ID, the command will create a subset containing only
the classes that do not violate known taxon constraints in that taxon.

Usage
-----
Use the `-t`, `--taxon` option to specify the taxon for which to create
a subset.

By default, the subset is evaluated from the top of the ontology
(`owl:Thing`). Use the `--root` option to evaluate from a specific
class; all classes above the chosen root will not be included in the
subset. The option may be repeated to evaluate the subset from more than
one root class.

The default behaviour of the command is to remove all classes that are
found not to belong in the subset from the output ontology.
Alternatively, to merely _tag_ the classes as belonging to the subset
without actively removing them, use both the `--no-remove` option to
keep the classes in the ontology and the `--subset-name` option to
specify the subset IRI to tag the classes with.
