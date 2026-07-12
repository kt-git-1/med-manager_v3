# App Store Screenshots

Generated screenshots for the App Store product page. The iPhone set embeds
real light-mode simulator captures populated with DEBUG-only sample data; the
application UI is not redrawn by the generator.

## iPhone 6.9-inch Set

- Directory: `iphone-6.9/`
- Size: `1242 x 2688` pixels
- Format: PNG
- Count: 7
- Preview: `iphone-6.9-contact-sheet.png`

## iPad 13-inch Set

- Directory: `ipad-13/`
- Size: `2064 x 2752` pixels
- Format: PNG
- Count: 7
- Preview: `ipad-13-contact-sheet.png`

These files are intended for the iPhone and iPad screenshot slots in App Store Connect. The layout uses short Japanese copy, app branding, and real application screens for the core release story:

1. Caregiver mode and patient mode introduction
2. One-tap scheduled dose recording
3. Medication list management
4. Inventory and refill awareness
5. History sharing
6. Senior-friendly patient mode
7. Family notification after the patient records a dose

## Regenerate

Use the Codex bundled Python runtime because it includes Pillow:

```sh
/Users/kaito/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 marketing/app-store-screenshots/generate_iphone_screenshots.py
/Users/kaito/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 marketing/app-store-screenshots/generate_ipad_screenshots.py
```
