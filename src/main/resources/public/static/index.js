function setParticipantCookie(roomId, participantId, name) {
  const value = JSON.stringify({ roomId, participantId, name });
  document.cookie = `yappa_participant=${encodeURIComponent(value)}; path=/; max-age=86400; SameSite=Lax`;
}

document.getElementById("createRoomForm").addEventListener("submit", async function (e) {
  e.preventDefault();

  const roomName = document.getElementById("roomName").value.trim();
  const nickname = document.getElementById("ownerName").value.trim();

  if (!roomName || !nickname) {
    alert("Both fields are required");
    return;
  }

  try {
    const response = await fetch("/room", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ roomName, ownerName: nickname })
    });

    if (!response.ok) {
      const text = await response.text();
      alert("Failed to create room: " + text);
      return;
    }

    const data = await response.json();

    // Owner is automatically added as the first participant
    const me = data.participants.find(p => p.name === nickname) || data.participants[0];
    if (me) {
      setParticipantCookie(data.id, me.id, me.name);
    }

    window.location.href = `/game?id=${data.id}`;

  } catch (error) {
    console.error("Error creating room:", error);
    alert("Something went wrong");
  }
});
