# Echo-Saavn-Extension

An unofficial JioSaavn extension for [Echo](https://github.com/brahmkshatriya/echo) — a music player that supports extensions for music sources.

## Disclaimer

This extension is **unofficial** and **not affiliated with JioSaavn**. It uses a community-maintained unofficial API. Use at your own risk, and respect JioSaavn's Terms of Service.

## Features

### Browsing

- Parse tracks, albums, playlists and artists
- Home feed with all supported languages (Default tab mixes languages)
- Search by URL — paste a JioSaavn link to open the item directly
- Pagination support across all lists and feeds
- Detail feeds for tracks, albums and playlists — related items, recommendations and more

### Playback

- 48, 96, 160 and 320 Kbps audio qualities
- Endless queue — auto-adds tracks similar to the last song in the queue

### Library (no login required)

- **Save to Library** — keep tracks, albums, artists and playlists locally
- **Saved Songs playlist** — a read-only playlist mirroring your locally saved tracks
- **Playlist creation and management** — create, rename, delete and manage local playlists

### Login

Login opens JioSaavn's official login page in a WebView. Your credentials never touch the extension — only the session cookie is captured.

- **Follow / unfollow** Artists and Playlists
- **Favourite / unfavourite** Tracks and Albums (syncs with your JioSaavn liked items)
- **JioSaavn playlist management** — add, remove and reorder tracks in playlists you created
- **Read-only liked playlists** — view your JioSaavn playlists

### Sharing

- **Share URL** — share any item's JioSaavn link
- **Share playlist as CSV** — export all tracks with title, artists, album, duration, release date, year, label, composer, explicit flag, JioSaavn ID and URL (toggle in settings)

  The CSV follows RFC 4180 with a UTF-8 BOM, so it opens correctly in Excel, Google Sheets, Numbers, pandas and any standard CSV parser without manual encoding steps.

## Settings

| Setting | Description |
|---|---|
| **Default Home Languages** | Languages mixed on the Home feed's Default tab |
| **Auto-Radio** | Auto-add similar tracks when the queue ends |
| **Rich Share** | Share playlists as CSV instead of a URL |

## Limitations

- **No ISRC** — JioSaavn doesn't expose ISRC, so CSV-based track identification relies on title, primary artists and duration, with album, release date and composer as tiebreakers.
- **Read-only saved items** — "Saved Songs" and liked JioSaavn playlists can't be edited from within the playlist view.

### Planned

- Saavn playlists can't be renamed or deleted from the extension yet. Only track add/remove/reorder is supported (for now).

## Requirements

- [Echo](https://github.com/brahmkshatriya/echo) music player
- An active internet connection (streaming requires network access)
- A JioSaavn account (optional — only required for login features)

## Installation

1. Install Echo from the [official releases](https://github.com/brahmkshatriya/echo).
2. Download the latest extension APK from releases
3. Open Echo -> Manage Extensions -> Add -> File -> Add, and select the .eapk.

## Credits

- **Echo** framework by [brahmkshatriya](https://github.com/brahmkshatriya)
- **Unofficial JioSaavn API** by [sumitkolhe](https://github.com/sumitkolhe)
- Built with the Echo extension SDK

## License

MIT
