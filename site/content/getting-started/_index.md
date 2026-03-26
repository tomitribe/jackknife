---
title: "Getting Started"
description: "Add Jackknife to your project and start exploring."
weight: 1
---

## Add to your CLAUDE.md

Drop this in `~/.claude/CLAUDE.md` so every Claude session knows how to use Jackknife:

```markdown
## Jackknife
- When you need to inspect, decompile, or find classes in jar dependencies,
  run `mvn jackknife:index` in the project. This generates `.jackknife/USAGE.md`
  with full instructions. Read that file — it has everything you need.
```

No skill to install, no plugin to configure. Claude reads the generated `USAGE.md`
and takes it from there.

## Use everywhere

Add Jackknife to `~/.m2/settings.xml` and it's available in every Maven project:

```xml
<settings>
  <pluginGroups>
    <pluginGroup>org.tomitribe.jackknife</pluginGroup>
  </pluginGroups>
</settings>
```

## Add to a specific project

Or add the plugin to a project's `pom.xml`:

```xml
<plugin>
  <groupId>org.tomitribe.jackknife</groupId>
  <artifactId>jackknife-maven-plugin</artifactId>
  <version>0.3</version>
</plugin>
```

## Index your dependencies

```bash
mvn jackknife:index
```

This creates `.jackknife/manifest/` with lightweight class listings for every
dependency jar. Sub-second for an entire classpath.

## Find a class

```bash
Grep "ObjectMapper" .jackknife/manifest/
```

## Read source code

```bash
mvn jackknife:decompile -Dclass=com.fasterxml.jackson.databind.ObjectMapper
```

## Instrument a method

```bash
mvn jackknife:instrument -Dclass=org.tomitribe.util.Join -Dmethod=join
mvn test
```

Debug output appears as JSON:

```
JACKKNIFE {"event":"call","time":"12.3us","class":"Join","method":"join","args":[", ",["x","y","z"]],"return":"x, y, z"}
```

## Clean up

```bash
mvn jackknife:clean
```

## Add .jackknife/ to .gitignore

```
.jackknife/
```

All Jackknife state is local. Nothing touches your source, your jars, or your repository.
