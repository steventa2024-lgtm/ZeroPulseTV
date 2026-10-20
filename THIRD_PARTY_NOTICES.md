# Third-party notices

## Software

The APK contains **no third-party code**: it is written against the Android framework only, with no
libraries. Build-time tools are not distributed with it:

| Tool | Used for | Licence | Distributed? |
|---|---|---|---|
| Android SDK Build-Tools 34 (aapt2, d8, zipalign, apksigner), Android platform 34 (`android.jar`, `core-lambda-stubs.jar`) | Compiling, dexing, signing | [Android Software Development Kit License](https://developer.android.com/studio/terms) | No (compile-time only) |
| Eclipse Temurin JDK 17 | `javac`, `jar`, `keytool`, unit tests | GPLv2 with Classpath Exception | No |

Fonts: the app uses the TV's system fonts (Roboto on Android TV); no font files are bundled.

### OpenPulse (separate project, not bundled)

The optional JARVIS feature talks to [OpenPulse](https://github.com/steventa2024-lgtm/openpulse), a
separate project under the MIT License, running on the user's own PC. No OpenPulse code is included in
ZeroPulse TV; the TV implements the TV-link protocol independently (`launcher/src/tv/calmhome/launcher/jarvis/`).

## Wallpaper photographs

Nine photographs from [Wikimedia Commons](https://commons.wikimedia.org/) (Featured Pictures) are bundled in
`launcher/assets/wallpapers/`. **Changes made:** each was cropped to 16:9 and resized to 1920×1080 and
re-encoded as JPEG. The modified files are distributed under the same licence as the original. Each photo is
credited on the Home screen while shown and in **Settings → About**.

| File | Subject | Author | Licence | Source |
|---|---|---|---|---|
| wp01.jpg | Near Lag da Pigniu, Graubünden, Switzerland | Dominicus Johannes Bergsma | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Panixersee_(Lag_da_Pigniu)_boven_Andiast._26-09-2025._(d.j.b.)_09.jpg) |
| wp02.jpg | Vihren Peak, Pirin Mountains, Bulgaria | Marcin Szala (Pudelek) | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Pirin_-_Vihren_peak.JPG) |
| wp03.jpg | Worm Bay, Port Campbell National Park, Australia | Dietmar Rabich | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Peterborough_(AU),_Port_Campbell_National_Park,_Worm_Bay_--_2019_--_0863.jpg) |
| wp04.jpg | Piedras Rojas, Salar de Aguas Calientes, Chile | Diego Delso | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Piedras_Rojas,_salar_de_Aguas_Calientes,_Chile,_2016-02-08,_DD_72.JPG) |
| wp05.jpg | Wooded island reflecting on still water | Basile Morin | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Pirogue_and_tiny_wooded_island_reflecting_in_the_water.jpg) |
| wp06.jpg | The Mekong at sunset, Don Det, Laos | Basile Morin | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Pirogue_running_on_the_Mekong_at_sunset_with_pink_clouds_in_Don_Det_Laos.jpg) |
| wp07.jpg | Saslonch from Plan de Frea, Dolomites, Italy | Wolfgang Moroder | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Plans_de_Frea_Murfrëit_y_Saslonch.jpg) |
| wp08.jpg | Połonina Wetlińska, Bieszczady, Poland | Marcin Szala (Pudelek) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Połonina_Wetlińska_-_Smerek.JPG) |
| wp09.jpg | Playa de Ojos, Fuerteventura, Spain | H. Zell | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) | [Commons](https://commons.wikimedia.org/wiki/File:Playa_de_Ojos_-_Fuerteventura.JPG) |

The Creative Commons licences apply to these image files only, not to the app's source code.

## Original artwork

`zp01.jpg` (Pulse Horizon), `zp02.jpg` (Reactor Dawn) and `zp03.jpg` (Midnight Waves) are original works
created for ZeroPulse TV with `branding/tools/SignatureArt.java`, covered by this repository's licence.

## Trademarks

"ZeroPulse" and the ZeroPulse logo identify this project and are not licensed under the code licence.
Android, Android TV, Google TV, YouTube and Google Play are trademarks of Google LLC. TCL is a trademark of
TCL. Netflix, Prime Video, Disney+, Hulu and other app names and logos shown in the app or in screenshots
belong to their owners; the launcher displays the names and banners that installed apps provide and is not
affiliated with or endorsed by any of them.
