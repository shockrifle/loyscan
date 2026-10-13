# Add a store to the catalogue

How to add or change an entry in `app/src/main/assets/catalogue/stores.json` ([[store-catalogue]]). The sourcing
rules come from [#3](https://github.com/shockrifle/loyscan/issues/3) and [[2026-10-13-curated-store-catalogue]].

## 1. Collect the facts, from a real card or the retailer

For each fact, note where it came from. Use a real card (yours, scanned or read), or the retailer's own public
material (card terms, FAQ, app help, card images on its site). Another app's catalogue may tell you *what* to check,
never the answer to copy.

- name as shown to users, and short search aliases (programme name, spelling without accents);
- brand colour (`#RRGGBB`, from the retailer's own site, not sampled from a logo on white);
- barcode format;
- the printed number: its length, fixed leading digits, check digit, how it's grouped;
- how the barcode content relates to the printed number: the same, or with a prefix, padding or check digit added.

## 2. Write the entry

```json
{
  "id": "hu-example",
  "name": "Example",
  "aliases": ["Example Club"],
  "countryCodes": ["HU"],
  "color": "#1A2B3C",
  "defaultBarcodeFormat": "EAN_13",
  "cardNumberRule": {
    "id": "hu-example", "format": "EAN_13", "minLength": 13, "maxLength": 13,
    "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "VERIFY" },
    "display": { "pattern": "#### #### #### #" }
  }
}
```

- **`id`:** the country code, then a slug of lowercase letters, digits and single hyphens. **It never changes once
  shipped**, because cards reference it. A new country's card from the same chain is a new entry (`sk-…`).
- **`cardNumberRule`:** optional. Leave it `null` when the barcode is just the typed number. The **core** is the
  number as printed on the card. Write the rule so that typing what is printed produces the barcode. Field meanings
  and the format checks are in [[2026-10-09-card-number-rule-engine]]. For a fixed-length number, set
  `minLength == maxLength`.
- No `logo`: the catalogue ships none ([[2026-10-13-curated-store-catalogue]]).
- Keep the array sorted by id.

## 3. Test it

- Add a test vector to `ShippedCatalogueTest.vectors` (`app/src/test/java/com/bdaniel/loyscan/data/local/db/`): a made-up
  core number, never a real person's card, and the barcode content and display number the rule must produce.
- `./gradlew testDebugUnitTest --tests '*Catalogue*'`. A malformed entry fails `ShippedCatalogueTest`, and so does a rule without a vector.
- On a device: open a card of the store and check that its barcode scans at the till (once #4 draws barcodes).

## Changing an existing entry

Edit it in place. On the next app start every installed copy rewrites that store's row, and typed cards of that
store get the new barcode ([[store-catalogue]]). Don't rename the id. To retire a store, remove its entry: existing
rows and their cards stay.
