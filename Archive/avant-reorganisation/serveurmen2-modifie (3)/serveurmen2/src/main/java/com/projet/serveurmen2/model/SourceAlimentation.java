package com.projet.serveurmen2.model;

/**
 * Sources d'alimentation possibles de l'appareil important.
 *
 * - LIGNE_FIXE         : reseau JIRAMA (source principale, broche Source 1 du relais)
 * - ONDULEUR           : ligne temporaire (alimente l'appareil des le debut de la coupure,
 *                        sur batterie, sans reservoir de carburant)
 * - GROUPE_ELECTROGENE : ligne de secours (apres ecoulement du delai de bascule sans
 *                        retour de la ligne fixe ; broche Source 2 du relais ; c'est
 *                        cette source qui possede le reservoir de carburant)
 */
public enum SourceAlimentation {
    LIGNE_FIXE,
    ONDULEUR,
    GROUPE_ELECTROGENE
}
