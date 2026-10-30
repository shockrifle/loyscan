# "Frequently added" stores are a curated catalogue flag, for the phone's country

**Status:** Active · **Date:** 2026-10-30 · **Issue:** [#6](https://github.com/shockrifle/loyscan/issues/6) (step 1)

## Context

#6 asks the store picker for "frequently added" stores for the user's country. The app has no server, so nothing
knows what other users add, and the catalogue ([[store-catalogue]]) has only Hungarian stores (16 on 2026-10-30).

## Options considered

- **What is frequent:**
  - a curated flag on catalogue entries;
  - every catalogue store of the user's country (all 16 today, so the section would repeat the full list);
  - the stores this user has added cards for most (empty for a new user, who needs the shortcut most, and blind to
    the country).
- **Where the country comes from:**
  - the SIM's country, then the mobile network's, then the region of the device's language;
  - the language region only (a user in Hungary with an `en-US` phone would get `US`);
  - the language region plus a country setting (no settings screen exists).
- **Schema:** a new `stores.frequentlyAdded` column, with a version bump (and a migration, or a wipe of dev installs)
  or with `2.json` rewritten in place at version 2.

## Decision

- A catalogue entry has `frequentlyAdded` (default `false`), stored in the `stores` column of the same name and on
  `Store`. Custom stores are never frequently added. Which stores carry it is an editorial pick (product choice,
  2026-10-30), listed on [[store-catalogue]].
- The country is `UserCountry.code()`: `DeviceUserCountry` (`app/src/main/java/com/bdaniel/loyscan/data/device/UserCountry.kt`)
  takes the first non-blank of `TelephonyManager.simCountryIso`, `networkCountryIso` and the configuration locale's
  country, upper-cased; `null` when all are blank. No permission is needed.
- The picker shows the flagged stores whose `countryCodes` contain that country (`frequentlyAddedIn`), by name, under
  "Frequently added" above "All stores", while nothing is searched ([[add-card]]).
- No version bump: the column was added and `2.json` rewritten at version 2 (product choice, 2026-10-30: early
  development, no migrations yet). A dev install from before must clear its data ([[reset-local-database]]).

## Why

- Curated, because it is the only option that is useful to a new user without a server, and it keeps the section
  short. The country matters for the flag once the catalogue has a second country.
- SIM and network before the language: they say where the user lives, the language often doesn't. They need no
  permission, and a tablet without a SIM still has its locale.
- No migration: nothing is released yet, and [[no-room-migrations]] (#12) settles migrations before the first
  release.

## Consequences

- A store's flag ships with the catalogue, so changing the pick is a catalogue edit ([[add-a-catalogue-store]]).
- Outside the countries the catalogue covers, the picker has no "Frequently added" section; the full list is unchanged.
- The country is read once per picker (`StorePickerViewModel`), not observed: a SIM swap shows on the next add.
