Merging equivalent terms across ontologies
==========================================

The `uberon:merge-equivalent-sets` command is intended to replace
OWLTools’s `--merge-equivalence-sets` command. It is used in Uberon,
alongside the `uberon:merge-species` command, to build the “composite”
ontologies.

Usage
-----
The command accepts the same options as the original OWLTools command.
However, scores assigned to each prefix should be specified as
`prefix=score` pairs. That is, use `-s UBERON=10 -s CL=9` instead of
`-s UBERON 10 -s CL 9`.
