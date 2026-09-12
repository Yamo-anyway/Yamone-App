# Yamone Renewal v0.00.23 implementation

Approved source: `yamone-renewal-full-record-detail-v23`

## Locked design tokens

- Mint: bg `#FBFDFC`, soft `#E9FAF5`, primary `#0BB88E`, primary2 `#43D2B0`, border `#DCE3E7`
- Pink: bg `#FFFAFB`, soft `#FFF0F4`, primary `#F06F95`, primary2 `#FFADC4`, border `#EFDFE5`
- Text: Mint `#111827`, Pink `#21171B`
- Card: 15dp radius, 1.5dp border
- Screen horizontal padding: 17dp
- Top/title asset standard: 44dp height
- Bottom navigation: 80dp height, 34dp artwork, 10sp label
- Bottom tabs: Home / Activity / Records / Alarm / Settings

## Asset import rule

The PNG source artwork is kept as raster artwork and imported under `app/src/main/res/drawable-nodpi/` without redesign. Android resource names use the prefix `v23_` and replace hyphens with underscores.

Example: `title-home.png` -> `v23_title_home.png`.

Assets are added as each screen is migrated so the old production UI can continue to build during the transition.

## Migration rule

1. Do not rewrite working alarm, sleep, movement, or location-sharing engines during the UI migration.
2. Move only presentation/navigation first.
3. Connect existing engines after the matching v0.00.23 mock screen is visually complete.
4. Important actions use confirm/cancel modal; simple feedback uses toast/inline message.
5. `main` remains untouched until the renewal branch is verified.
