package com.example.myapplication;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * État du système énergétique, aligné sur le serveur Spring Boot "serveurmen2".
 *
 * Le serveur expose (par ESP32, deviceId donné) :
 *   - la SOURCE d'alimentation actuelle : LIGNE_FIXE (JIRAMA) / ONDULEUR /
 *     GROUPE_ELECTROGENE (bascule AUTOMATIQUE après un délai sans retour JIRAMA) ;
 *   - l'état de la ligne fixe JIRAMA (présente / absente) ;
 *   - le niveau de carburant du groupe électrogène ;
 *   - les alertes actives (SECOURS_INOPERATIONNEL, CARBURANT_BAS...) ;
 *   - l'historique de la ligne fixe et la timeline des sources.
 *
 * Deux modes :
 *   1. MODE SERVEUR : les données réelles appliquées par applyServeur*()
 *      (appelé par ApiClient après chaque interrogation du serveur) ;
 *   2. MODE DÉMO : quand le serveur est éteint — valeurs locales + commande
 *      de démonstration du groupe (l'écran de commande reste jouable).
 */
public class PowerSystem {

    /** Valeur de démonstration (le serveur ne gère pas encore les coupures programmées). */
    public static final String PROCHAINE_COUPOURE_DEMO = "15/10/2026 de 08h00 à 12h00";

    // ------------------------------------------------------------------
    // ÉTAT RÉEL (serveur) — null = non connu
    // ------------------------------------------------------------------
    private static String sourceActuelle = null;      // LIGNE_FIXE / ONDULEUR / GROUPE_ELECTROGENE
    private static String motifSource = null;
    private static String sourceDepuis = null;        // horodatage formaté
    private static Long ageSourceSecondes = null;
    private static Long secondesAvantBascule = null;  // renseigné si ONDULEUR
    private static Boolean ligneFixePresente = null;
    private static String ligneFixeDepuis = null;
    private static Double carburantPourcent = null;
    private static String carburantMesureAt = null;
    private static final List<String> ALERTES = new ArrayList<>();
    private static final List<String> HISTORIQUE_LIGNE = new ArrayList<>();
    private static final List<String> TIMELINE_SOURCES = new ArrayList<>();

    /** Dernier message d'erreur réseau (affiché sous les statuts). */
    private static String erreurServeur = null;

    // ------------------------------------------------------------------
    // ÉTAT DE DÉMONSTRATION (serveur éteint)
    // ------------------------------------------------------------------
    private static boolean groupeDemoEnMarche = false;
    private static final List<String> HISTORIQUE_DEMO = new ArrayList<>();

    static {
        horodatageDemo("Ligne JIRAMA sous tension");
        horodatageDemo("Coupure JIRAMA annoncée : 15/10/2026 (08h00 - 12h00)");
        horodatageDemo("Groupe électrogène à l'arrêt");
    }

    // ------------------------------------------------------------------
    // LECTURES (utilisées par les écrans)
    // ------------------------------------------------------------------
    public static boolean isModeServeur() { return sourceActuelle != null; }

    public static String getSourceActuelle() { return sourceActuelle; }
    public static String getMotifSource() { return motifSource; }
    public static String getSourceDepuis() { return sourceDepuis; }
    public static Long getAgeSourceSecondes() { return ageSourceSecondes; }
    public static Long getSecondesAvantBascule() { return secondesAvantBascule; }
    public static Boolean isLigneFixePresente() { return ligneFixePresente; }
    public static String getLigneFixeDepuis() { return ligneFixeDepuis; }
    public static Double getCarburantPourcent() { return carburantPourcent; }
    public static String getCarburantMesureAt() { return carburantMesureAt; }
    public static String getErreurServeur() { return erreurServeur; }

    /** État de démo du groupe (écran de commande quand le serveur est éteint). */
    public static boolean isGroupeDemoEnMarche() { return groupeDemoEnMarche; }

    public static List<String> alertes() { return new ArrayList<>(ALERTES); }
    public static List<String> historiqueLigne() { return new ArrayList<>(HISTORIQUE_LIGNE); }
    public static List<String> timelineSources() { return new ArrayList<>(TIMELINE_SOURCES); }
    public static List<String> historiqueDemo() { return new ArrayList<>(HISTORIQUE_DEMO); }

    /** Historique affiché : serveur si connecté, sinon démo. */
    public static List<String> historiqueAffiche() {
        return isModeServeur() ? historiqueLigne() : historiqueDemo();
    }

    // ------------------------------------------------------------------
    // APPLICATION DES DONNÉES SERVEUR (appelé par ApiClient)
    // ------------------------------------------------------------------
    public static void applyServeurAlimentation(String source, String motif,
                                                String depuis, Long ageSecondes,
                                                Long basculeSecondes,
                                                Boolean lignePresente, String ligneDepuis,
                                                Double carburant, String carburantAt,
                                                List<String> alertes) {
        sourceActuelle = source;
        motifSource = motif;
        sourceDepuis = depuis;
        ageSourceSecondes = ageSecondes;
        secondesAvantBascule = basculeSecondes;
        ligneFixePresente = lignePresente;
        ligneFixeDepuis = ligneDepuis;
        carburantPourcent = carburant;
        carburantMesureAt = carburantAt;
        erreurServeur = null;
        if (alertes != null) {
            ALERTES.clear();
            ALERTES.addAll(alertes);
        }
    }

    public static void setHistoriqueLigne(List<String> lignes) {
        if (lignes == null) return;
        HISTORIQUE_LIGNE.clear();
        HISTORIQUE_LIGNE.addAll(lignes);
    }

    public static void setTimelineSources(List<String> lignes) {
        if (lignes == null) return;
        TIMELINE_SOURCES.clear();
        TIMELINE_SOURCES.addAll(lignes);
    }

    public static void setErreurServeur(String message) {
        erreurServeur = message;
    }

    // ------------------------------------------------------------------
    // COMMANDE DE DÉMONSTRATION (écran de commande, serveur éteint)
    // ------------------------------------------------------------------
    public static void commanderGroupeDemo(boolean demarrer, String par) {
        groupeDemoEnMarche = demarrer;
        horodatageDemo((demarrer ? "Groupe électrogène DÉMARRÉ" : "Groupe électrogène ARRÊTÉ")
                + " par " + par);
    }

    // ------------------------------------------------------------------
    // LIBELLÉS & OUTILS
    // ------------------------------------------------------------------
    /** Traduction d'un code de source serveur en libellé lisible. */
    public static String sourceLibelle(String code) {
        if (code == null) return "—";
        switch (code) {
            case "LIGNE_FIXE":         return "Ligne fixe (JIRAMA)";
            case "ONDULEUR":           return "Onduleur";
            case "GROUPE_ELECTROGENE": return "Groupe électrogène";
            default:                   return code;
        }
    }

    /** Libellé "source des données" affiché sous les statuts. */
    public static String labelSource(android.content.Context c) {
        if (isModeServeur()) {
            String base = c.getString(R.string.source_serveur);
            if (sourceDepuis != null) base += " " + sourceDepuis;
            return base;
        }
        if (erreurServeur != null) {
            return c.getString(R.string.source_demo_erreur, erreurServeur);
        }
        return c.getString(R.string.source_demo);
    }

    /**
     * Convertit une date ISO-8601 du serveur ("2026-10-09T06:12:00.123Z")
     * en heure locale lisible ("09/10/2026 14:32"). Retourne l'entrée brute
     * si le format est inattendu.
     */
    public static String dateLocale(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            String s = iso.trim().replace("Z", "+0000");
            // retire la fraction de seconde si présente ("2026-10-09T06:12:00.123+0000")
            s = s.replaceAll("\\.\\d+", "");
            // "+00:00" → "+0000" (format attendu par SimpleDateFormat)
            if (s.length() >= 6 && s.charAt(s.length() - 3) == ':') {
                s = s.substring(0, s.length() - 3) + s.substring(s.length() - 2);
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(s);
            if (d == null) return iso;
            SimpleDateFormat aff = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH);
            return aff.format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    private static void horodatageDemo(String evenement) {
        String ts = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH).format(new Date());
        HISTORIQUE_DEMO.add(0, ts + " — " + evenement);
    }
}
