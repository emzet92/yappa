#!/usr/bin/env bash

BASE_URL="http://localhost:8080"

echo "🚀 Creating room..."

CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/room" \
  -H "Content-Type: application/json" \
  -d '{"roomName":"Sprint 42","ownerName":"Mateusz"}')

echo "Create response:"
echo "$CREATE_RESPONSE"

# 🔥 Wyciągamy roomId
ROOM_ID=$(echo "$CREATE_RESPONSE" | jq -r '.id')

# 🔥 Wyciągamy participantId z pierwszego uczestnika
PARTICIPANT_ID=$(echo "$CREATE_RESPONSE" | jq -r '.participants[0].id')

if [ -z "$ROOM_ID" ] || [ "$ROOM_ID" = "null" ]; then
  echo "❌ Failed to extract room id"
  exit 1
fi

if [ -z "$PARTICIPANT_ID" ] || [ "$PARTICIPANT_ID" = "null" ]; then
  echo "❌ Failed to extract participant id"
  exit 1
fi

echo "✅ Room ID: $ROOM_ID"
echo "👤 Participant ID: $PARTICIPANT_ID"

echo "▶️ Starting voting..."

curl -s -X PUT "$BASE_URL/room/$ROOM_ID" \
  -H "Accept: application/json"

echo
echo "🗳 Submitting vote..."

curl -s -X PUT "$BASE_URL/room/$ROOM_ID/vote" \
  -H "Content-Type: application/json" \
  -d "{
        \"participantId\": \"$PARTICIPANT_ID\",
        \"value\": \"5\"
      }"

echo
echo "🎉 Done"