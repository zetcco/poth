# Poth

A personal EPUB reader for Android (Kotlin, Jetpack Compose, Material 3 Expressive).

## Build and install

```sh
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew :app:testDebugUnitTest          # unit tests (plain JVM)
./gradlew :app:installDebug               # build and install on the phone connected over adb
adb shell am start -n dev.indrajith.poth/.MainActivity
```

## Layout

- `epub/`: reading EPUB files: container, OPF (metadata, manifest, spine), and each chapter's
  XHTML and CSS into blocks of styled text (`ChapterParser`). Plain Kotlin, tested on the JVM.
- `reader/`: laying chapters out in pages with Android's `StaticLayout` (`Paginator`), the open
  book and its page cache (`OpenBook`, `Pages`), and timing pages on screen (`PageClock`).
- `data/`: the SQLite database (`PothDatabase`), books and reading positions, the page-view log,
  importing a picked file.
- `dictionary/`: matching dictionary terms in text, guessing a new entry's scope, and the small
  Markdown subset entries are written in. Plain Kotlin, tested on the JVM.
- `actions/`: quick actions, handing a word (filled into a template) to other apps.
- `stats/`: what counts as reading, worked out from the raw page views. Plain Kotlin, tested.
- `widget/`: the home-screen widget (Glance), its calendar drawn at the widget's exact size.
- `ui/`: Compose screens: the book list, the reader, and the dictionary card and editor.

A reading position is a spine index and a character offset in that chapter's text, so it does not
change with the font size or the screen. Changing how chapters are parsed can shift offsets a little.

Books are copied into the app's storage when added and named by their SHA-256, which is also the
book's ID: the same file added twice is one book.

## Plan

Built in iterations; each one ends with something usable on the phone.

- [x] 0. Skeleton: database (books, shelves, page views), add an EPUB, see its text
- [x] 1. Reader core: own page renderer, swipe to turn, position remembered, page views logged
- [x] 2. Reader bars: title/section/page, "18 of 1789", part ticks, safe scrubber, contents
- [x] 3. Pinch zoom (magnify without reflow)
- [x] 4. Dictionary: Markdown entries, Book/Shelf/Library scopes, underlines
- [x] 5. Quick actions: look a word up in installed apps, prompt templates; Wiktionary definitions
- [x] 6. Reading stats (weighted at query time; quick glances don't count), GitHub-style calendar, home-screen widget
- [x] Library: covers, shelves, progress per book, today's reading at the top
- [ ] 7. Polish: search, font settings, `.md` export, backup
- Later: images in entries, where each edit was made
