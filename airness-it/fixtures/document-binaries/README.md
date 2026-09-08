# Document binary fixtures

`module.wasm` is the minimal valid WebAssembly module: its magic number and version 1 header.
`font.pfb` is `standard_fonts/FoxitSymbol.pfb`, and `map.bcmap` is `cmaps/Hankaku.bcmap`,
from the published `pdfjs-dist` 6.3.289 package. The font and character-map redistribution
notices are kept beside them. Binary bytes remain unchanged.

These files deliberately have no text line-ending contract. The installed consumer test
accepts and preserves them while still rejecting an ordinary text file without its final newline.
