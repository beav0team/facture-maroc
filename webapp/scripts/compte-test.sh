#!/usr/bin/env bash
# Compte de test de l'agent — un seul, jamais celui d'un client.
#
#   ./scripts/compte-test.sh creer
#   ./scripts/compte-test.sh etat
#   ./scripts/compte-test.sh reinitialiser
#   ./scripts/compte-test.sh supprimer
#
# Ce compte est un propriétaire de boutique ordinaire : il ne peut rien lire en
# dehors de sa propre boutique (RLS). Ne jamais le nommer administrateur
# plateforme : la console voit toutes les boutiques et les e-mails des clients.
#
# Les identifiants vivent dans ~/.config/factureapp/test.env (chmod 600), hors
# du dépôt qui est public. Le script n'affiche jamais le mot de passe.

set -euo pipefail

SERVICE_ENV="${FACTUREAPP_SERVICE_ENV:-$HOME/.config/factureapp/supabase-service.env}"
TEST_ENV="${FACTUREAPP_TEST_ENV:-$HOME/.config/factureapp/test.env}"
EMAIL="${FACTUREAPP_TEST_EMAIL:-agent-test@factureapp.ma}"
NOM_BOUTIQUE="${FACTUREAPP_TEST_SHOP:-Atelier de test (agent)}"
REF="${SUPABASE_PROJECT_REF:-njbfvskjexzlnvenmxml}"
BASE="https://$REF.supabase.co"

if [ ! -f "$SERVICE_ENV" ]; then
  echo "✗ $SERVICE_ENV introuvable (clé service_role)." >&2
  exit 2
fi
# shellcheck disable=SC1090
set -a; . "$SERVICE_ENV"; set +a
: "${SUPABASE_SERVICE_ROLE_KEY:?SUPABASE_SERVICE_ROLE_KEY absente de $SERVICE_ENV}"

entetes=(
  -H "apikey: $SUPABASE_SERVICE_ROLE_KEY"
  -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY"
  -H "Content-Type: application/json"
)

api() { # api METHODE CHEMIN [CORPS] -> corps de la réponse
  local methode="$1" chemin="$2" corps="${3:-}"
  if [ -n "$corps" ]; then
    curl -sS -X "$methode" "${entetes[@]}" -d "$corps" "$BASE$chemin"
  else
    curl -sS -X "$methode" "${entetes[@]}" "$BASE$chemin"
  fi
}

json() { # json EXPRESSION  (lit stdin)
  python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"
}

utilisateur_id() {
  api GET "/auth/v1/admin/users?per_page=200" |
    python3 -c "import sys,json;u=[x['id'] for x in json.load(sys.stdin).get('users',[]) if x['email']=='$EMAIL'];print(u[0] if u else '')"
}

boutique_id() {
  local nom_encode
  nom_encode=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$NOM_BOUTIQUE")
  api GET "/rest/v1/shops?select=id&nom=eq.$nom_encode" | json "d[0]['id'] if d else ''"
}

lire_test_env() {
  [ -f "$TEST_ENV" ] || { echo "✗ $TEST_ENV absent : lancer « creer »." >&2; exit 2; }
  # shellcheck disable=SC1090
  set -a; . "$TEST_ENV"; set +a
}

nettoyer_stockage() { # nettoyer_stockage SHOP_ID
  local shop="$1"
  REF="$REF" SHOP="$shop" KEY="$SUPABASE_SERVICE_ROLE_KEY" python3 - <<'PY'
import json, os, urllib.request
base = f"https://{os.environ['REF']}.supabase.co/storage/v1"
h = {'apikey': os.environ['KEY'], 'Authorization': f"Bearer {os.environ['KEY']}", 'Content-Type': 'application/json'}
def post(chemin, corps):
    r = urllib.request.Request(base + chemin, data=json.dumps(corps).encode(), headers=h, method='POST')
    return json.load(urllib.request.urlopen(r))
objets = [f"{o['name']}" for o in post('/object/list/archives', {'prefix': os.environ['SHOP'] + '/', 'limit': 100})]
if objets:
    prefixes = [f"{os.environ['SHOP']}/{nom}" for nom in objets]
    r = urllib.request.Request(base + '/object/archives', data=json.dumps({'prefixes': prefixes}).encode(), headers=h, method='DELETE')
    urllib.request.urlopen(r)
print(f"  stockage : {len(objets)} PDF supprimés")
PY
}

compter() { # compter TABLE FILTRE -> entier
  local table="$1" filtre="$2"
  curl -sS -I "${entetes[@]}" -H "Prefer: count=exact" "$BASE/rest/v1/$table?select=*&$filtre" |
    tr -d '\r' | awk -F'/' '/content-range/ {print $2}'
}

cmd_creer() {
  local uid shop motdepasse
  uid="$(utilisateur_id)"
  if [ -z "$uid" ]; then
    motdepasse=$(python3 -c 'import secrets;print(secrets.token_urlsafe(24))')
    uid=$(api POST "/auth/v1/admin/users" \
      "{\"email\":\"$EMAIL\",\"password\":\"$motdepasse\",\"email_confirm\":true}" | json "d['id']")
    echo "✓ compte créé : $EMAIL"
  else
    motdepasse=$(python3 -c 'import secrets;print(secrets.token_urlsafe(24))')
    api PUT "/auth/v1/admin/users/$uid" "{\"password\":\"$motdepasse\"}" > /dev/null
    echo "✓ compte existant, mot de passe renouvelé : $EMAIL"
  fi

  shop="$(boutique_id)"
  if [ -z "$shop" ]; then
    # Sans `return=representation`, PostgREST répond 201 sans corps.
    shop=$(curl -sS -X POST "${entetes[@]}" -H "Prefer: return=representation" \
      -d "{\"nom\":\"$NOM_BOUTIQUE\"}" "$BASE/rest/v1/shops" | json "d[0]['id']")
    echo "✓ boutique créée : $NOM_BOUTIQUE"
  else
    echo "✓ boutique existante : $NOM_BOUTIQUE"
  fi

  if [ -z "$(api GET "/rest/v1/memberships?select=user_id&user_id=eq.$uid&shop_id=eq.$shop" | json "d[0]['user_id'] if d else ''")" ]; then
    api POST "/rest/v1/memberships" "{\"user_id\":\"$uid\",\"shop_id\":\"$shop\",\"role\":\"owner\"}" > /dev/null
    echo "✓ appartenance owner ajoutée"
  fi

  umask 077
  cat > "$TEST_ENV" <<EOF
# Compte de test de l'agent — ne pas committer, ne pas nommer administrateur.
SUPABASE_URL="$BASE"
TEST_EMAIL="$EMAIL"
TEST_PASSWORD="$motdepasse"
TEST_SHOP_ID="$shop"
TEST_SHOP_NOM="$NOM_BOUTIQUE"
EOF
  chmod 600 "$TEST_ENV"
  echo "✓ identifiants dans $TEST_ENV (chmod 600)"
  echo "  boutique $shop · $EMAIL · mot de passe non affiché"
}

compter_archives() { # compter_archives SHOP_ID
  local shop="$1" ids
  ids=$(api GET "/rest/v1/documents?select=id&shop_id=eq.$shop" | json "','.join(x['id'] for x in d)")
  if [ -z "$ids" ]; then echo 0; return; fi
  compter archives "document_id=in.($ids)"
}

cmd_etat() {
  lire_test_env
  local uid="${TEST_USER_ID:-}"
  if [ -z "$uid" ]; then
    uid=$(utilisateur_id)
  fi
  echo "compte   : $EMAIL ${uid:+($uid)}"
  echo "boutique : $TEST_SHOP_NOM ($TEST_SHOP_ID)"
  echo "documents: $(compter documents "shop_id=eq.$TEST_SHOP_ID")"
  echo "clients  : $(compter clients "shop_id=eq.$TEST_SHOP_ID")"
  echo "articles : $(compter articles "shop_id=eq.$TEST_SHOP_ID")"
  echo "archives : $(compter_archives "$TEST_SHOP_ID")"
}

cmd_reinitialiser() {
  lire_test_env
  local shop="$TEST_SHOP_ID"
  # Ordre sûr : les mouvements et lignes partent avec le document, puis le
  # catalogue, puis les compteurs (la numérotation repart de 1).
  # Les lignes, paiements et archives partent avec le document (ON DELETE CASCADE).
  api DELETE "/rest/v1/documents?shop_id=eq.$shop" > /dev/null
  api DELETE "/rest/v1/clients?shop_id=eq.$shop" > /dev/null
  api DELETE "/rest/v1/articles?shop_id=eq.$shop" > /dev/null
  api DELETE "/rest/v1/numerotation?shop_id=eq.$shop" > /dev/null
  api DELETE "/rest/v1/subscriptions?shop_id=eq.$shop" > /dev/null
  nettoyer_stockage "$shop"
  echo "✓ boutique de test vidée"
  echo "  documents $(compter documents "shop_id=eq.$shop") · clients $(compter clients "shop_id=eq.$shop") · articles $(compter articles "shop_id=eq.$shop")"
}

cmd_supprimer() {
  lire_test_env
  local uid
  uid=$(utilisateur_id)
  nettoyer_stockage "$TEST_SHOP_ID"
  api DELETE "/rest/v1/shops?id=eq.$TEST_SHOP_ID" > /dev/null
  [ -n "$uid" ] && api DELETE "/auth/v1/admin/users/$uid" > /dev/null
  rm -f "$TEST_ENV"
  echo "✓ compte, boutique, PDF et fichier d'identifiants supprimés"
}

case "${1:-}" in
  creer)         cmd_creer ;;
  etat)          cmd_etat ;;
  reinitialiser) cmd_reinitialiser ;;
  supprimer)     cmd_supprimer ;;
  *)
    echo "usage : $0 {creer|etat|reinitialiser|supprimer}" >&2
    exit 2
    ;;
esac
