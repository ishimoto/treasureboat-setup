# Schema audit

Checks a live PostgreSQL database against what the TreasureBoat `.eomodeld` models
actually expect. Read-only: no writes, no DDL.

## Why this exists

`@TBEnterpriseMigrationSkipError` rolled back inside a `finally`, so the rollback fired on
**success** exactly as it fired on failure. An annotated migration did precisely what it was
asked, had its work discarded, and the version number advanced as though it had applied.
Nothing was logged, because nothing had failed.

Found on AppGWare on 2026-08-29, when a Person's licence panel died with
`relation "tb_dis_ServerLicense" does not exist` — a rename from months earlier that had run,
succeeded, and been thrown away. Fixed in tb-core `2b7aa658`.

The point of this tool is that the fix stops it happening again but repairs nothing already
broken, and the damage is invisible until something reads the missing table or column — which
may be months later, or never, or in front of a customer.

## Running it

```sh
# 1. regenerate from the current models (run from GitRoot)
python3 treasureboat-setup/schema-audit/gen-tb-schema-audit.py . \
        treasureboat-setup/schema-audit/tb-schema-audit.sql

# 2. set the schema, near the bottom of the generated file:
#      SELECT 'public'::text AS sch          <-- EDIT for the target database
#
# 3. run the whole file against the database and read row 0 first.
```

**Row 0 is a schema check.** It prints the database and how many tables it can see. If that
count is 0 or absurdly low, stop — the schema is wrong and every row beneath it is noise.
That check exists because we hit exactly that twice in one afternoon.

## Reading the output

| finding | meaning |
|---|---|
| `0 SCHEMA CHECK` | read this first, always |
| `1 MISSING TABLE` | the model wants it, the database has not got it |
| `2 MISSING COLUMN` | the dangerous one — fails on first read, long after the deploy |
| `3 NOT DROPPED/RENAMED` | a rolled-back drop or rename; usually cosmetic |
| `4 NOT DELETED` | a rolled-back column delete; harmless |

Two rules for reading it, both learned by getting them wrong first:

- **A whole model missing means that framework is not installed, not that something broke.**
  Damage looks like *some* tables of a model missing while its others are present.
- **The detail column on a missing column names the model that wants it.** A partial entity
  can contribute columns to another framework's table — distribution's `TBServerPerson` adds
  `company`, `about`, `gitHub` and `idTBServerLicense` to basemodel's `tb_bm_Person`. If that
  model is not installed here, those columns are absent legitimately. Cross-check against
  section 1.

## Regenerate after any model change

The expected table and column list is only as current as the models it was built from. When
`TBNCMailReceive` was retired from the model, the stale audit reported its table as a missing
table on a database that was perfectly fine.

## Known schemas

| app | database | schema |
|---|---|---|
| AppGWare | `gw_fist` | `public` |
| Edison | `es_pg_lake` | `public` |
| Edison | `es_pg_cscw` | `public` |
| Boise | `fft_prod_bak2_pg` | **`fft_prod_bak2`** |

Edison's acotro / acotro_corecom / cscw / nrrm faces are FrontBase and out of scope for this
script.

## Repairing what it finds

Two things that cost real time on 2026-08-29 and will again:

- **Some clients run with autocommit OFF.** A `DROP` reported success three times on AppGWare
  and rolled back each time. `COMMIT` is not optional.
- **Never verify a change from the session that made it.** Inside an open transaction the
  change is visible, so the verification honestly reports success while nothing is committed.
  That produced a false "clean" that was believed for several minutes. Verify from a **fresh
  connection**.

## Results, 2026-08-29

Every database checked — AppGWare, Edison `es_pg_lake`, Edison `es_pg_cscw`, Boise, and local
development copies — carried `tb_bm_navigationFavorite` with a lower-case `n` where the model
has always said `tb_bm_NavigationFavorite`. Four for four. Assume any TreasureBoat database has
it until shown otherwise. `TBBaseModel29` carries the rename, and depends on tb-core `2b7aa658`
being in place first, or it is a silent no-op.
