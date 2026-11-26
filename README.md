# Nettoyeur de doublons (Android)

Application Android qui repère les fichiers en double dans les dossiers que vous choisissez.

## Fonctionnalités (version 1.0)
- Ajout de un ou plusieurs dossiers via le sélecteur Android (l'application n'accède qu'à ces dossiers).
- Recherche dans les sous-dossiers.
- Détection des vrais doublons par contenu (empreinte SHA-256), même si les noms diffèrent.
- Sélection automatique des copies, en gardant toujours un fichier par groupe.
- Suppression seulement après confirmation.

Non inclus pour l'instant : réorganisation automatique par dossiers et renommage par lot.

## Compiler l'APK (méthode la plus simple)
1. Installez **Android Studio** (gratuit) sur votre PC.
2. Ouvrez Android Studio, puis **Open** et choisissez le dossier `NettoyeurDoublons`.
3. Attendez la fin de la synchronisation Gradle.
4. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.
6. Copiez-le sur votre téléphone et installez-le (autorisez l'installation depuis une source inconnue le temps de l'essai).

## Préparer la publication sur le Play Store
- Changez `applicationId` dans `app/build.gradle.kts` (par exemple `dz.votrenom.nettoyeur`) : cet identifiant est définitif une fois publié.
- Créez une clé de signature et générez un fichier `.aab` (Build > Generate Signed App Bundle).
- Créez votre compte Google Play Console, puis téléversez le `.aab`.
- Publiez une politique de confidentialité sur une page web publique.

## Remarque
Ce code n'a pas été compilé ni testé sur un appareil. Si Android Studio signale une erreur, copiez le message et envoyez-le moi.
