# Tokenize codec

This package implements a codec that compresses a graph by replacing its repeated
strings with tokens, rather than storing those strings over and over across every
vertex and edge that uses them.

A provenance graph tends to repeat the same small set of strings constantly - the same
annotation names appear on nearly every vertex and edge, and many of the same annotation
values recur as well. Instead of storing each occurrence of such a string directly, this
codec assigns every distinct string a token the first time it's seen, and every later
occurrence of that same string is replaced by a reference to that token. The string
itself is then stored only once, no matter how many times it appears across the graph.

## What gets tokenized

The unit of text that gets tokenized is called a phrase. A phrase is exactly one of:

- an id / a hash, whichever the exported identifier resolves to
- an annotation name
- an annotation value

Phrases are not subdivided any further - a phrase is tokenized as a whole string, not
broken down into words or smaller pieces. This keeps tokenization simple and keeps the
space savings tied to whole recurring strings, which is where the repetition in a
provenance graph actually occurs.
