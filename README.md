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

### merge-equivalent-sets
This command provides the same feature as OWLTools’
`--merge-equivalence-sets` command, also used in Uberon to build the
composite ontologies.

The command accepts the same options as the original OWLTools command.
However, scores assigned to each prefix should be specified as
`prefix=score` pairs. That is, use `-s UBERON=10 -s CL=9` instead of `-s
UBERON 10 -s CL 9`.

Building and using
------------------
Build with Maven by running:

```sh
mvn clean package
```

This will produce two Jar files in the `target` directory.

The `uberon-robot-plugin-X.Y.Z.jar` file is the plugin itself, to be
used with a version of ROBOT that supports plugins. If you have such a
version (which probably means you have compiled it yourself, as no such
version has been released yet), place this file in your plugins
directory (by default `~/.robot/plugins`), then call the commands by
prefixing them with the basename of the Jar file in the plugins
directory.

For example, if you placed the plugin at `~/.robot/plugins/uberon.jar`,
you may call the `merge-species` command as follows:

```sh
robot uberon:merge-species ...
```

The `uberon-robot-standalone-X.Y.Z.jar` file is a standalone version of
ROBOT that includes the commands provided by this plugin as is they were
built-in commands. It is intended to be used until a plugin-enabled
version of ROBOT becomes available.

Copying
-------
Since the Uberon ROBOT plugin is, at least for now, made of commands
that are merely a port of the original OWLTools versions, the plugin is
distributed under the same terms as OWLTools (3-clause BSD license). See
the [COPYING file](COPYING) in the source distribution.
