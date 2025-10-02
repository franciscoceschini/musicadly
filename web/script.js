const statusBox = document.getElementById('status');
const feedbackBox = document.getElementById('feedback');
const boardBody = document.getElementById('board-body');
const trackList = document.getElementById('track-list');
const gameArea = document.getElementById('game-area');
const artistForm = document.getElementById('artist-form');
const artistInput = document.getElementById('artist');
const restartButton = document.getElementById('restart-btn');
const startButton = document.getElementById('start-btn');
const guessForm = document.getElementById('guess-form');
const guessInput = document.getElementById('guess');
const guessButton = guessForm.querySelector('button[type="submit"]');
const suggestionsBox = document.getElementById('track-suggestions');

let gameId = null;
let availableTracks = [];
let currentSuggestions = [];
let activeSuggestionIndex = -1;

function setActiveSuggestion(index) {
    const buttons = suggestionsBox.querySelectorAll('button');
    buttons.forEach(button => button.classList.remove('active'));

    if (index >= 0 && index < buttons.length) {
        activeSuggestionIndex = index;
        const activeButton = buttons[index];
        activeButton.classList.add('active');
        activeButton.scrollIntoView({block: 'nearest'});
    } else {
        activeSuggestionIndex = -1;
    }
}

function showMessage(element, message, type = 'info') {
    if (!message) {
        element.classList.add('hidden');
        return;
    }
    element.textContent = message;
    element.classList.remove('hidden');
    element.classList.toggle('error', type === 'error');
}

function renderBoard(board) {
    boardBody.innerHTML = '';
    for (let i = 0; i < board.name.length; i++) {
        const row = document.createElement('tr');
        ['name', 'album', 'trackNo', 'length', 'ft', 'explicit'].forEach(key => {
            const cell = document.createElement('td');
            cell.textContent = board[key][i];
            row.appendChild(cell);
        });
        boardBody.appendChild(row);
    }
}

function populateTracks(tracks) {
    availableTracks = Array.isArray(tracks) ? tracks.slice() : [];
    trackList.innerHTML = '';
    availableTracks.forEach(track => {
        const span = document.createElement('span');
        span.textContent = track;
        trackList.appendChild(span);
    });
    renderSuggestions('');
}

function renderSuggestions(query) {
    suggestionsBox.innerHTML = '';
    const term = query.trim().toLowerCase();

    if (!term) {
        suggestionsBox.classList.add('hidden');
        currentSuggestions = [];
        activeSuggestionIndex = -1;
        return;
    }

    const matches = availableTracks.filter(track => track.toLowerCase().includes(term)).slice(0, 8);
    currentSuggestions = matches;
    activeSuggestionIndex = -1;

    if (!matches.length) {
        const empty = document.createElement('div');
        empty.className = 'suggestions-empty';
        empty.textContent = 'No se encontraron coincidencias.';
        suggestionsBox.appendChild(empty);
        suggestionsBox.classList.remove('hidden');
        return;
    }

    matches.forEach(track => {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = track;
        button.addEventListener('mousedown', (event) => {
            event.preventDefault();
            guessInput.value = track;
            suggestionsBox.classList.add('hidden');
            currentSuggestions = [];
            activeSuggestionIndex = -1;
        });
        suggestionsBox.appendChild(button);
    });

    suggestionsBox.classList.remove('hidden');
}

function lockArtistInput(name) {
    artistInput.value = name;
    artistInput.disabled = true;
    artistInput.classList.add('locked');
    startButton.disabled = true;
    restartButton.disabled = false;
}

function unlockArtistInput() {
    artistInput.disabled = false;
    artistInput.classList.remove('locked');
    startButton.disabled = false;
    restartButton.disabled = true;
    artistInput.focus();
}

function resetGameState() {
    gameId = null;
    availableTracks = [];
    boardBody.innerHTML = '';
    trackList.innerHTML = '';
    renderSuggestions('');
    showMessage(statusBox, '', 'info');
    showMessage(feedbackBox, '', 'info');
    gameArea.classList.add('hidden');
    guessForm.reset();
    guessInput.disabled = false;
    guessButton.disabled = false;
}

artistForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    showMessage(statusBox, 'Iniciando juego...', 'info');
    const artist = artistInput.value.trim();

    try {
        const response = await fetch('/api/game', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({artist})
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'No se pudo iniciar el juego');
        }

        gameId = data.gameId;
        renderBoard(data.board);
        populateTracks(data.possibleTracks);
        showMessage(statusBox, '', 'info');
        showMessage(feedbackBox, '', 'info');
        gameArea.classList.remove('hidden');
        lockArtistInput(data.artistName);
        guessInput.disabled = false;
        guessButton.disabled = false;
        guessInput.focus();
    } catch (error) {
        console.error(error);
        showMessage(statusBox, error.message, 'error');
    }
});

guessForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!gameId) {
        showMessage(feedbackBox, 'Primero inicia un juego.', 'error');
        return;
    }

    const guess = guessInput.value.trim();
    if (!guess) {
        showMessage(feedbackBox, 'Escribe el nombre de la canción.', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/game/${gameId}/guess`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({guess})
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'No se pudo procesar el intento');
        }

        renderBoard(data.board);
        const feedbackType = data.win ? 'info' : (data.over ? 'error' : 'info');
        showMessage(feedbackBox, `${data.message} ${data.feedback}`.trim(), feedbackType);
        guessForm.reset();
        renderSuggestions('');
        if (data.over) {
            guessInput.disabled = true;
            guessButton.disabled = true;
        } else {
            guessInput.focus();
        }

        if (data.over) {
            gameId = null;
            restartButton.focus();
        }
    } catch (error) {
        showMessage(feedbackBox, error.message, 'error');
    }
});

restartButton.addEventListener('click', () => {
    resetGameState();
    unlockArtistInput();
    artistInput.value = '';
});

guessInput.addEventListener('input', (event) => {
    renderSuggestions(event.target.value);
});

guessInput.addEventListener('focus', (event) => {
    renderSuggestions(event.target.value);
});

guessInput.addEventListener('blur', () => {
    setTimeout(() => suggestionsBox.classList.add('hidden'), 120);
});

guessInput.addEventListener('keydown', (event) => {
    if (suggestionsBox.classList.contains('hidden') || !currentSuggestions.length) {
        return;
    }

    if (event.key === 'ArrowDown') {
        event.preventDefault();
        const nextIndex = activeSuggestionIndex < currentSuggestions.length - 1 ? activeSuggestionIndex + 1 : 0;
        setActiveSuggestion(nextIndex);
    } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        if (activeSuggestionIndex === -1) {
            setActiveSuggestion(currentSuggestions.length - 1);
        } else {
            const prevIndex = activeSuggestionIndex > 0 ? activeSuggestionIndex - 1 : currentSuggestions.length - 1;
            setActiveSuggestion(prevIndex);
        }
    } else if (event.key === 'Enter') {
        if (activeSuggestionIndex >= 0) {
            event.preventDefault();
            guessInput.value = currentSuggestions[activeSuggestionIndex];
            suggestionsBox.classList.add('hidden');
            currentSuggestions = [];
            activeSuggestionIndex = -1;
        }
    } else if (event.key === 'Escape') {
        suggestionsBox.classList.add('hidden');
        currentSuggestions = [];
        activeSuggestionIndex = -1;
    }
});
