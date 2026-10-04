# tb-whatsnew

What an application actually gains by moving its framework pins.

```
tb-whatsnew ~/GitRoot/NipponYa/Nipponya/pom.xml
tb-whatsnew <app-pom> --target 21.0.16
tb-whatsnew <app-pom> --only features -v
tb-whatsnew --from 21.0.10 --to 21.0.15 --only core,sangria
```

## Why this is not a CHANGELOG.md

A hand-written changelog is a second copy of what git already knows. Across fifteen
framework repos it would need updating on every release, and the first time one is
forgotten it reports "no changes" when there were — which is worse than having nothing,
because you would believe it. This reads git, so it cannot drift: if nobody made a commit,
an empty result is correct by construction.

## Reading the output

```
  framework   from      to         commits   src  schema  data   note
  features    21.0.10   21.0.15         33    67       3     2   SCHEMA — test this
  pro         21.0.10   21.0.15         13    19       0     5   data migration
  prox        21.0.1    21.0.15          2     0       0     0   label only, no source changed
```

- **commits** — real commits, with `Release x` / `Back to …-SNAPSHOT` bookkeeping filtered out
- **src** — files changed that are actually source (`.java`, `.wod`, `.html`, `.plist`, …)
- **schema** — migration classes added. **This is the risk column.** They alter tables, they
  are what to test, and they are what makes an out-of-date database refuse to boot
- **data** — `EOMigration/*.xml` added: rows edited, not tables

`-v` lists the commit subjects and names each migration.

## The gap is not distance

A TreasureBoat version names the **release it came from**, not how many times that framework
has been released. A framework that was not in a release simply keeps its old number, so
gaps are normal and they are information.

`tb-prox 21.0.1 → 21.0.15` therefore means "was in none of the fourteen releases in
between" — it did not change. The wide gap is evidence of *less* change, not more, which is
the opposite of what the numbers suggest. Reading it the other way is the mistake this
script exists to prevent; it was made on 2026-10-04 while deciding Edison's bump, and the
answer took a hand-written sweep to recover.

`--target` follows the same rule: asking for `21.0.16` resolves each framework to its newest
release **at or below** that, so a framework whose latest is 21.0.15 reports 21.0.15 rather
than erroring.

## What it does not tell you

Why a release happened. That lives in the release commit messages —
`git log --grep='^Release' --oneline` in any framework repo.

## Notes

- Reads the pom's own `<properties>`, never a `<profile>` — so the `-Psnapshot` block that
  maps everything to `21.99.99-SNAPSHOT` is correctly ignored.
- Expects repos at `~/GitRoot/treasureboat-<key>` for each `tb.fw.<key>` pin. Anything it
  cannot find is listed at the end rather than silently skipped.
- `--fetch` updates tags first; without it you are reading what you have locally, and a
  missing tag is reported as such rather than guessed around.

Related: `feedback_framework-pin-alignment` (move the whole table together),
`feedback_framework-release-flow` (how a release is cut).
