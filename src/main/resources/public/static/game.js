/* ---------------- COOKIE HELPERS ---------------- */

function setParticipantCookie(roomId, participantId, name) {
  const value = JSON.stringify({ roomId, participantId, name });
  document.cookie = `yappa_participant=${encodeURIComponent(value)}; path=/; max-age=86400; SameSite=Lax`;
}

function getParticipantCookie(roomId) {
  const match = document.cookie.split(';')
    .map(c => c.trim())
    .find(c => c.startsWith('yappa_participant='));
  if (!match) return null;
  try {
    const data = JSON.parse(decodeURIComponent(match.slice('yappa_participant='.length)));
    return data.roomId === roomId ? data : null;
  } catch {
    return null;
  }
}

let currentParticipant = null;
let currentRoomId = null;

/* ---------------- LOAD ROOM ---------------- */

async function loadRoom(roomId) {
  try {
    const response = await fetch(`/room/${roomId}`);
    if (!response.ok) throw new Error("Room not found");
    const room = await response.json();
    renderRoom(room);
  } catch (err) {
    console.error(err);
    alert("Failed to load room");
  }
}

/* ---------------- VOTE / START VOTING ---------------- */

async function submitVote(value) {
  const res = await fetch(`/room/${currentRoomId}/vote`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ participantId: currentParticipant.participantId, value })
  });
  if (!res.ok) throw new Error("Vote failed");
  return res.json();
}

async function startVoting() {
  const res = await fetch(`/room/${currentRoomId}`, { method: "PUT" });
  if (!res.ok) throw new Error("Start voting failed");
  return res.json();
}

async function revealVotes() {
  const res = await fetch(`/room/${currentRoomId}/reveal`, { method: "PUT" });
  if (!res.ok) throw new Error("Reveal failed");
  return res.json();
}

/* ---------------- INIT ---------------- */

document.addEventListener("DOMContentLoaded", async () => {

  const tooltip = document.createElement("div");
  tooltip.className = "custom-tooltip";
  tooltip.textContent = "Not Implemented yet";
  document.body.appendChild(tooltip);

  document.querySelectorAll(".disabled-section").forEach(section => {
    section.addEventListener("mouseenter", () => tooltip.classList.add("visible"));
    section.addEventListener("mouseleave", () => tooltip.classList.remove("visible"));
    section.addEventListener("mousemove", e => {
      tooltip.style.left = e.clientX + 12 + "px";
      tooltip.style.top = e.clientY + 12 + "px";
    });
  });

  const params = new URLSearchParams(window.location.search);
  const roomId = params.get("id");

  if (!roomId) {
    alert("Missing room id in URL");
    return;
  }

  currentParticipant = getParticipantCookie(roomId);
  currentRoomId = roomId;

  if (!currentParticipant) {
    document.getElementById("joinModal").style.display = "grid";
  } else {
    await loadRoom(roomId);
    setInterval(() => loadRoom(roomId), 3000);
  }

  document.getElementById("joinBtn").addEventListener("click", async () => {
    const name = document.getElementById("joinName").value.trim();
    if (!name) return;

    try {
      const res = await fetch(`/room/${roomId}/join`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name })
      });

      if (!res.ok) { alert("Failed to join room"); return; }

      const room = await res.json();
      const me = room.participants.find(p => p.name === name);
      if (me) {
        setParticipantCookie(roomId, me.id, me.name);
        currentParticipant = { participantId: me.id, name: me.name };
      }

      document.getElementById("joinModal").style.display = "none";
      renderRoom(room);
      setInterval(() => loadRoom(roomId), 3000);
    } catch (err) {
      console.error(err);
      alert("Something went wrong");
    }
  });
});

/* ---------------- RENDER ---------------- */

function renderRoom(room) {
  document.querySelector(".room-id").textContent = "Room: " + room.id;
  renderParticipants(room);
  renderVotes(room);
  renderProposed(room);
  renderDeck(room);
  renderControls(room);
}

/* ---------------- DECK ---------------- */

function renderDeck(room) {
  const canVote = room.state === "Voting";
  const myVote = currentParticipant && room.currentRoundResponse?.votes?.[currentParticipant.participantId];

  document.querySelectorAll(".deck-card").forEach(card => {
    const value = card.textContent.trim();
    card.classList.toggle("selected", value === myVote);
    card.classList.toggle("inactive", !canVote);
    card.onclick = canVote ? async () => {
      try {
        renderRoom(await submitVote(value));
      } catch (e) {
        console.error("Vote error:", e);
      }
    } : null;
  });
}

/* ---------------- CONTROLS ---------------- */

function renderControls(room) {
  const panel = document.querySelector(".controls-panel");
  panel.innerHTML = "<h3>Host Controls</h3>";

  const me = room.participants.find(p => p.id === currentParticipant?.participantId);
  if (!me?.isAdmin) return;

  if (room.state === "Waiting" || room.state === "Revealed") {
    const btn = document.createElement("button");
    btn.className = "button primary";
    btn.textContent = "Start Voting";
    btn.onclick = async () => {
      try { renderRoom(await startVoting()); }
      catch (e) { console.error("Start voting error:", e); }
    };
    panel.appendChild(btn);
  } else if (room.state === "Voting") {
    const btn = document.createElement("button");
    btn.className = "button secondary";
    btn.textContent = "Reveal cards";
    btn.onclick = async () => {
      try { renderRoom(await revealVotes()); }
      catch (e) { console.error("Reveal error:", e); }
    };
    panel.appendChild(btn);
  }
}

/* ---------------- PARTICIPANTS ---------------- */

function renderParticipants(room) {
  const usersBar = document.querySelector(".users-bar");
  usersBar.innerHTML = "";

  room.participants.forEach(p => {
    const badge = document.createElement("div");
    badge.className = "user-badge";
    badge.innerHTML = `<div class="user-dot"></div> ${p.name}`;
    usersBar.appendChild(badge);
  });
}

/* ---------------- VOTES ---------------- */

function renderVotes(room) {
  const voteArea = document.querySelector(".cards");
  voteArea.innerHTML = "";

  if (!room.currentRoundResponse) return;

  const votes = room.currentRoundResponse.votes;
  const isRevealed = room.state === "Revealed";

  if (isRevealed) {
    const grouped = {};
    for (const participantId in votes) {
      const value = votes[participantId];
      if (!grouped[value]) grouped[value] = [];
      const participant = room.participants.find(p => p.id === participantId);
      if (participant) grouped[value].push(participant.name);
    }

    Object.keys(grouped).forEach(value => {
      const group = document.createElement("div");
      group.className = "card-group";

      const card = document.createElement("div");
      card.className = "card " + getCardColor(value);
      card.textContent = value;

      const users = document.createElement("div");
      users.className = "card-users";
      grouped[value].forEach(name => {
        const span = document.createElement("span");
        span.textContent = name;
        users.appendChild(span);
      });

      group.appendChild(card);
      group.appendChild(users);
      voteArea.appendChild(group);
    });
  } else {
    Object.keys(votes).forEach(participantId => {
      const participant = room.participants.find(p => p.id === participantId);
      if (!participant) return;

      const group = document.createElement("div");
      group.className = "card-group";

      const card = document.createElement("div");
      card.className = "card green";
      card.textContent = "✓";

      const users = document.createElement("div");
      users.className = "card-users";
      const span = document.createElement("span");
      span.textContent = participant.name;
      users.appendChild(span);

      group.appendChild(card);
      group.appendChild(users);
      voteArea.appendChild(group);
    });
  }
}

/* ---------------- PROPOSED ---------------- */

function renderProposed(room) {
  const proposedEl = document.querySelector(".proposed");

  if (room.state !== "Revealed") {
    proposedEl.textContent = "-";
    return;
  }

  const votes = room.currentRoundResponse?.votes;
  if (!votes || Object.keys(votes).length === 0) {
    proposedEl.textContent = "-";
    return;
  }

  const values = Object.values(votes).map(v => parseInt(v)).filter(v => !isNaN(v));
  if (values.length === 0) {
    proposedEl.textContent = "-";
    return;
  }

  const avg = Math.round(values.reduce((a, b) => a + b, 0) / values.length);
  proposedEl.textContent = avg;
}

/* ---------------- CARD COLOR ---------------- */

function getCardColor(value) {
  const num = parseInt(value);
  if (isNaN(num)) return "green";
  if (num <= 3) return "green";
  if (num <= 8) return "orange";
  return "red";
}
