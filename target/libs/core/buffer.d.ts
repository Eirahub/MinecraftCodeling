// pxt's Python converter (pxtpy) maps bytes/memoryview to a TS type named Buffer and fails on
// every statement if no such type exists. pxt-common-packages ships the real one.
// shortcut: empty declaration, replace with a real Buffer API if we ever expose binary data.
declare interface Buffer {
}
