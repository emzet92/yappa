#!/usr/bin/env bash

BASE_URL="http://localhost:8080"

echo "🚀 Creating room..."

CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/room" \
  -H "Content-Type: application/json" \
  -d '{"roomName":"Sprint 42","ownerName":"Mateusz"}')

echo "Create response:"
echo "$CREATE_RESPONSE"

# 🔥 Extract roomId
ROOM_ID=$(echo "$CREATE_RESPONSE" | jq -r '.id')

# 🔥 Extract owner participantId
OWNER_ID=$(echo "$CREATE_RESPONSE" | jq -r '.participants[0].id')

if [ -z "$ROOM_ID" ] || [ "$ROOM_ID" = "null" ]; then
  echo "❌ Failed to extract room id"
  exit 1
fi

if [ -z "$OWNER_ID" ] || [ "$OWNER_ID" = "null" ]; then
  echo "❌ Failed to extract owner participant id"
  exit 1
fi

echo "✅ Room ID: $ROOM_ID"
echo "👤 Owner ID: $OWNER_ID"

echo
echo "➕ Joining room as Andrew..."

JOIN_RESPONSE=$(curl -s -X POST "$BASE_URL/room/$ROOM_ID/join" \
  -H "Content-Type: application/json" \
  -d '{"name":"Andrew"}')

echo "Join response:"
echo "$JOIN_RESPONSE"

# 🔥 Extract Andrew participantId (last participant)
ANDREW_ID=$(echo "$JOIN_RESPONSE" | jq -r '.participants[-1].id')

if [ -z "$ANDREW_ID" ] || [ "$ANDREW_ID" = "null" ]; then
  echo "❌ Failed to extract Andrew participant id"
  exit 1
fi

echo "👤 Andrew ID: $ANDREW_ID"

echo
echo "▶️ Starting voting..."

curl -s -X PUT "$BASE_URL/room/$ROOM_ID" \
  -H "Accept: application/json"

echo
echo "🗳 Owner submits vote 5..."

curl -s -X PUT "$BASE_URL/room/$ROOM_ID/vote" \
  -H "Content-Type: application/json" \
  -d "{
        \"participantId\": \"$OWNER_ID\",
        \"value\": \"5\"
      }"

echo
echo "🗳 Andrew submits vote 8..."

curl -s -X PUT "$BASE_URL/room/$ROOM_ID/vote" \
  -H "Content-Type: application/json" \
  -d "{
        \"participantId\": \"$ANDREW_ID\",
        \"value\": \"8\"
      }"

echo
echo "🎉 Done"