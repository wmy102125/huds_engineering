#!/usr/bin/env bash
# Smoke test for the ADR-013 session endpoints and the admin session operations.
# Requires: curl, python3.
# Usage:  PASSWORD='your-test-password' ./auth-smoke.sh
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
EMAIL="${EMAIL:-admin@example.com}"
PASSWORD="${PASSWORD:?Set PASSWORD, for example: PASSWORD='secret' ./auth-smoke.sh}"

DEVICE_A="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
DEVICE_B="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
JAR_A="$TMP/a.txt"
JAR_B="$TMP/b.txt"

# Prints only the HTTP status code of a curl call.
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

# Prints one top-level field of a JSON document read from stdin.
json_field() { python3 -c 'import sys, json; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

# Fetches a CSRF token into the given cookie jar and prints the token value.
csrf() {
  curl -s -c "$1" -b "$1" "$BASE/api/v1/auth/csrf" > /dev/null
  grep XSRF-TOKEN "$1" | awk '{print $7}'
}

# login <jar> <csrf-token> <device-id> <label> <client-type>  -> prints the HTTP status
login() {
  local body
  body="$(printf '{"email":"%s","password":"%s","deviceId":"%s","deviceLabel":"%s","clientType":"%s"}' \
    "$EMAIL" "$PASSWORD" "$3" "$4" "$5")"
  curl -s -o /dev/null -w '%{http_code}' -b "$1" -c "$1" \
    -H "X-XSRF-TOKEN: $2" -H "Content-Type: application/json" \
    -d "$body" "$BASE/api/v1/auth/login"
}

echo "1) Log in from two devices (A = desktop, B = mobile)"
TOKEN_A="$(csrf "$JAR_A")"
TOKEN_B="$(csrf "$JAR_B")"
echo "   login A: $(login "$JAR_A" "$TOKEN_A" "$DEVICE_A" "Desktop curl" DESKTOP)   (expect 200)"
echo "   login B: $(login "$JAR_B" "$TOKEN_B" "$DEVICE_B" "Mobile curl" MOBILE)    (expect 200)"

echo "2) Both sessions work at the same time"
echo "   /me A: $(status -b "$JAR_A" "$BASE/api/v1/auth/me")   (expect 200)"
echo "   /me B: $(status -b "$JAR_B" "$BASE/api/v1/auth/me")   (expect 200)"

USER_ID="$(curl -s -b "$JAR_A" "$BASE/api/v1/auth/me" | json_field userId)"

echo "3) Admin (device A) lists the sessions of the user"
curl -s -b "$JAR_A" "$BASE/api/v1/admin/users/$USER_ID/sessions" | python3 -m json.tool

echo "4) Admin revokes the session of device B"
REF_B="$(curl -s -b "$JAR_A" "$BASE/api/v1/admin/users/$USER_ID/sessions" \
  | python3 -c 'import sys, json; d = json.load(sys.stdin); print(next(s["sessionRef"] for s in d if s["deviceId"] == sys.argv[1]))' "$DEVICE_B")"
echo "   revoke B: $(status -X DELETE -b "$JAR_A" -H "X-XSRF-TOKEN: $TOKEN_A" "$BASE/api/v1/admin/users/$USER_ID/sessions/$REF_B")   (expect 204)"

echo "5) Device B is signed out, device A is not affected"
echo "   /me B: $(status -b "$JAR_B" "$BASE/api/v1/auth/me")   (expect 401)"
echo "   /me A: $(status -b "$JAR_A" "$BASE/api/v1/auth/me")   (expect 200)"

echo "6) Log out device A"
echo "   logout A: $(status -X POST -b "$JAR_A" -c "$JAR_A" -H "X-XSRF-TOKEN: $TOKEN_A" "$BASE/api/v1/auth/logout")   (expect 200 or 204)"
echo "   /me A: $(status -b "$JAR_A" "$BASE/api/v1/auth/me")   (expect 401)"


echo "7) Password reset revokes every session of the user (reset to the same value)"
if [ "${#PASSWORD}" -lt 12 ]; then
  echo "   skipped: the password must be at least 12 characters for the reset endpoint"
  exit 0
fi
TOKEN_A="$(csrf "$JAR_A")"
TOKEN_B="$(csrf "$JAR_B")"
echo "   login A: $(login "$JAR_A" "$TOKEN_A" "$DEVICE_A" "Desktop curl" DESKTOP)   (expect 200)"
echo "   login B: $(login "$JAR_B" "$TOKEN_B" "$DEVICE_B" "Mobile curl" MOBILE)    (expect 200)"
RESET_BODY="$(printf '{"newPassword":"%s"}' "$PASSWORD")"
echo "   reset:   $(status -X POST -b "$JAR_A" -H "X-XSRF-TOKEN: $TOKEN_A" -H "Content-Type: application/json" -d "$RESET_BODY" "$BASE/api/v1/admin/users/$USER_ID/password")   (expect 204)"
echo "   /me A:   $(status -b "$JAR_A" "$BASE/api/v1/auth/me")   (expect 401, every session was revoked)"
echo "   /me B:   $(status -b "$JAR_B" "$BASE/api/v1/auth/me")   (expect 401)"
TOKEN_A="$(csrf "$JAR_A")"
echo "   login again with the new hash: $(login "$JAR_A" "$TOKEN_A" "$DEVICE_A" "Desktop curl" DESKTOP)   (expect 200)"
echo "   /me A:   $(status -b "$JAR_A" "$BASE/api/v1/auth/me")   (expect 200)"
echo "   logout:  $(status -X POST -b "$JAR_A" -c "$JAR_A" -H "X-XSRF-TOKEN: $TOKEN_A" "$BASE/api/v1/auth/logout")   (expect 204)"