const form = document.getElementById('chat-form');
const chat = document.getElementById('chat');
const textarea = document.getElementById('query');

const pathTestEcho = '/test_echo';
const pathTestSleep = '/test_sleep';
const pathQuery = '/query';

textarea.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.ctrlKey) {
        e.preventDefault();
        form.requestSubmit(form.querySelector('button[data-path="' + pathQuery + '"]'));
    }
});

function escapeHtml(text) {
    return text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\n/g,'<br>');
}

function appendMessage(role, text) {
    const msg = document.createElement('div');
    msg.className = 'message ' + role;
    msg.innerHTML = '<span>' + escapeHtml(text) + '</span>';
    chat.appendChild(msg);
    chat.scrollTop = chat.scrollHeight;
    return msg;
}

function appendLoading() {
    const msg = document.createElement('div');
    msg.className = 'message assistant';
    msg.innerHTML = '<span><i class="spinner"></i></span>';
    chat.appendChild(msg);
    chat.scrollTop = chat.scrollHeight;
    return msg;
}

form.addEventListener('submit', async function(e) {
    e.preventDefault();
    const text = textarea.value.trim();
    if (!text) return;

    appendMessage('user', text);
    textarea.value = '';
    textarea.focus();

    const loader = appendLoading();
    const path = (e.submitter && e.submitter.dataset.path) ? e.submitter.dataset.path : '/';
    const controls = path === pathTestSleep ? [...form.querySelectorAll('button, textarea')] : [];
    controls.forEach(el => el.disabled = true);

    try {
        const response = await fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain' },
            body: text
        });
        const reply = await response.text();
        loader.innerHTML = '<span>' + escapeHtml(reply) + '</span>';
    } catch (err) {
        loader.innerHTML = '<span>' + escapeHtml('Error: ' + err.message) + '</span>';
    } finally {
        controls.forEach(el => el.disabled = false);
        textarea.focus();
    }
    chat.scrollTop = chat.scrollHeight;
});
