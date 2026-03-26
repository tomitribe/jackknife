---
title: "Getting Started"
description: "Add Jackknife to your Maven project and start exploring dependencies."
weight: 1
---

## Installation

Add the plugin to your `pom.xml`:

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

Read `.jackknife/USAGE.md` for the full reference — it's generated with
every index and has everything you need.

## Find a class

```bash
Grep "ObjectMapper" .jackknife/manifest/
```

## Read source code

```bash
mvn jackknife:decompile -Dclass=com.fasterxml.jackson.databind.ObjectMapper
```

The entire jar is decompiled. Every class is a `.java` file in `.jackknife/source/`.

## Instrument a method

```bash
mvn jackknife:instrument -Dclass=com.example.Foo -Dmethod=bar
mvn test
```

Debug output appears as JSON during your test run:

```
JACKKNIFE {"event":"call","time":"12.3us","class":"Foo","method":"bar","args":["hello"],"return":"world"}
```

## Clean up

```bash
mvn jackknife:clean
```
