# Tester sans jamais toucher aux données d'un client

Toutes les vérifications qui écrivent quelque chose passent par **un seul
compte de test**, propriétaire de sa propre boutique, créé par
`scripts/compte-test.sh`. Aucune boutique réelle n'est utilisée, jamais.

```bash
./scripts/compte-test.sh creer          # crée le compte, la boutique et le fichier d'identifiants
./scripts/compte-test.sh etat           # compte, boutique, nombre de documents
./scripts/compte-test.sh reinitialiser  # vide les documents, PDF, clients, articles, compteurs
./scripts/compte-test.sh supprimer      # tout enlève, y compris les PDF du stockage
```

## Les règles

1. **Un compte, pas plusieurs.** `agent-test@factureapp.ma` est le seul compte
   de test. Ne pas en créer d'autres : les comptes jetables laissent des PDF
   orphelins quand un test meurt avant son nettoyage.
2. **Jamais administrateur plateforme.** La console voit toutes les boutiques
   et les e-mails. Un test de la console nomme un administrateur pour la durée
   du test, puis le retire.
3. **Identifiants hors du dépôt.** `~/.config/factureapp/test.env`, `chmod 600`,
   avec la clé service_role dans `~/.config/factureapp/supabase-service.env`.
   Le dépôt est public : rien de tout cela n'y entre.
4. **La RLS fait le reste.** Ce compte est un propriétaire de boutique
   ordinaire : il ne peut lire que sa boutique, donc un test ne peut pas
   atteindre un client même par erreur.
5. **Nettoyer après un test qui écrit.** `reinitialiser` avant de rendre la
   boutique au prochain test.

## Lancer

```bash
cd webapp
pnpm test                 # Vitest, aucun réseau, aucune base
pnpm check                # svelte-check
node scripts/e2e-live.mjs # vrai navigateur (Brave sur le port 9223), vraie base
```

`e2e-live.mjs` lit les identifiants dans `test.env` par défaut ; `E2E_EMAIL` et
`E2E_PASS` les remplacent au besoin. Il crée une facture dans la boutique de
test, vérifie les totaux, l'émet et télécharge l'archive (`%PDF-`).
