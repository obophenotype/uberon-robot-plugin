Uberon ROBOT plugin
===================

This project is intended to provide some extra
[ROBOT](http://robot.obolibrary.org/) commands to be used in portions of
the [Uberon](https://github.com/obophenotype/uberon) pipeline.

Mostly, the goal is to be able to replace the use of
[OWLTools](https://github.com/owlcollab/owltools) by ROBOT commands
only, so that building Uberon no longer requires constantly switching
between ROBOT and OWLTools.

Provided commands
-----------------

### merge-species
This command provides the same feature as OWLTools’
`--merge-species-ontology` command, used in Uberon to build the
“composite” cross-species products (such as `composite-metazoan`).

The syntax is similar to that of the original OWLTools command:

```sh
robot merge-species -i <FILE> -t <TAXON> -s <SUFFIX> -o <FILE>
```

except that the `-q`, `--include-property` option must be repeated for
each property to include (that is, use `-q RO:0002202 -q RO:0002496`
instead of `-q RO:0002202 RO:0002496`).

The command offers additional features that were not available in the
original OWLTools command. They are all disabled by default, use the
following options to enable them:

* `-d`: delete class declaration axioms for classes that have been
  merged;
* `-x`: translate more class types of class expression than just
  _ObjectSomeValuesFrom_ (_ObjectIntersectionOf_, _ObjectUnionOf_,
  _ObjectComplementOf_, and _Object(Exact|Min|Max)Cardinality_
  expressions are supported);
* `-g`: translate general class axioms that refer to a class that has
  been merged;
* `-G`: delete (instead of translating) general class axioms that refer
  to a class that has been merged.


### merge-equivalent-sets
This command provides the same feature as OWLTools’
`--merge-equivalence-sets` command, also used in Uberon to build the
composite ontologies.

The command accepts the same options as the original OWLTools command.
However, scores assigned to each prefix should be specified as
`prefix=score` pairs. That is, use `-s UBERON=10 -s CL=9` instead of `-s
UBERON 10 -s CL 9`.

### create-species-subset
This command is intended to replace OWLTools’ `--make-species-subset`
command. Given a a NCBITaxon ID (specified with option `-t`, `--taxon`),
the command will create a subset containing only the classes that do not
violate known taxon constraints in that taxon.

By default, the subset is evaluated from the top of the ontology
(`owl:Thing`). Use the `--root` option to evaluate from a specific
class; all classes above the root will not be included in the subset.
The option may be repeated to evaluate the subset from more than one
root class.

The default behavior of the command is to remove all classes that are
found not to belong in the subset from the output ontology.
Alternatively, to merely tag the classes as belonging to the subset
without actively remove them, use both the `--no-remove` option to keep
the classes in the ontology and the `--subset-name` option to specify
the subset IRI to tag the classes with.

Building and using
------------------
Build with Maven by running:

```sh
mvn clean package
```

This will produce two Jar files in the `target` directory.

The `uberon.jar` file is the plugin itself, to be used with a version of
ROBOT that supports plugins. If you have such a version, place this file
in your plugins directory (by default `~/.robot/plugins`), then call the
commands by prefixing them with the basename of the Jar file in the
plugins directory.

For example, if you placed the plugin at `~/.robot/plugins/uberon.jar`,
you may call the `merge-species` command as follows:

```sh
robot uberon:merge-species ...
```

The `uberon-robot-standalone-X.Y.Z.jar` file is a standalone version of
ROBOT that includes the commands provided by this plugin as is they were
built-in commands. It is intended to be used until a plugin-enabled
version of ROBOT becomes available.

Using with the ODK
------------------
To use the plugin in an ODK-managed ontology, add the following to your
ODK configuration file (`src/ontology/myontology-odk.yaml`):

```yaml
robot_plugins:
  plugins:
    - name: uberon
      mirror_from: https://github.com/obophenotype/uberon-robot-plugin/releases/download/uberon-robot-plugin-X.Y.Z/uberon.jar
```

where `X.Y.Z` is the version number of the Uberon ROBOT plugin.

Then, whenever you need the plugin in one of your custom Makefile rules,
make the rule depend on the `all_robot_plugins` target, and invoke the
command you need as part of a ROBOT pipeline by prefixing its name with
`uberon:`. For example:

```make
target.owl: source1.owl source2.owl | all_robot_plugins
	$(ROBOT) merge -i source1.owl -o source2.owl \
	         uberon:merge-species -t NCBITaxon:7227 -s 'D melanogaster' -o $@
```

Copying
-------
Since the Uberon ROBOT plugin is, at least for now, made of commands
that are merely a port of the original OWLTools versions, the plugin is
distributed under the same terms as OWLTools (3-clause BSD license). See
the [COPYING file](COPYING) in the source distribution.
