# Lobbykin pateikimas į RuneLite Plugin Hub

Paruošta 2026-10-30. Kandidatas 0.9.4-rc1, pagrindas 0.9.3.
Tai šaltinio paketas, o ne jau įdiegtas ar patvirtintas Plugin Hub leidimas.

## Kas paruošta

- Visa įskiepio src/main ir src/test struktūra bei Gradle wrapper.
- Vienoda versija build.gradle, runelite-plugin.properties ir HTTP User-Agent.
- build=standard; bytecode taikinys Java 11, vietiniam Gradle paleidimui JDK 17.
- BSD-2-Clause licencija ir Gradle Apache-2.0 tekstas bei kilmės pastabos.
- Katalogo icon.png: originali 48x48 Lobbykin ikona, nepakeistas talismanas.
- Angliškas README, PRIVACY, SECURITY, pateikimo PR juodraštis.
- GitHub Actions build/test ir offline paketo patikra.
- Manifesto generatorius, reikalaujantis tikro repo URL ir pilno commit SHA.

## 1. Vieša GitHub saugykla

Vieša saugykla jau sukurta ir šaltinis įkeltas:
https://github.com/juskyg/lobbykin-runelite
Pradinis 66 failų importas: 7b06e8c25fb2656e3b013ca6e12c23354972f32e.
Toliau esantys įkėlimo punktai palikti tik kaip šaknies struktūros paaiškinimas;
pakartotinai kurti saugyklos ar kelti ZIP nereikia. Tęsti nuo build ir gyvų testų.

Į naujo repo šaknį įkelti šio aplanko TURINĮ: build.gradle, src, gradle, gradlew,
runelite-plugin.properties ir kitus failus. Neįkelti viso papildomo aplanko kaip
vienintelio repo katalogo. `.github/workflows/build.yml` taip pat turi patekti.
Išlaikyti vykdomą gradlew bitą (`git update-index --chmod=+x gradlew`).

Galima pradėti nuo https://github.com/runelite/example-plugin/generate arba
tuščio repo https://github.com/new. Nepalikti ExamplePlugin šalia Lobbykin.
Išsaugoti pateiktas licencijas. Pagal šį paketą projekto kodas leidžiamas BSD-2-Clause.

## 2. Kompiliavimas ir bandymai — prieš siunčiant PR

Pirma turi praeiti GitHub Actions **Build and test**. Šio paruošimo aplinkoje
Gradle negalėjo būti atsisiųstas, taip pat trūksta pilno JDK, todėl kompiliacija ir Java testai
NAUJAM kandidatui nepatvirtinti. Ankstesnių versijų testai nėra šios versijos įrodymas.

JDK17 komandos repo šaknyje:

```sh
python3 tools/preflight.py
./gradlew --no-daemon clean test jar
```

Windows antrajai komandai naudoti `gradlew.bat --no-daemon clean test jar`.
Pateikimo dieną patikrinti dabartinį
https://github.com/runelite/plugin-hub/blob/master/runelite.version ir testuoti
su ta versija per `-PruneLiteVersion=...`. Patikrinta reikšmė 2026-10-30: 1.13.4.
Galima papildoma patikra su `-PruneLiteVersion=latest.release`.
Hub `standard` build pakeičia vietinį build.gradle, todėl mūsų CI yra papildoma,
o ne oficialų Hub build pakeičianti patikra.

Gyvai patikrinti bent hostą ir kitą narį, o raidų detektoriams — visus reklamojamus
CoX/ToB/ToA režimus arba aiškiai susiaurinti aprašomą palaikymą:

| Scenarijus | Būtinas rezultatas |
| --- | --- |
| Browser connect, cancel, reconnect, revoke | Tik patvirtintas ryšys; atšauktas nesugrįžta |
| Pasirinktiniai leidimai | Išjungti leidimai nesuteikia atitinkamų veiksmų |
| Ready dviejuose klientuose | Atitinkama būsena ir rosteryje, ir svetainėje |
| Hop į sesijos world | Tik aiškus paspaudimas; žaidimo ribojimai galioja |
| Lobby ir tikra pradžia | Laukimas neįskaitomas kaip vykstantis raidas |
| Mirtis | Didėja mirtys; raidas neužbaigiamas |
| Išėjimas / nutrūkęs ryšys | Nėra sėkmingo užbaigimo |
| Du nariai baigia tą patį raidą | Vienas grupės užbaigimas |
| Pakartotas / pavėluotas pranešimas | Nėra papildomo užbaigimo; naujas raidas nesugadinamas |
| Du raidai iš eilės | Du grupės užbaigimai |
| Restart / disable plugin | Lokalūs prisijungimo duomenys nelaikomi diske |

Kelių žaidėjų sujungimą vykdo serveris: šis Java repo jo neįrodo. Fiksuoti bandymo
datą, raidą/režimą, klientų skaičių ir rezultatą be slaptų kodų. Taip pat prieš
viešą naudojimą užbaigti paslaugos duomenų saugojimo bei ištrynimo informaciją:
PRIVACY.md sąmoningai neišgalvoja serverio saugojimo terminų.

## 3. Plugin Hub pateikimas

Kai aukščiau esantys vartai praeiti:

1. Užfiksuoti galutinį plugin repo commit. Nusikopijuoti pilną 40 ženklų SHA;
   jei po bandymo keičiama versija, pakartoti build galutiniam commit.
2. Forkinti https://github.com/runelite/plugin-hub į savo paskyrą.
3. Forke sukurti atskirą šaką, pvz. `add-lobbykin`.
4. Sukurti **plugins/lobbykin** failą be plėtinio. Jame turi būti tik
   `repository=` su realia viešo plugin repo HTTPS .git nuoroda ir
   `commit=` su tikru galutinio commit SHA. Ne ZIP ar JAR nuoroda.
5. Galima sugeneruoti failo tekstą `tools/make_hub_manifest.py`, nurodant
   `--repository` ir `--commit` tikras reikšmes. Generatorius viešumo ar commit
   egzistavimo nepatvirtina — tai patikrinti GitHub.
6. Commitinti tą vieną manifesto failą forke ir atverti Pull Request į
   **runelite/plugin-hub**. Pritaikyti PLUGIN_HUB_PR.md juodraštį, įrašyti realią
   CI bei gyvų bandymų informaciją. Nepalikti neįvykdytų testų kaip atliktų.
7. Stebėti oficialų Hub CI ir peržiūros pastabas. Taisant plugin kodą, keičiasi
   jo SHA: tame pačiame PR atnaujinti `commit=`. Nekurti naujo PR kiekvienai pataisai.
8. Laukti peržiūros ir merge. Patvirtinimo terminas ir rezultatas negarantuojami.

Oficialios instrukcijos:
https://github.com/runelite/plugin-hub/blob/master/README.md
Peržiūros ribos:
https://github.com/runelite/runelite/wiki/Plugin-Hub-Review

## 4. Po priėmimo

Patikrinti, kad Lobbykin randamas Plugin Hub ir įsidiegia švariame RuneLite
kliente. Tada naudotojams užtenka Plugin Hub → Lobbykin → Install; mūsų BAT ir
atskiro JDK įdiegimo galutiniam vartotojui nebereikia. Kodo saugykla lieka vieša,
Issues naudojami klaidoms, atnaujinimai teikiami nauju `commit=` per Hub PR.

## Kas šiuo darbu neatlikta

Viešas repo sukurtas, šaltinis ir CI workflow įkelti. Oficialus Hub PR dar
nepateiktas, gamybinė svetainė ir DB nekeistos. Tai nėra RuneLite/Jagex patvirtinimas.
