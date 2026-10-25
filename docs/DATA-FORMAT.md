# Public data analysis

Source: https://catchtype.com/data and its bundled README.txt. Files were inspected before the Java loader was implemented. Downloaded originals, README, license, and checksums are bundled under `data/typeshift/catchtype` in the JAR. Runtime loads classpath resources only; no network requests occur in TypeShift.

| File | Observed format | Use |
| --- | --- | --- |
| korean-words.txt | One word per line, most common first | 1-based frequency rank |
| korean-more-words.txt | Continuation of Korean rank list | Ranks offset by first list length |
| english-words.txt | One word per line, most common first | English veto, prefixes, rank evidence |
| english-subtitle-only-words.txt | One word per line | Reduce subtitle-specific English rank bonus |
| common-syllables.txt | One Hangul syllable per line | Candidate coverage check |
| korean-syllable-pairs.txt | Pair, whitespace, negative integer | Korean score, including `^가`, `힝$` |
| korean-unseen.txt | Previous syllable or `^`, negative integer | Missing-pair fallback |
| english-letter-triples.txt | Triple, whitespace, negative integer | English score, including `^^a`, end `$` |
| english-unseen.txt | Previous two characters, negative integer | Missing-triple fallback |
| unknown.txt | `korean -778` and `english -340` | Language-specific final fallback |

README says unknown.txt is a single numerical value; the actual file has two labeled values. README also describes syllables/letters without explicitly explaining boundaries. The loader follows the actual files and retains boundary keys. Invalid numeric rows and duplicate statistic keys cause loading failure; initialization then disables conversion and logs the cause rather than supplying fabricated statistics.

The upstream scale/formula is not specified. TypeShift treats numbers as relative scores, averages each candidate's boundary-inclusive ngrams, and divides by 100 as an independent heuristic scale. These scores are **not calibrated probabilities**. It adds a diminishing frequency-rank bonus and compares Korean against English. Unknown words use contextual unseen values, then language-specific unknown values. At least 80% common syllables and a larger score margin are required for unknown Korean words. Initial inline conversion requires complete multi-syllable candidates with at least four raw keys. At a whitespace boundary, frequent Korean words receive a small additional allowance. When the preceding three tokens (at most 64 characters) contain at least two Hangul syllables, the boundary threshold is reduced, including for statistical unknown words of three or more syllables. A one-syllable candidate with at least three raw keys is allowed only at such a contextual boundary and only when it ranks in the top 1,000 Korean words. These contextual heuristics may increase false positives; English dictionary and protection vetoes remain in force. Exact English words are vetoed, and English prefixes are vetoed before a boundary. Sensitivity changes the required margin, not the data.

After the first decision, the active token continues composing from its original keys, including temporary trailing jamo. This deliberate stateful behavior is different from accepting each partial token as a new dictionary decision. URL/path/number markers restore the raw token if they arrive after an early conversion. Whitespace ends the token session but, since 26.3.3, preserves the language preference. Future punctuation cannot be known before it is typed, so a path-like token may briefly appear as Hangul before its marker arrives.

## Boundary word structure evidence (26.3.2)

At whitespace, `!?;,`, or Enter, KoreanWordAnalyzer considers a known prefix of at least two syllables plus a listed suffix, or two known components of at least two syllables each. Components must rank in the first 30,000 Korean entries or appear in the independent local technical lexicon. The word is limited to 3–16 syllables; only one split is considered at a time. Evidence adds 0.9 to the score and removes the extra unknown-word penalty. Exact local technical terms receive 1.6 evidence. Original exact dictionary words continue using their frequency evidence. All other validation and English/protected-token vetoes remain. This is a heuristic component check, not a linguistic morphological parser, and may produce false positives.

`korean-technical-words.txt` is independently authored MIT-licensed supplemental data; it does not modify the upstream frequency ranks or files. Period remains a file/URL protection marker, not a conversion boundary. Enter finalizes only a matching, active typed session; paste, IME and stale sessions are excluded.

## Persistent language preference (26.3.3)

LanguageTracker starts UNDECIDED. An accepted Korean decision sets KOREAN. In this state, ambiguous subsequent typed tokens are composed from their original ASCII keys even if absent from all dictionaries or common-syllable lists. This inherited preference is not itself new statistical evidence. Protection rules still override display without changing the surrounding language preference.

English evidence sets ENGLISH and restores only the active token's exact original keys, including case. Inline English reversal requires at least four keys; a boundary permits two. Known Korean words or supported Korean components block this reversal. Otherwise a known English word wins if the Korean candidate is incomplete Hangul or English exceeds its score by 2.5. At boundaries only, unknown English tokens of at least six keys may win if their average English score is at least -3.5 and the English margin is at least 3.0. These thresholds provide resistance to switching on every ambiguous prefix. New Korean evidence can switch back.

Undo chooses English preference and suppresses reconversion of the undone token. Temporary Korean display of an eventual English word does not discard undo for the preceding retained Korean token. Cursor/selection discontinuities, external text updates and native IME input reset preference. No OS layout switching or rewriting of native committed Hangul occurs. Earlier completed tokens are not retroactively reinterpreted using later context. This implementation remains an independent heuristic, not Catchtype's private algorithm.

## Numeric date/time suffixes (26.3.4)

A fresh typed run of ASCII digits may open a separate suffix session. NumericSuffixMatcher recognizes the raw key sequences for 년, 월, 일, 시, 분, 초 and a bounded set of common endings. Digits remain outside the replacement range. Complete unit forms establish Korean preference; incomplete prefixes only continue an already-converted suffix. Unsupported continuations and protection markers restore the original suffix. This exemption never bypasses command, URL/path, local-term or online-player protection. External edits, IME and undo cancel the fresh-number eligibility. Calendar values are not semantically validated. Other numeric identifiers remain protected; this is not a general number-plus-Korean parser.

## Attached particles after English stems (26.3.5)

AttachedParticleMatcher searches longest-first for an English stem of at least two characters in the top 20,000 English entries or the protected local lexicon, followed by the raw keys of a listed Korean particle. Exact full English words and protected full tokens do not undergo this split. The stem must already be displayed literally before the suffix arrives; a coincidental English prefix inside an actively composed Korean name does not qualify. Known whole Korean words also take precedence. Complete particles convert in either language preference; incomplete particle prefixes compose only in Korean preference and only before a boundary. The English stem retains its exact case. An active session records the literal stem length so raw-key backspace does not recompose the stem as Hangul. Undo restores the entire original token. Existing command, URL, filename, numeric and online-player protections run before this analysis. This bounded suffix recognizer does not support arbitrary mixed-language segmentation.

## Deletion reconciliation and quotes (26.3.6)

The adapter uses KeyEvent.shortcutKey() with KEYCODE constants, matching vanilla EditBox dispatch. It observes deleteCharsToPos/deleteWords with a nesting guard, including their internal insertText("") selection deletion. Failed raw-key backspace no longer destroys provenance before vanilla edits the widget.

InputController retains the most recent owned conversion span separately from the active session. A pure deletion may recover a session only if the entire before-text matches that owned snapshot, the retained prefix is within the span, and an exact prefix of the recorded original keys composes to the retained display. Literal English prefixes and numeric-suffix origin are retained. Cursor navigation may retain this bounded provenance; paste, external setValue/insertText, native IME, raw restoration and undo discard it. No arbitrary Hangul-to-key inference is used.

ASCII double quotes are token boundaries. Opening a quote remembers the surrounding language preference, known English words take precedence inside, and closing restores that preference. This permits adjacent Korean after a quoted English word and protects quoted game terms. Single quotes, escape processing and nested quotation syntax are not implemented.

## Dotted numeric version suffixes (26.3.7)

Freshly typed numeric groups separated by single dots retain numeric-prefix eligibility. Dotted prefixes allow only 버전 plus the supported endings, rather than ordinary calendar units. The dot exemption also requires the complete raw token shape to be numeric groups plus ASCII suffix keys (with optional supported terminal punctuation). Slashes, filenames with later dots, arbitrary identifier suffixes, pasted tokens and commands retain protection. Original version text is outside the replacement span, including when undoing or recovering from deletion. Codex is an explicit local English term, so the existing attached-particle rules apply.

## General continuation after numeric Korean evidence (26.3.8)

Once a numeric suffix has actually converted, its original key stream continues composing instead of requiring each new form to occur in the bounded ending list. Thus 분 → 분전 and 시 → 시야 remain Korean. An integer or a numeric prefix with at least two dots may also begin a complete multi-syllable suffix if ordinary Korean evidence accepts it or Korean preference is already active; exact English words do not qualify. Two-group decimal-like prefixes retain the explicit version-word restriction. Number text is excluded from the replacement. Full-token URL/file/command/player checks still run first, and markers restore raw suffixes.

Local Korean technical terms may contribute evidence during typing, not only at boundaries. 버전 is included. Ambiguous ahem/모드 remains unresolved without Korean preference; this change does not override exact English vetoes in undecided mode.

## Unknown-word backoff (26.3.10)

At token boundaries only, unknown Korean candidates whose raw keys are neither English words nor English prefixes can receive a second heuristic score. It requires at least five keys, two common syllables, and English mean trigram score <= -4.5. Missing internal Korean pairs back off to the natural log of the destination syllable support fraction, if that syllable occurs in at least 20 of the top 20,000 Korean dictionary words. Support counts distinct syllable presence per word, not corpus token frequency. Existing pairs and boundary transitions remain unchanged. Both accumulated scores are divided by raw key length before comparison, avoiding different transition-count denominators in this auxiliary comparison. The public integer scores have no documented probability calibration; combining them with log support is an empirical heuristic, not a recovered Catchtype model or calibrated probability. Original data files remain unchanged; derived counts exist only in memory.

### Rare syllables (26.3.11)

The boundary-only auxiliary path now requires complete Hangul, but no longer requires all syllables in common-syllables.txt. If dictionary support is below 20, the original unseen transition is retained instead of rejecting the candidate. The auxiliary margin subtracts 0.3 times the uncommon-syllable fraction and 2.0 times the fraction missing from korean-unseen.txt. These are independent heuristic penalties, not probabilities. Only an eligible auxiliary score that is at least the original score may bypass the normal common-ratio gate; English word/prefix, protected-token, enabled, length and sensitivity checks remain. This allows 딴겜 without changing dictionary files while keeping unsupported 똠쀍쀍 rejected in the regression cases.

## Shared scoring update (26.3.12)

Both Korean pair totals and English trigram totals are now divided by raw key count (and the data scale 100). Prefix evaluation excludes the trailing $ marker; boundary evaluation includes it. Korean raw length is passed explicitly, preserving shifted-key counts rather than counting displayed syllables. English empty and Korean zero-key inputs return zero. This is an independently implemented score normalization, not calibrated probability or a full reproduction of Catchtype's classifier. Dictionary bonuses and local policy remain our own.

Unknown boundary candidates use the eligible backoff margin plus morphological evidence instead of mixing differently normalized margins. The old English transition mean is retained only as the auxiliary eligibility filter to avoid broadening that filter during this change. Rare-syllable penalties still apply. Unknown inline candidates without word evidence require at least five syllables and margin 4.5; shorter unknown words wait for a boundary unless Korean state was already established or the existing ambiguous-stem continuation rule applies. These are conservative local policy choices.
