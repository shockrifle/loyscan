# No bundled store logos, and a non-affiliation note before release

**Status:** Active · amends [[2026-10-13-curated-store-catalogue]] · **Date:** 2026-11-12 ·
**Issue:** [#109](https://github.com/shockrifle/loyscan/issues/109)

Catalogue stores keep showing their name on the brand colour. The app bundles no retailer logos, because Google
Play's intellectual-property policy asks for the owner's permission, and getting written permission from each
retailer is not practical. The store names and brand colours stay. Before the first release, the app and the Play
listing must say that LoyScan is not affiliated with the retailers.

The findings below are web research done on 2026-11-12, **not legal advice**. Quotes are short; follow the links
for the current text.

## Context
- Logos would make cards easier to recognise. The 2026-10-13 decision left them out because they are trademarks,
  and #109 asked whether bundling them is safe, especially with the repo going public (#107).
- The app already lets the user's own front card photo replace the tile (`CardItem` and
  `CARD_TILE_PHOTO_TAG` in `app/src/main/java/com/bdaniel/loyscan/ListScreen.kt`, [[card-photos]]). That is a photo of the whole card, not a
  cropped logo.

## Findings

**Trademark law (EU, Hungary).** Article 14(1)(c) of the Trade Mark Directive
([2015/2436](https://eur-lex.europa.eu/eli/dir/2015/2436/oj)) and the same article of the EU Trade Mark
Regulation let a third party use a mark "for the purpose of identifying or referring to goods or services as those
of the proprietor". Article 14(2) allows this only "in accordance with honest practices". Hungary's Act XI of 1997
carries the same limitation ([SZTNH English text, 2026](https://www.sztnh.gov.hu/sw/static/file/Trademark_Act_XI_1997_EN_jan2026.pdf)).
The Court of Justice reads honest practices as: no impression of a commercial connection with the owner, no
discrediting the mark, no unfair advantage from its repute, and not presenting the product as a replica
(Gillette, C-228/03, [summary](https://cms.law/en/gbr/legal-updates/use-of-third-party-trade-marks-permissible-provided-use-is-honest)).
In Audi, C-334/22 (2024), it held that a use going beyond referring to the owner's product falls outside the
exception ([judgment notice](https://eur-lex.europa.eu/eli/C/2024/1822/oj/eng),
[commentary](https://cliffordchance.com/expertise/services/intellectual-property/global-ip-updates/2024/q2/the-court-of-justice-of-the-european-union-sheds-some-light-on-the-interpretation-of-the-referential-use-of-a-trademark.html)).
Showing a retailer's logo on that retailer's own card is arguably referential. An app made of many retailers' logos
can still look endorsed, and no case on card-wallet apps was found. *(Inference: referential use is defensible but
untested here.)*

**Google Play.** The [Intellectual Property policy](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en)
says: "Don't use another party's trademarks, such as logos or brand names without permission and/or in a way that
could confuse users". It covers "all content in your app and store listing", and a developer with written
permission is told to contact Play before submitting. The
[Impersonation policy](https://support.google.com/googleplay/android-developer/answer/9888374?hl=en) adds: "Don't
imply that your app is related to or authorized by someone that it isn't". It applies to icons, descriptions,
titles and in-app elements. A confusing use "may be suspended", and owners report apps through Google's trademark
complaint process. Neither page says whether a disclaimer is enough. The policy is stricter than the law: it asks
for permission for logos, whatever the referential-use defence.

**Public repo.** Icon collections publish brand logos with a per-icon source, a link to the brand's guidelines, a
separate legal disclaimer, and a takedown route
([Simple Icons contributing guide](https://cdn.jsdelivr.net/gh/simple-icons/simple-icons@16.23.0/CONTRIBUTING.md),
[theSVG legal page](https://thesvg.org/legal)). The file licence covers the drawing, not the mark. It can be done,
but #107 lists logos among the third-party material to keep out of the repo.

**Comparable apps.** Stocard shows retailer logos and describes itself as working with thousands of retailers on
offers and analytics ([business page](https://stocardapp.com/business)). *(Inference: its logos come with
commercial relationships we don't have.)* How free card wallets such as Catima handle logos was not found.

**Asking retailers.** Only 4 of the 23 catalogue retailers offer a logo download, and only one of them states
terms: editorial use with credit. None publishes a brand-licensing contact a small app could use, apart from Shell's
access-controlled brand portal. Written permission would mean 23 separate requests through press addresses, renewed
whenever a catalogue store is added. Checked 2026-11-12; "none found" means not found in a reasonable search.

| Store | Press or brand page | Logo download | Stated terms |
|---|---|---|---|
| Auchan | [press releases](https://www.auchan.hu/sajtokozlemenyek) | not seen | none |
| Brendon | none found | no | — |
| Coop | none found | no | — |
| Decathlon | group logo page behind a login | login only | not visible |
| dm | [group newsroom](https://newsroom.dm.de/latest_media) (German) | press photos only | media use (snippet, unconfirmed) |
| Douglas | [group media library](https://douglas.group/en/newsroom/media-library) | yes (PNG) | editorial use only, with credit |
| IKEA | [Médiatár](https://www.ikea.com/hu/hu/newsroom/media-resources/mediatar-pub15bf5150/) | photos only | credit IKEA as source |
| KiK | [media archive](https://vallalat.kik.hu/vallalat/sajto-es-hirek/media-archivum) | yes (PNG) | none |
| Libri | none found (advertising kit only) | no | — |
| Lidl | [press room](https://vallalat.lidl.hu/sajtoszoba) | no | none |
| Líra | none found (site refused the fetch) | unknown | unknown |
| Möbelix | group [downloads page](https://www.xxxlgroup.com/downloads/) (contents not seen) | unclear | none found |
| MOL | [group media centre](https://molgroup.info/en/media-centre) | press photos only | none for logos |
| mömax | same group page as Möbelix | unclear | none found |
| Ofotért | none found | no | — |
| Penny | [press page](https://www.penny.hu/pennykucko/sajto) | yes (JPG, SVG) | none |
| Praktiker | none found | no | — |
| Rossmann | [HU press room](https://www.rossmann.hu/sajtoszoba); [group media library](https://unternehmen.rossmann.de/presse/mediathek.html) | wordmark JPG (group site) | copyright notice only |
| Shell | [brand portal](https://brandcentral.shell.com) | access on request | logo use needs approval |
| SuperShop | none found | no | — |
| Szerencsejáték | none found | no | — |
| Tesco | none found (site refused the fetch) | unknown | unknown |
| Unix Autó | none found (news feed only) | no | — |

## Options considered
- **Bundle logos as referential use**, with a disclaimer: legally arguable, but against Play's "without permission"
  rule, and it puts third-party material in the public repo.
- **Bundle only with written permission**, per retailer: safe, but impractical for 23 retailers with no licensing
  contact.
- **Fetch logos at run time** (a URL in the catalogue): the same trademark and Play exposure, plus a network call
  and an outside dependency for an offline app.
- **No logos (chosen):** the name on the brand colour, and the user's own card photo as the tile.

## Decision
- **No bundled or fetched logos.** Catalogue stores keep `logo = null`
  (`app/src/main/java/com/bdaniel/loyscan/data/mapper/CatalogueMapper.kt`). Logos are not added on the strength of a
  press kit; a press kit's "editorial use" is not permission for an app.
- **Names and brand colours stay**, as referential use: they identify which retailer's card it is.
- **A non-affiliation note is required before the first release**, in the app (about/settings) and in the Play
  listing (#75): LoyScan is not affiliated with or endorsed by the retailers whose cards it stores, and their names
  belong to their owners.
- The user chose this on 2026-11-12: bundling was ruled out, the press kits of all 23 stores were checked, and the
  disclaimer was made a requirement.

## Why
- Play's IP policy asks for permission for logos, and a suspended listing is the realistic risk, more than a court
  case. Permission is not practical to get (see the table).
- Keeping logos out also keeps the public repo free of third-party marks (#107).
- Names alone are the weakest form of use, and the app can't work without them. Play also lists "brand names", but
  only "in a way that could confuse users". The note, and a listing that doesn't present the app as any retailer's,
  answer that. *(The user accepted this residual risk on 2026-11-12.)*

## Consequences
- Tiles stay the name on the brand colour unless the user adds a front photo ([[card-photos]]). A designed name
  badge is visual polish (#108), not a logo.
- Play screenshots must use made-up cards, with no retailer logos visible on card photos (already a #75 criterion).
- `Store.logo`, the Coil SVG loader and the `INTERNET` permission stay as they are. No shipped store uses them
  ([[coil-image-loading]]). Removing them is out of scope here.
- Adding a catalogue store adds no logo ([[add-a-catalogue-store]]).
