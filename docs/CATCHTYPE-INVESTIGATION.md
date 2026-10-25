# Catchtype investigation — 2026-10-25

Scope: read-only inspection of the locally installed Catchtype 0.3.0. No app patching, execution injection, preference changes, or classifier implementation changes were performed.

REA 6.7.0 was fetched to /tmp/typeshift-rea-npm and invoked as a one-shot CLI. Global MCP configuration was not changed. The current GitHub main documentation describes inspect-desktop-application, which is absent in this installed npm release; inspect-macho succeeded instead. REA doctor reports no configured Hopper, Ghidra or IDA provider. Function decompilation is not complete.

## Evidence

- Target: /Applications/Catchtype.app/Contents/MacOS/Catchtype
- Architectures: x86_64, arm64; REA selected arm64.
- SHA-256: 6970e1a75fc60a522cc1f14cdb8dd7d1ce8e1d718bd1e00b1fd88a69efc00768
- REA evidence: ev_23f993cd5bfab4fbc7371c627dca28258d914fe58df3bc0a19e4ec4f48d42405
- Full temporary metadata: /tmp/typeshift-catchtype-macho.json
- Supplementary symbol inventory: nm, decoded by swift-demangle; /tmp/typeshift-catchtype-demangled.txt.

Symbol signatures include Classifier.judgePrefix(_:startedIn:), Classifier.judgeWord(_:startedIn:), KoreanShape.init, LanguageModel.koreanScore(_:isComplete:), Classifier.isEnglishWord, Classifier.isSureEnglish, Classifier.isLoanword and Classifier.mixedSplit. These establish named routines and arguments, not their actual control flow, thresholds, or execution on the reported examples. No original implementation was copied into the mod.

## TypeShift dkak diagnosis

The local independent engine composes dkak as 아마. 아마 is present at Korean rank 248; dkak has no English word/prefix match. Default boundary margin is 1.055664495752135 against threshold 1.4, so initial-state conversion is rejected. Korean context lowers the threshold enough to accept it. The unknown-word backoff does not apply because 아마 is already known. Thus this case is not a missing-dictionary or rare-syllable issue.

## Next analysis targets

Configure a native decompiler, then trace judgeWord and judgePrefix into LanguageModel and KoreanShape. Compare observations for dkak, ahem, eksxk and Eksrpa with ordinary English controls, explicitly separating cold language state, learned exceptions, live-prefix and boundary decisions. Treat decompiler output as evidence requiring validation, not recovered source or a guarantee of behavior.

## Native analysis follow-up

Ghidra 12.1.4 official release was downloaded and SHA-256 verified against GitHub release metadata (`ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db`). Its included native decompiler source was compiled for macOS arm64 in /tmp/typeshift-ghidra. REA uses JDK 21 and a thin arm64 copy of the installed executable; the original application is untouched.

### Assembly-confirmed findings

The following observations use the arm64 slice of the binary identified above, via Apple's otool, cross-checked against Swift symbol names. They are stronger than symbol-name guesses but do not establish every classifier branch.

- `LanguageModel.koreanScore` at 0x100045708 accumulates pair/unseen values. Its divisor is the sum of Hangul.Unit fields at offset 0x10. The Unit.keyCount setter at 0x10006f098 writes exactly this field. The final division is at 0x1000458e8. Thus Korean score normalization uses key counts, not pair counts.
- At 0x1000458b4–0x1000458e0, isComplete controls whether the final end-marker transition is added.
- judgeWord passes true to koreanScore at 0x10001534c and to englishLogProbability at 0x1000153c8.
- prefixLean passes false to koreanScore at 0x10001be6c. judgePrefix passes false to englishLogProbability at 0x10001b710.
- judgeWord divides English log total by its returned count at 0x1000153dc–0x1000153e0, adds a capitalLog result and subtracts from the Korean score at 0x100015408–0x10001540c. The returned English count and capitalization correction require separate careful interpretation; do not equate this with TypeShift's current heuristic margin.
- Actual calls from judgeWord include mixedSplit, isEnglishWord, isPluralOfEnglishWord, isLoanword, isSureEnglish, RankedWords.rank and applyParticleAgreement. This is a layered classifier, not only one n-gram threshold.

### Independent numerical check

Using TypeShift's bundled public data, 아마 has summed Korean boundary score -11.37. Dividing by three pair transitions gives -3.79 (our existing score); dividing by four raw keys gives -2.8425. This arithmetic demonstrates a normalization difference. It is not a measured Catchtype runtime result or proof that this alone determines its decision. Comparison CSV: /tmp/typeshift-catchtype-analysis/normalization-comparison.csv.

No decompiled implementation has been copied into TypeShift. Next independent-engine work should distinguish prefix versus complete-token scoring and use an explicitly chosen normalization, then retune on both Korean and English cases instead of adding word-specific exceptions.

## Completed focused decompilation and limits

REA/Ghidra full auto-analysis encountered Swift signature/namespace reconstruction errors and was explicitly terminated after the focused fallback completed; its owned Node and Java processes were confirmed gone. It did not produce a successful judgeWord Evidence result.

The same Ghidra installation was then used directly with a small locally authored GhidraScript, no global auto-analysis, and bounded per-function decompilation. All four targets exported successfully: judgeWord, judgePrefix, koreanScore, englishLogProbability. Temporary files are /tmp/typeshift-catchtype-analysis/{judgeWord,judgePrefix,koreanScore,englishLogProbability}-focused.c; script and focused.log are in the same analysis folder. These outputs have imperfect Swift register/return typing. In particular, inferred floating return arguments and tuple return values must not be treated as valid source. The normalization and isComplete observations above were cross-checked against actual instructions, not inferred C parameter names. No runtime calls into Catchtype were made.

Current result: analysis setup is functional and concrete score/prefix differences were identified. The precise branch taken by Catchtype for dkak, the complete classifier weights, state enum interpretation, and accuracy over a corpus are still unverified. TypeShift production code and version remain 26.3.11 during this research turn. A subsequent independent implementation should change the shared scoring design and evaluate regressions rather than transplanting the decompiled code or copying private exception lists.
