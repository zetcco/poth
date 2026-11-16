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

- `epub/`: reading EPUB files: container, OPF (metadata, manifest, spine), chapters.
- `data/`: the SQLite database (`PothDatabase`), the books table, importing a picked file.
- `ui/`: Compose screens: the book list and the reader.

Books are copied into the app's storage when added and named by their SHA-256, which is also the
book's ID: the same file added twice is one book.

## Plan

Built in iterations; each one ends with something usable on the phone.

- [x] 0. Skeleton: database (books, shelves, page views), add an EPUB, see its text
- [ ] 1. Reader core: own page renderer, swipe to turn, position remembered, page views logged
- [ ] 2. Reader bars: title/section/page, "18 of 1789", chapter ticks, safe scrubber, contents
- [ ] 3. Pinch zoom (magnify without reflow)
- [ ] 4. Dictionary: Markdown entries, Book/Shelf/Library scopes, underlines
- [ ] 5. Quick actions: look a word up in installed apps, prompt templates
- [ ] 6. Reading stats (weighted at query time; quick glances don't count)
- [ ] 7. Polish: search, font settings, `.md` export, backup
- Later: library and shelf screens, images in entries, where each edit was made
