Creating composite cross-species ontologies
===========================================

The `uberon:merge-species` command is intended to replace OWLTools’s
`--merge-species-ontology` command. It is a core component of the
pipeline that creates Uberon’s “composite” cross-species products (such
as `composite-metazoan`).

Usage
-----
The syntax is similar to that of the original OWLTools command:

```sh
robot uberon:merge-species -i <FILE> -t <TAXON> -s <SUFFIX> -o <FILE>
```

except that the `-q`, `--include-property` option must be repeated for
each property to include (that is, use `-q RO:0002202 -q RO:0002496`)
instead of `-q RO:0002202 RO:0002496`).

The command offers additional features that were not available in the
original OWLTools command. They are all disabled by default, use the
following options to enable them:

* `-d`, `--remove-declarations`: delete class declarations for classes
  that have been merged;
* `-x`, `--extended-translation`: translate more types of class
  expression than just _ObjectSomeValuesFrom_;
* `-g`, `--translate-gcas`: translate general class axioms that refer to
  a class that has been merged;
* `-G`, `--remove-gcas`: delete (instead of translating) general class
  axioms that refer to a class that has been merged.
  
Batch mode
----------
For convenience, the command also supports a “batch mode”, enabled with
the `-b`, `--batch-file` option, to create a composite ontology
involving several species in a single step. The batch file should be a
TSV file where each line represents a species, and:

* the first column indicates the taxon ID (equivalent to the `-t`
  option);
* the second column indicates the suffix to append to term labels
  (equivalent to the `-s` option);
* the third column indicates the properties to unfold on
  (comma-separated; equivalent to the `-p` option);
* and the fourth column indicates the object properties to include
  (comma-separated; equivalent to the `-q` option).
  
All columns except the first are optional.

For example, if `batch.tsv` is a file containing the following:

```
NCBITaxon:9606	human	RO:0002162	RO:0002202,RO:0002496,RO:0002497
NCBITaxon:10090	mouse	RO:0002162	RO:0002202,RO:0002496,RO:0002497
```

then the following command:

```sh
robot uberon:merge-species -i source.owl --batch-file batch.tsv -o output.owl
```

is equivalent to:

```sh
robot uberon:merge-species -i source.owl \
                           -t NCBITaxon:9606 \
                           -s human \
                           -p RO:0002162 \
                           -q RO:0002202 -q RO:0002496 -q BFO:0000051 \
      uberon:merge-species -t NCBITaxon:10090 \
                           -s mouse \
                           -p RO:0002162 \
                           -q RO:0002202 -q RO:0002496 -q BFO:0000051 \
                           -o output.owl
```
