Assessing impact of logical changes
===================================

The `uberon:inference-diff` command is a specialised version of the
standard `diff` ROBOT command that specifically reports how inferred
_SubClassOf_ axioms differ between two versions of an ontology.

Usage
-----
Basic usage is as follows:

```sh
robot uberon:inference-diff -i <HEAD> -b <BASE> -d <OUTPUT>
```

where `<HEAD>` and `<BASE>` point to the two versions of the ontology to
compare, and `<OUTPUT>` is the name of the file where the report will be
written (if not specified, it defaults to `inference-diff.md`).

If a XML catalog is needed to resolve import declarations in the
`<HEAD>` file (respectively the `<BASE>` file), use the `--catalog`
(resp. `--base-catalog`) option.

The command will make use of a reasoner to check how inferred axioms
differ between the two versions of the ontology. As with any other ROBOT
command involving a reasoner, the reasoner to use can be specified with
the `-r` option; the default is ELK.

Report
------
The command will produce a report containing the following:

* The number of logical definitions (_EquivalentClasses_ axioms where
  at least one of the operands is a named class) that have been added /
  removed / modified between the `<BASE>` file and the `<HEAD>` file.

* For every class _C_ whose logical definition has changed (including
  the case where a class that did not have a logical definition now has
  one, and the other way round):

  * the number and list of all removed inferred subclasses (classes
    that are inferred to be subclasses of _C_ in `<BASE>` but not in
    `<HEAD>`);

  * the number and list of all added inferred subclasses (classes that
    are inferred to be subclasses of _C_ in `<HEAD>` but not in
    `<BASE>`).
    
To restrict the report to only the classes that belong to a given
namespace, add the `--base-iri` option (which may be used repeatedly).
For example, to report only on changes impacting classes in the Uberon
or CL namespaces:

```sh
robot uberon:inference-diff -i <HEAD> -b <BASE> --base-iri UBERON: --base-iri CL:
```
