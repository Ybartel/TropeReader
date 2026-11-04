# TropeReader

Personal Android reader for TvTropes, one trope at a time. See the spec: *TropeReader — Spécifications* (Nov 3, 2026).

Roadmap status: **steps 1–4 done; step 5 (robustness) started with backups**: in-app links, per-spoiler reveal, list view, mark page read, settings and history done.

- `parser/`: pure Kotlin/JVM module that splits a page into cards (step 1).
- `app/`: Android app (Compose, Room, Hilt): Library → Work → Reader.

## Library and pages (step 3)

- **Add a work** (F1.1) by pasting its link, or with Android "Share" from the browser (any text containing a TvTropes link). A link to a subpage (YMMV, Characters…) adds its work; a link to a work already in the library opens it.
- **Library** (F1.3–F1.4): title, media type, progress, last read; sort by recent or title; remove with confirmation. Removal is a soft delete: adding the work again restores its progress. Card totals are only known for pages opened at least once, so progress reads "of opened pages" until then.
- **Work screen** (F2.1–F2.3): the work's pages with their state (not started / in progress x/y / done) and a Resume button (most recently read page).
- **Merged tropes** (F2.5): a work's tropes page is one logical page made of the main page plus its A-to-Z subpages, loaded one per second with progress shown. Breaking Bad: 6 parts, 1,402 cards.
- **Subpages** come from the site's subpage bar (Characters, YMMV, Trivia, Funny…), in its order.
- **Schema v2** adds `page_summary` (card count per page) through an auto-migration, tested by `MigrationTest` (`./gradlew :app:connectedDebugAndroidTest`, needs a device or emulator).

## Links and spoilers (step 4, part 1)

- **Links** (F7.1–F7.4): a TvTropes page link opens in the app as a scrolling list of cards, stacked over the reader; Back returns to the same card, not marked read. Its "Add to library" adds the work, or the general trope itself (read like a work, examples grouped by media folder). Links to other sites open in the phone's browser.
- **Spoilers** (F6.1): each spoiler is a solid block until tapped; revealed ones stay highlighted. Compose's HTML conversion ignores inline styles, so spoilers are marked with private-use characters before conversion and styled afterwards (`ui/common/CardHtml.kt`).
- The A-to-Z parts' intros (only a navigation bar) are dropped when merging.
- **List view** (F4.5): reader top bar toggles a list of all cards (folder headers, read checks, spoilers blanked in previews, current card highlighted); tapping one jumps to it without marking anything read.
- **Settings** (gear in the library): spoilers hidden until tapped or always shown (F6.2), text size 85–150% on top of the system font scale, theme (system / light / dark), version number (F11.1). Stored with DataStore: device preferences, not part of the synced user state.
- **History** (F8): every card shown in the reader is logged (the same card twice in a row counts once), with its name and work so the list needs no page reload. The history screen (clock icon in the library) groups entries by day; tapping one reopens the reader on that card; "Clear" soft-deletes all entries. Schema v3, with migration tests for 2→3 and 1→3. Pages opened from links are not logged yet.
- **Quotes, tables, images** (F3.8): the parser splits each card body into blocks (`HtmlBlocks`): text, quotes (side bar, italic, "— Author" right-aligned), tables (columns sized from their longest cell, scroll sideways), images (thumbnail with caption; tap for full screen, pinch to zoom). Images are behind the same Cloudflare check as pages, so Coil loads them with the WebView's cookies and exact User-Agent (`SiteHttp`). No real page tested so far has a table: table rendering is covered by the splitting tests only.
- **Mark page read / unread** (F4.6): reader menu, or each page's menu on the work screen, always with confirmation. Unread clears every read state of the page; the position is kept.

## Backup (step 5, part 1)

Settings → Backup. **Export** writes `tropereader-backup-<date>.json` wherever the Android file picker allows (Downloads, Drive…): works, pages, read states, positions and history, soft-deleted rows included. Page content is not exported (it is re-downloaded). **Import** merges a backup into the phone, record by record: the most recent `updatedAt` wins, ties keep the local copy, all in one transaction. Thanks to deterministic ids, the same card from two phones is the same record. This is the format the future Google Drive sync will exchange (`format: 1`; newer formats are refused with a message, unknown fields ignored).

Checked on the emulator: export, `pm clear`, import → library and progress back. Tests: format round trip and merge rule (unit), Room round trip between two databases and newest-wins (instrumented).

Not done yet: hiding / reordering pages (F2.4), images inside list items deeper than the card body, Recap index read as a group of episodes (F3.7). A Recap page currently reads as its plain index.

## Reader (step 2)

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
- **Inline notes** (`span.notelabel` + `span.inlinefolder`) exist next to spoilers; they are kept as `<span class="note">` with the parentheses written into the HTML, so screen and speech show the same text.
- **Hidden text:** some editor labels are hidden with `style="display:none"` (e.g. "invoked"); the parser removes them, since sanitizing drops styles and they would otherwise show glued to the next word.
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

`pages/` holds 8 real pages (Breaking Bad and its 5 A-to-Z parts, Portal 2, Abandoned Area), taken from the app's own page cache on the emulator. Their snapshots catch any change in how they parse.

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
