package com.example.myapplication;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Remplissage commun des cartes du tableau de bord (Admin et Observation),
 * à partir de l'état PowerSystem (serveur réel ou démonstration).
 */
public final class AffichageDashboard {

    private AffichageDashboard() { }

    /** Remplit toutes les cartes d'état. */
    public static void rafraichir(AppCompatActivity act) {
        // ---- Carte source d'alimentation ----
        TextView tvSource = act.findViewById(R.id.tvSource);
        String source = PowerSystem.getSourceActuelle();
        if (source == null) {
            tvSource.setText("—");
            tvSource.setTextColor(ContextCompat.getColor(act, R.color.couleur_texte_secondaire));
        } else {
            tvSource.setText(PowerSystem.sourceLibelle(source));
            int couleur;
            switch (source) {
                case "LIGNE_FIXE":         couleur = R.color.couleur_succes; break;
                case "ONDULEUR":           couleur = R.color.couleur_accent; break;
                case "GROUPE_ELECTROGENE": couleur = R.color.couleur_principale; break;
                default:                   couleur = R.color.couleur_texte; break;
            }
            tvSource.setTextColor(ContextCompat.getColor(act, couleur));
        }

        TextView tvSourceDepuis = act.findViewById(R.id.tvSourceDepuis);
        if (source == null) {
            tvSourceDepuis.setVisibility(View.GONE);
        } else {
            tvSourceDepuis.setVisibility(View.VISIBLE);
            String depuis = PowerSystem.getSourceDepuis();
            Long age = PowerSystem.getAgeSourceSecondes();
            StringBuilder sb = new StringBuilder(depuis == null ? "" : "Depuis " + depuis);
            if (age != null) {
                if (sb.length() > 0) sb.append(" — ");
                sb.append("il y a ").append(dureeLisible(age));
            }
            tvSourceDepuis.setText(sb);
        }

        // Bascule automatique (visible seulement si ONDULEUR)
        TextView tvBascule = act.findViewById(R.id.tvBascule);
        Long avant = PowerSystem.getSecondesAvantBascule();
        if ("ONDULEUR".equals(source) && avant != null) {
            tvBascule.setVisibility(View.VISIBLE);
            if (avant > 0) {
                tvBascule.setText(act.getString(R.string.bascule_dans, dureeLisible(avant)));
            } else {
                tvBascule.setText(R.string.bascule_depassee);
            }
        } else {
            tvBascule.setVisibility(View.GONE);
        }

        // ---- Carte ligne fixe JIRAMA ----
        TextView tvLigne = act.findViewById(R.id.tvLigneStatus);
        Boolean presente = PowerSystem.isLigneFixePresente();
        if (presente == null) {
            tvLigne.setText(R.string.etat_ligne_inconnue);
            tvLigne.setTextColor(ContextCompat.getColor(act, R.color.couleur_texte_secondaire));
        } else if (presente) {
            tvLigne.setText(R.string.etat_ligne_ok);
            tvLigne.setTextColor(ContextCompat.getColor(act, R.color.couleur_succes));
        } else {
            tvLigne.setText(R.string.etat_ligne_coupure);
            tvLigne.setTextColor(ContextCompat.getColor(act, R.color.couleur_erreur));
        }

        TextView tvLigneDepuis = act.findViewById(R.id.tvLigneDepuis);
        String ligneDepuis = PowerSystem.getLigneFixeDepuis();
        if (presente == null || ligneDepuis == null) {
            tvLigneDepuis.setVisibility(View.GONE);
        } else {
            tvLigneDepuis.setVisibility(View.VISIBLE);
            tvLigneDepuis.setText(act.getString(R.string.depuis_format, ligneDepuis));
        }

        // Prochaine coupure : donnée de démonstration seulement
        TextView tvCoupure = act.findViewById(R.id.tvProchaineCoupure);
        if (PowerSystem.isModeServeur()) {
            tvCoupure.setVisibility(View.GONE);
        } else {
            tvCoupure.setVisibility(View.VISIBLE);
            tvCoupure.setText(act.getString(R.string.prochaine_coupure_format,
                    PowerSystem.PROCHAINE_COUPOURE_DEMO));
        }

        // ---- Carte carburant ----
        TextView tvCarburant = act.findViewById(R.id.tvCarburant);
        Double carburant = PowerSystem.getCarburantPourcent();
        if (carburant == null) {
            tvCarburant.setText("—");
            tvCarburant.setTextColor(ContextCompat.getColor(act, R.color.couleur_texte_secondaire));
        } else {
            tvCarburant.setText(act.getString(R.string.carburant_pourcent, Math.round(carburant)));
            int couleur = carburant < 20
                    ? R.color.couleur_erreur : R.color.couleur_succes;
            tvCarburant.setTextColor(ContextCompat.getColor(act, couleur));
        }
        TextView tvCarburantDepuis = act.findViewById(R.id.tvCarburantDepuis);
        String carburantAt = PowerSystem.getCarburantMesureAt();
        if (carburantAt == null) {
            tvCarburantDepuis.setVisibility(View.GONE);
        } else {
            tvCarburantDepuis.setVisibility(View.VISIBLE);
            tvCarburantDepuis.setText(act.getString(R.string.mesure_le, carburantAt));
        }

        // ---- Source des données (réelle / démo / erreur) ----
        ((TextView) act.findViewById(R.id.tvDernierEnvoi)).setText(PowerSystem.labelSource(act));

        // ---- Listes ----
        remplirListe(act, R.id.llAlertes, R.id.tvAlertesVide,
                PowerSystem.alertes(), R.string.aucune_alerte);
        remplirListe(act, R.id.llHistorique, -1,
                PowerSystem.historiqueAffiche(), -1);
        remplirListe(act, R.id.llTimeline, R.id.tvTimelineVide,
                PowerSystem.timelineSources(), R.string.aucune_donnee);
    }

    /** Remplit un conteneur avec item_log.xml ; la vue « vide » dédiée du layout
     *  (si idVide != -1) est montrée quand la liste est vide. */
    private static void remplirListe(AppCompatActivity act, int idConteneur, int idVide,
                                     java.util.List<String> lignes, int texteVide) {
        LinearLayout conteneur = act.findViewById(idConteneur);
        conteneur.removeAllViews();
        if (lignes.isEmpty()) {
            if (idVide != -1) act.findViewById(idVide).setVisibility(View.VISIBLE);
            return;
        }
        if (idVide != -1) act.findViewById(idVide).setVisibility(View.GONE);
        for (String ligne : lignes) {
            TextView tv = (TextView) act.getLayoutInflater()
                    .inflate(R.layout.item_log, conteneur, false);
            tv.setText(ligne);
            conteneur.addView(tv);
        }
    }

    /** Durée lisible : "45 s" sous 2 minutes, sinon "X min" (évite "0 min"). */
    private static String dureeLisible(long secondes) {
        if (secondes < 120) return secondes + " s";
        return (secondes / 60) + " min";
    }
}
