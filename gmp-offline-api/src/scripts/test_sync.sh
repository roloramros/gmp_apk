#!/usr/bin/env bash
#
# test_sync.sh — flujo de prueba end-to-end para GET /sync
#
# Uso:
#   chmod +x test_sync.sh
#   ./test_sync.sh
#
# Requiere: curl, python3 (usado solo para parsear JSON, no se instala nada nuevo)
# Corre contra http://localhost:3002 por defecto (cambia BASE_URL si hace falta).

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3002}"

ADMIN_COMPANY_ID=1
ADMIN_PHONE="5551234"
ADMIN_PASSWORD="test5678"

WORKER_PHONE="5559999"
WORKER_PASSWORD="worker123"

# --- helpers ---------------------------------------------------------------

# Extrae un campo de nivel superior de un JSON leído por stdin.
# Uso: echo "$json" | jsonfield token
jsonfield() {
  python3 -c "import sys, json; print(json.load(sys.stdin).get('$1', ''))"
}

pretty() {
  python3 -m json.tool
}

section() {
  echo
  echo "=============================================================="
  echo "== $1"
  echo "=============================================================="
}

# --- 0. Salud del servidor --------------------------------------------------

section "0. Health check"
curl -sf "$BASE_URL/health" | pretty

# --- 1. Login como admin ----------------------------------------------------

section "1. Login admin (company_id=$ADMIN_COMPANY_ID, phone=$ADMIN_PHONE)"
LOGIN_RESPONSE=$(curl -sf -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"company_id\": $ADMIN_COMPANY_ID, \"phone\": \"$ADMIN_PHONE\", \"password\": \"$ADMIN_PASSWORD\"}")

echo "$LOGIN_RESPONSE" | pretty
ADMIN_TOKEN=$(echo "$LOGIN_RESPONSE" | jsonfield token)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "ERROR: no se obtuvo token de admin. Revisa credenciales." >&2
  exit 1
fi

# --- 2. Sync completo (since vacío = full dump) -----------------------------

section "2. GET /sync?since= (full dump, admin)"
SYNC_RESPONSE=$(curl -sf "$BASE_URL/sync?since=" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "$SYNC_RESPONSE" | pretty
CURSOR=$(echo "$SYNC_RESPONSE" | jsonfield cursor)
echo ">> cursor guardado: $CURSOR"

# --- 3. Sync incremental sin cambios (debe venir todo vacío) ----------------

section "3. GET /sync?since=$CURSOR (nada debería haber cambiado)"
curl -sf "$BASE_URL/sync?since=$CURSOR" -H "Authorization: Bearer $ADMIN_TOKEN" | pretty

# --- 4. Crear un job de prueba (comando con X-Command-Id) -------------------

section "4. POST /jobs (crear job de prueba)"
JOB_UUID=$(python3 -c "import uuid; print(uuid.uuid4())")
COMMAND_ID=$(python3 -c "import uuid; print(uuid.uuid4())")

echo ">> job uuid: $JOB_UUID"
echo ">> command id: $COMMAND_ID"

JOB_RESPONSE=$(curl -sf -X POST "$BASE_URL/jobs" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Command-Id: $COMMAND_ID" \
  -d "{\"uuid\": \"$JOB_UUID\", \"title\": \"Job de prueba test_sync.sh\", \"address\": \"Calle Falsa 123\"}")

echo "$JOB_RESPONSE" | pretty

# --- 5. Reintentar el mismo comando (debe devolver la misma respuesta, sin duplicar) --

section "5. Reintento del mismo POST /jobs con el mismo X-Command-Id (prueba de idempotencia)"
curl -sf -X POST "$BASE_URL/jobs" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Command-Id: $COMMAND_ID" \
  -d "{\"uuid\": \"$JOB_UUID\", \"title\": \"Job de prueba test_sync.sh\", \"address\": \"Calle Falsa 123\"}" | pretty

# --- 6. Sync incremental: el job nuevo debe aparecer con el cursor anterior --

section "6. GET /sync?since=$CURSOR (el job nuevo debería aparecer en jobs.upserts)"
SYNC_RESPONSE_2=$(curl -sf "$BASE_URL/sync?since=$CURSOR" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "$SYNC_RESPONSE_2" | pretty
NEW_CURSOR=$(echo "$SYNC_RESPONSE_2" | jsonfield cursor)
echo ">> nuevo cursor: $NEW_CURSOR"

# --- 7. Login como trabajador (prueba de visibilidad por rol) ---------------

section "7. Login trabajador (phone=$WORKER_PHONE)"
WORKER_LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"company_id\": $ADMIN_COMPANY_ID, \"phone\": \"$WORKER_PHONE\", \"password\": \"$WORKER_PASSWORD\"}")
echo "$WORKER_LOGIN_RESPONSE" | pretty

WORKER_TOKEN=$(echo "$WORKER_LOGIN_RESPONSE" | jsonfield token 2>/dev/null || true)

if [ -z "$WORKER_TOKEN" ]; then
  echo
  echo "NOTA: el trabajador Juan Pérez está desactivado (active=false), así que"
  echo "el login falla como se espera. Para probar la visibilidad por rol en /sync"
  echo "necesitas reactivarlo primero. No hay endpoint API para reactivar (solo"
  echo "existe /staff/:uuid/deactivate), así que hazlo directo en la base de datos:"
  echo
  echo "  sudo -u postgres psql -d gmp_offline_db -c \\"
  echo "    \"UPDATE users SET active = true WHERE phone = '$WORKER_PHONE' AND company_id = $ADMIN_COMPANY_ID;\""
  echo
  echo "y vuelve a correr este script."
else
  section "7b. GET /sync?since= (full dump, trabajador) — debería ver solo sus jobs asignados"
  curl -sf "$BASE_URL/sync?since=" -H "Authorization: Bearer $WORKER_TOKEN" | pretty
fi

section "Listo"
echo "Revisa arriba: el paso 6 debe mostrar el job creado en jobs.upserts,"
echo "el paso 5 debe devolver EXACTAMENTE la misma respuesta que el paso 4"
echo "(mismo job, no un error ni un duplicado), y el paso 3 debe venir vacío."
