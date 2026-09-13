# Yamone v0.00.23 design source

Place the approved web-design ZIP here as:

`design-source/yamone-v23.zip`

Expected ZIP contents may have one top-level folder. The importer searches for the folder containing all three files below and uses that as the design root:

- `index.html`
- `app.js`
- `styles.css`

The approved `assets/` PNG folder is copied without image conversion. During the Android build, `tools/import-yamone-v23.sh` adds only the Android-specific `mobile.css` and `app-mobile.js` files and injects their references into `index.html`.

The production GPS, alarm, sleep-recording and location-sharing implementation is intentionally not connected during this design-review stage.
