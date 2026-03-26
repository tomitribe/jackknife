---
title: "Source Structure"
description: "How decompiled source files are organized."
weight: 3
---

## Directory layout

```
.jackknife/source/
└── com.fasterxml.jackson.core/
    └── jackson-databind-2.20.1/
        └── com/
            └── fasterxml/
                └── jackson/
                    └── databind/
                        ├── ObjectMapper.java
                        ├── JsonNode.java
                        └── ...
```

- **GroupId** as a flat directory name (dots preserved, not expanded to subdirectories)
- **Artifact-version** as a subdirectory
- **Package structure** preserved underneath, matching the original source layout

## Inner classes

Inner classes are decompiled into the same `.java` file as the outer class.
You won't see separate files for `Foo$Bar.class` — look in `Foo.java`.

## Vineflower settings

Jackknife uses Vineflower with modern Java support enabled:

- Generic signatures
- Enum support
- Pattern matching
- Switch expressions
- Lambda inlining
