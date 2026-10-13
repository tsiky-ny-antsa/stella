# Application Android mobile — Java

Ce dossier contient le projet Android actif Stella, package `mg.stella.mobile`, conservé avant la dernière réorganisation. L'ancien prototype Android est sauvegardé séparément dans `Archive/avant-reorganisation/Interface/`.

## Compiler localement

Prérequis : JDK 21 et Android SDK correspondant au `compileSdk` du module.

```bash
chmod +x ./gradlew
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

APK de débogage attendu : `app/build/outputs/apk/debug/app-debug.apk`.

## Compilation automatique

Le workflow [Android Build](../.github/workflows/android-build.yml) s'exécute sur les modifications de `mobile/**` poussées sur `main`, les pull requests ciblant `main`, et peut être lancé manuellement. Après réussite, il publie l'APK comme artefact GitHub Actions pendant 14 jours.

## Configuration

- Le package Android et le namespace sont `mg.stella.mobile`.
- En debug, l'émulateur utilise `http://10.0.2.2:8000/api/v1` pour joindre FastAPI sur le PC hôte.
- Le build release utilise un domaine fictif de remplacement ; configurer une vraie URL HTTPS avant livraison.
- Ne pas inclure de secrets dans l'APK.

Le contrat API cible est décrit dans [API_FASTAPI_MOBILE.md](../Doc/API_FASTAPI_MOBILE.md). Le workflow vérifie la compilation et les tests unitaires, mais ne prouve pas à lui seul l'intégration avec un serveur FastAPI en fonctionnement.
