# Privacy policy

Vialix is a navigation app. It has no account, no analytics, no advertising and no server of its
own. This page says exactly what leaves the phone and where it is kept.

## What is sent, and to whom

- **Your position is sent to the routing server** as the start of every route you preview and of
  every re-route while guiding, together with the destination and the routing options (car /
  bicycle / walking, units, guidance language). By default that server is the public
  [FOSSGIS Valhalla instance](https://valhalla1.openstreetmap.de); the app identifies itself with an
  `X-Client-Id` header and a `Vialix/<version>` user agent. The endpoint is configurable at build
  time, so a build you did not make yourself may use another Valhalla server.
- **Your search text and your position are sent to the geocoder** as you type in the search
  screen, so nearby results rank first. By default that is
  [komoot's public Photon instance](https://photon.komoot.io). Long-pressed points are reverse-
  geocoded the same way to label the pin.
- **Map tiles are fetched from [OpenFreeMap](https://openfreemap.org)**, which therefore sees your
  IP address and which areas of the map you look at, as any tile server does.
- **On devices with Google Play Services**, positions come from Google's fused location provider,
  which is governed by Google's own terms. On devices without it the app uses Android's location
  manager. Spoken guidance uses the text-to-speech engine installed on the phone.

Nothing else is sent anywhere. Each of these providers has its own privacy policy for what it
does with the requests it receives.

## What is stored on the phone

Settings (language, voice, routing profile, units, appearance), your Home and Work if you set
them, and up to 20 recent destinations. All of it stays in the app's private storage, is excluded
from Android backups and device transfers, and is deleted when you uninstall the app. Clear the
recents from the search screen's *Recent* header at any time.

## Permissions

*Precise location* is required to show where you are, to request routes and to guide you along
them. *Notifications* (Android 13 and later) are used only for the notification that keeps
guidance running while the app is in the background.

## Changes and contact

Changes to this policy are made in this file, in the project's repository, where its history is
public. Questions go to the [issue tracker](https://github.com/igalmarino/vialix/issues).
