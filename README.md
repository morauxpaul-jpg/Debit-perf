# Débit Perf

Calcul du débit d'une perfusion (gouttes/min ou ml/h) avec une goutte animée au bon rythme
pour régler la molette. Deux versions :

- App Android (un seul APK) : sur la Galaxy Watch, interface ronde (`src/fr/debitperf/DripView.java`) ;
  sur le téléphone, interface téléphone (`web/index.html`, embarquée dans l'APK, hors ligne).
- Version web installable (iPhone et Android) : dossier `web/` (page, manifeste, service worker, icônes).
  Sur iPhone : ouvrir l'adresse dans Safari → Partager → « Sur l'écran d'accueil ».

## Formules
- volume = poche + ajouts
- ml/h = volume (ml) × 60 / durée (min)
- gouttes/min = ml/h × gouttes par ml / 60
- Perfuseur : 20 gouttes/ml (standard), 60 (micro-goutte), 15 (transfusion), 10.

## Tests du calcul
```
mkdir -p /tmp/ct && javac -encoding UTF-8 -d /tmp/ct src/fr/debitperf/Calc.java test/CalcTest.java && java -cp /tmp/ct CalcTest
```

## Compiler l'APK
Il faut un JDK 17+ et, depuis dl.google.com, build-tools 35.0.1 et platform android-34.
```
./build.sh <dossier SDK Android>
```
La clé de signature `debitperf.keystore` n'est **pas** dans ce dépôt (fichier secret). Gardez-la à part :
sans elle, une mise à jour ne peut pas s'installer par-dessus la version existante.

## Installer sur le téléphone (Android)
Le même APK s'installe sur le téléphone : ouvrir `DebitPerf.apk` depuis les téléchargements,
autoriser « Installer des applis inconnues » pour l'appli qui l'ouvre (Fichiers ou Chrome), puis Installer.

## Installer sur la montre
1. Montre : Paramètres → À propos de la montre → Logiciel → toucher 5 fois « Version du logiciel ».
2. Options de développement : activer « Débogage ADB » et « Débogage via Wi-Fi ».
3. PC (Android SDK Platform-Tools) : `adb pair <ip:port> <code>`, `adb connect <ip:port>`, `adb install DebitPerf.apk`.

Aide au calcul : la prescription et le nombre de gouttes/ml indiqué sur l'emballage du perfuseur font foi.
