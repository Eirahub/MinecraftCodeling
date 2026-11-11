# Bundled translations

Passed to `pxt staticpkg --locs-src locales` so the editor needs no network for its UI strings.

- `nb/strings.json`, `nb/webstrings.json`: pxt core editor strings, fetched once on 2026-11-09 from
  `https://makecode.com/api/translations?lang=nb&filename=<file>&approved=true`.
  Licence for reuse in a non-Microsoft target is not verified yet (architecture.md section 11).
  shortcut: snapshot checked in by hand, replace with a build step plus licence check before release.
- `nb/codeling/*.json`: our own block and target strings.
