#!/bin/sh

cd /app

# Overwrite runtime config
cat <<EOF > ./src/assets/config/config.json
{
  "apiUrl": "${API_URL:-http://localhost:8080}"
}
EOF

# Start Angular dev server
exec npx ng serve --host 0.0.0.0 --port 4200



