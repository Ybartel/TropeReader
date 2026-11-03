# TropeReader

Personal Android reader for TvTropes, one trope at a time. See the spec: *TropeReader — Spécifications* (Nov 3, 2026).

Roadmap status: **step 2 (minimal reader) done**.

- `parser/`: pure Kotlin/JVM module that splits a page into cards (step 1).
- `app/`: Android app (Compose, Room, Hilt). For now it reads one hard-coded page (`VideoGame/Portal2`) card by card, saves progress in Room and resumes on reopen.

## App (step 2)

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- **Fetching:** `WebViewPageFetcher`, an off-screen WebView (the site rejects plain HTTP clients). Third-party requests (ads, trackers) are blocked; at most 1 request per second; User-Agent suffixed with `TropeReader/<version>`. Verified on an Android 17 emulator: the live page loads and splits into 795 cards.
- **Domain** (`app/.../domain`): `ReadingCursor` is the single reading position (F10.3); `AdvanceToNextCard` is the only way forward and marks the card read (F10.4); `MoveToCard` moves without marking; `SpeechEngine` is the interface for later read-aloud (F10.5).
- **Data:** Room schema v1, exported to `app/schemas/` (F11.2). User state uses deterministic ids (`domain.Ids`) with `updatedAt` / `deletedAt`, ready for a future sync. Pages are cached as raw HTML and re-parsed on open.
- **UI:** swipe or Previous/Next; position and folder in the header; Read/Unread toggle; spoilers hidden until the card is tapped.

## Run

Uses the JDK bundled with Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :parser:test
./gradlew :parser:run --args="parser/src/test/resources/pages/Series_BreakingBad.html"
```

`run` prints every card of a page: position, kind, stable key, folder, title, text preview.

## Findings from step 1 (2026-11-03)

- **Cloudflare blocks plain HTTP clients.** `curl`/OkHttp get `403` with `cf-mitigated: challenge` on every page, whatever the User-Agent. In a real browser the pages load normally, without any visible challenge.
  → The WebView is the **main** fetch path, not a fallback. Plan: a hidden WebView loads the page, and the raw HTML is read by a same-origin `fetch(location.href)` run inside it, so the parser always sees server HTML (not the DOM after scripts).
- **Big works are split into separate pages** (`BreakingBad/TropesAToB`, `…/TropesCToD`…), linked from a plain list on the main page. Others split with folders on one page (`Tropes A-C`…). Both cases are handled: `splitParts` for the former (F2.5), folders flattened for the latter (F3.4).
- **Inline notes** (`span.notelabel` + `span.inlinefolder`) exist next to spoilers; they are kept as `<span class="note">` and read in parentheses.
- The site embeds wiki markup in `data-format` attributes (base64). Not used, but a possible fallback if the HTML gets harder to parse.

## Parser

All site-specific selectors are in [`TvTropesPageParser`](parser/src/main/kotlin/app/tropereader/parser/TvTropesPageParser.kt), behind the [`PageParser`](parser/src/main/kotlin/app/tropereader/parser/PageParser.kt) interface.

| Spec | Behaviour |
| --- | --- |
| F3.1–F3.2 | One card per top-level entry; an entry with sub-bullets gives one `EXAMPLE` card per sub-bullet, with the parent's name and lead text as header. Deeper bullets stay inside their card. |
| F3.4 | Folders flattened; `folder` holds the label (`Outer / Inner` when nested). `section` holds the nearest heading. |
| F3.5 | Everything before the first `<hr>` is the `INTRO` card (episode summary on a Recap episode). |
| F3.6 | Keys: link path (`Main/AbortedArc`), else `n:` + normalized name, else `e:` + text hash. Sub-bullet: parent key + `#` + hash of its own text. Free text: `x:` + hash. Duplicates: `~2`… Normalization ignores case, accents, punctuation and spacing. |
| F3.7 | A Recap index exposes `recapEpisodes` in order (`RECAP_INDEX`). |
| F3.8–F3.9, F10.1–F10.2 | `bodyHtml` is sanitized (absolute links; only `spoiler`, `indent`, `note`, `quoteright`, `acaptionright` classes kept). `segments` is plain text with `isSpoiler`; images skipped, tables read row by row. |
| Risks | A page without `#main-article` (e.g. a Cloudflare challenge page) throws `PageParseException`, for the raw-display fallback. |

## Tests

- `TvTropesPageParserTest`: small hand-written pages in `src/test/resources/synthetic/`, mirroring markup observed on the live site.
- `RecordedPagesTest`: every real page in `src/test/resources/pages/` must parse with unique keys and match its dump in `src/test/resources/snapshots/`. Snapshots are created on first run; after an intended parser change, run `./gradlew :parser:test -DupdateSnapshots=true` and review the diff.

### Adding real pages

Save each page's **raw source** (not the rendered page):

1. Open `view-source:https://tvtropes.org/pmwiki/pmwiki.php/<Path>` in Chrome.
2. `Cmd+S`, save to `parser/src/test/resources/pages/<Namespace>_<Name>.html` (e.g. `Series_BreakingBad.html`).

Suggested set, one per tricky case:

| Page | Case |
| --- | --- |
| `Series/BreakingBad` | main page split into A-to-Z subpages |
| `BreakingBad/TropesAToB` | split part; spoilers; sub-bullets |
| `VideoGame/Portal2` | folders on one page |
| `Anime/NeonGenesisEvangelion` | anime, folders, `tt-markup` headings |
| `Characters/BreakingBadUniverseWalterWhite` | character page |
| `YMMV/BreakingBad` | YMMV, list and folders mixed |
| `Recap/BreakingBad` | Recap index |
| `Recap/BreakingBadS1E1Pilot` | Recap episode |
| `Main/ChekhovsGun` | general trope, examples by media |

Later, the app's "Export this page's HTML" (F11.4) produces the same kind of file.
