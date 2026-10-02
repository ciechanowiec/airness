# Media binary fixtures

`clip.mov` is the smallest thing that is a QuickTime movie: one ISO base media `ftyp` box whose
major brand is `qt  `. `tone.mp3` is an ID3v2.3 tag header followed by one MPEG-1 Layer III frame
header. Both are written by hand rather than taken from anywhere, so neither carries a licence.

Each one holds a carriage return inside a field its own format defines, and neither ends in a
newline. That is deliberate: those are the two things the text linter offers to repair, so a run
that passes over these files passes over them because the suffix is excluded and not because the
bytes happened to be agreeable. A fixture that already satisfied the text contract would prove
nothing about the exclusion.

Audio and video stand for the whole group added beside them. The suffix list is data; what the
installed consumer test proves is the mechanism that reads it.
