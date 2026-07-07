#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# insert-ambassador-user-config.sh
#
# Inserts the Ambassador UserConfig document into the userTypeConfig MongoDB
# collection via the live POST /user-config API endpoint.
#
# Usage:
#   ./scripts/insert-ambassador-user-config.sh [BASE_URL]
#
# Defaults to http://localhost:8080 if BASE_URL is not provided.
# Override for staging/production:
#   ./scripts/insert-ambassador-user-config.sh https://api.izinga.co.za
#
# The endpoint requires no authentication on current security config, but if
# that changes add: -H "Authorization: Bearer <token>"
# ---------------------------------------------------------------------------

BASE_URL="${1:-http://localhost:8080}"
ENDPOINT="${BASE_URL}/user-config"

PAYLOAD='{
  "name": "ambassador",
  "label": "iZinga Ambassador",
  "userRole": "AMBASSADOR",
  "mandatoryFields": [
    {
      "name": "idNumber",
      "label": "ID Number",
      "dataType": "STRING"
    },
    {
      "name": "bankAccountNumber",
      "label": "Bank Account Number",
      "dataType": "STRING"
    },
    {
      "name": "bankName",
      "label": "Bank Name",
      "dataType": "STRING"
    },
    {
      "name": "bankAccountType",
      "label": "Bank Account Type",
      "dataType": "STRING"
    }
  ],
  "optionalFields": []
}'

echo "Posting Ambassador UserConfig to ${ENDPOINT} ..."
echo ""

HTTP_STATUS=$(curl -s -o /tmp/user_config_response.json -w "%{http_code}" \
  -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d "${PAYLOAD}")

echo "HTTP status: ${HTTP_STATUS}"
echo "Response:"
cat /tmp/user_config_response.json
echo ""

if [ "${HTTP_STATUS}" -eq 200 ] || [ "${HTTP_STATUS}" -eq 201 ]; then
  echo "Ambassador UserConfig inserted successfully."
else
  echo "Insert failed. Check the server logs."
  exit 1
fi
