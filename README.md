# Live Positie (RuneLite-plugin)

Kleine RuneLite-plugin die jouw positie in het spel (x, y, verdieping) doorgeeft aan de kaart van **OSRS Companion**.

- Luistert alleen op `127.0.0.1:37780`, dus alleen deze computer kan erbij.
- Stuurt niets naar internet en verzamelt niets.
- Spreekt hetzelfde berichtformaat als de kaart in de app: `{"type":"position","x":..,"y":..,"plane":..}`.
- Instances worden gemeld als `instanced: true`; de kaart toont die plek dan niet.

## Het bestand krijgen

GitHub bouwt de plugin vanzelf zodra er iets is gepusht. Het bestand `osrs-live-positie.jar` staat bij **Releases → Live Positie (laatste)** (en bij Actions als artifact).

## Installeren (Windows)

1. Maak deze map aan als hij er nog niet is: `C:\Users\<jouw naam>\.runelite\sideloaded-plugins`
2. Zet `osrs-live-positie.jar` in die map.
3. Start RuneLite met de optie **`--developer-mode`**. Het eenvoudigst: maak een snelkoppeling naar `RuneLite.exe` en zet achter het pad in het veld "Doel" een spatie en `--developer-mode`. Via de RuneLite-launcher kan het ook onder **Configure → Client arguments**.
4. In RuneLite: open de plugin-lijst (moersleutel), zoek **Live Positie** en zet hem aan.
5. Log in op het spel en klik in OSRS Companion bij de kaart op **Live positie**.

## Problemen

- *De plugin staat niet in de lijst:* RuneLite is niet met `--developer-mode` gestart, of het bestand staat in de verkeerde map.
- *De kaart zegt "Niet verbonden":* de plugin staat uit, of een andere plugin gebruikt poort 37780 al (zet RuneRadar uit).
- *Bouwen mislukt:* kijk bij het tabblad **Actions** en stuur de foutmelding.
