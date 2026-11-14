// Ambient module declaration for Payload's bundled stylesheet.
//
// Payload's generated admin files use `import '@payloadcms/next/css'`, a
// side-effect import that resolves (via the package `exports` map) to a `.css`
// file with no type declarations. TypeScript 6 reports this as TS2882. The
// specifier ends in `/css`, so Next's ambient `*.css` declaration does not match
// it - this declaration covers it explicitly.
declare module '@payloadcms/next/css'
