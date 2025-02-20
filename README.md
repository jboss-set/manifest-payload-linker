# Manifest Payload Linker

This tool scans through a payload of Jira component upgrade tickets, and tries to determine which upgrades are
covered by given Wildfly Channel manifest.

## Configuration

The tool expects `config/application.properties` file being present and containing necessary configuration. Check the
`config/application.properties.template` file for example. The configuration keys can also be given as system 
properties, or both approaches can be combined.

## Execution

```shell
java -jar path/to/manifest-payload-linker-*-executable.jar path/to/manifest.yaml
```

## Output

The output is a list of Jira tickets that has been identified as being covered by given manifest, as well as
incorporated issues. Output is written to standard out, as well as into a couple of files in current directory:

* issue-codes.txt - contains just issue keys,
* issue-links.txt - contains issue links,
* detailed-report.txt - to contain some extra info, currently just issue links and statuses.
