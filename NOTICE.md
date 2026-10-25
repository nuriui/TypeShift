# Third-party data attribution

TypeShift's Java implementation is independent. It does not reproduce or claim to reproduce Catchtype's proprietary decision algorithm.

The unchanged files in `src/main/resources/data/typeshift/catchtype/` were retrieved from https://catchtype.com/data on 2026-10-25. The publisher is **Hebbian AI Co., Ltd.** These data files are licensed under **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**: https://creativecommons.org/licenses/by-sa/4.0/. The complete license and upstream README are included alongside the data in source and binary distributions. `SHA256SUMS` records the retrieved file checksums. No upstream data file has been modified.

Underlying source: **FrequencyWords 2018 Korean and English 50k lists**, **Hermit Dave**, https://github.com/hermitdave/FrequencyWords, CC BY-SA 4.0. FrequencyWords derives from **OpenSubtitles 2018**, OPUS, https://opus.nlpl.eu/legacy/OpenSubtitles2018.php (P. Lison and J. Tiedemann, 2016), based on subtitles from https://www.opensubtitles.org/.

Upstream changes: lists were cut and filtered into word lists and statistics. Counts from NSMC (https://github.com/e9t/nsmc), Mozilla Common Voice (both CC0 1.0), and Catchtype-authored lists were added. Google Books Ngram 2020 (CC BY 3.0) and SCOWL 2020.12.07 were used only for exclusions from the subtitle-only list; their contents are not included.

Redistributions must retain attribution, license information, and notices of changes. Adapted versions of these data must be distributed under CC BY-SA 4.0. The repository's MIT license applies to TypeShift's original source code, not to the separately licensed Catchtype data. The independently authored local game-term and Korean technical-term dictionaries are part of the MIT-licensed source.
