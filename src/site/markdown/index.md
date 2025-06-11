Uberon ROBOT plugin
===================

This is a [ROBOT](http://robot.obolibrary.org/) plugin intended to
provide some extra commands to be used primarily in the
[Uberon](https://github.com/obophenotype/uberon) pipelines.

Available commands
------------------
Currently, the Uberon ROBOT plugin provides the following commands:

* [uberon:merge-species](merge-species.html), to create composite
  cross-taxa ontologies;
* [uberon:merge-equivalent-sets](merge-equivalent-sets.html), to merge
  terms that are considered equivalent across several ontologies;
* [uberon:create-species-subset](create-species-subset.html), to create
  a subset of an ontology containing only terms that are valid for a
  specific taxon.

Use with ROBOT
--------------
To use the plugin with your local installation of ROBOT, download the
plugin file `uberon.jar` and place it in ROBOT’s plugins directory (by
default `~/.robot/plugins`). You may then use any command provided by
the plugin as any other ROBOT builtin commands.

Use with the ODK
----------------
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
The Uberon ROBOT plugin is distributed under the terms of a 3-clause BSD
license. See the [COPYING file](https://github.com/obophenotype/uberon-robot-plugin/blob/main/COPYING)
in the source distribution.