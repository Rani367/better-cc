/* chat.js — Functions called from Kotlin via executeJavaScript()
 *
 * VS Code Extension Faithful Port — Timeline Layout
 */

// Will be set from Kotlin before page load completes
var sendToKotlin = null;

// Raw code storage for copy button (avoids HTML entity issues)
var _codeBlocks = {};

// Track timeline items for line positioning
var _timelineCount = 0;

// Configure marked with custom renderer
var renderer = new marked.Renderer();

renderer.code = function(code, language) {
    var langLabel = language || '';
    var highlighted;
    try {
        if (language && hljs.getLanguage(language)) {
            highlighted = hljs.highlight(code, { language: language }).value;
        } else if (code.length < 50000) {
            highlighted = hljs.highlightAuto(code).value;
        } else {
            highlighted = escapeHtml(code);
        }
    } catch (e) {
        highlighted = escapeHtml(code);
    }

    var blockId = 'cb-' + Math.random().toString(36).substr(2, 9);
    _codeBlocks[blockId] = code;

    return '<div class="code-block">' +
        '<div class="code-header">' +
            (langLabel ? '<span class="code-language">' + escapeHtml(langLabel) + '</span>' : '<span class="code-language"></span>') +
            '<button class="copy-button" onclick="copyCode(\'' + blockId + '\', this)">Copy</button>' +
        '</div>' +
        '<pre><code class="hljs">' + highlighted + '</code></pre>' +
    '</div>';
};

marked.setOptions({
    renderer: renderer,
    breaks: true,
    gfm: true
});

function escapeHtml(text) {
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
}

function formatTimestamp() {
    var now = new Date();
    var h = now.getHours().toString().padStart(2, '0');
    var m = now.getMinutes().toString().padStart(2, '0');
    return h + ':' + m;
}

function senderDisplayName(sender) {
    switch (sender) {
        case 'USER': return 'You';
        case 'ASSISTANT': return 'Claude';
        case 'ERROR': return 'Error';
        case 'SYSTEM': return 'System';
        default: return sender;
    }
}

function senderClass(sender) {
    switch (sender) {
        case 'USER': return 'user';
        case 'ASSISTANT': return 'assistant';
        case 'ERROR': return 'error';
        case 'SYSTEM': return 'system';
        default: return 'assistant';
    }
}

/**
 * Update timeline classes on all assistant/error/system messages.
 * First gets timeline-first, last gets timeline-last, etc.
 */
function updateTimelineClasses() {
    var container = document.getElementById('messages');
    var timelineItems = container.querySelectorAll(
        '.message-assistant, .message-error, .message-system, .thinking-indicator'
    );

    for (var i = 0; i < timelineItems.length; i++) {
        var item = timelineItems[i];
        item.classList.remove('timeline-first', 'timeline-last');
        if (i === 0) item.classList.add('timeline-first');
        if (i === timelineItems.length - 1) item.classList.add('timeline-last');
    }
}

/**
 * Add a finalized message (user messages, errors, or pre-rendered assistant messages).
 */
function addMessage(id, sender, content) {
    showEmptyState(false);
    var container = document.getElementById('messages');
    var cls = senderClass(sender);

    var div = document.createElement('div');
    div.id = 'msg-' + id;

    if (sender === 'USER') {
        // User message: flat bubble, left-aligned
        div.className = 'message message-user';

        var rewindHtml =
            '<button class="rewind-btn" title="Rewind to this point" ' +
                'onclick="showRewindMenu(\'' + escapeHtml(id) + '\', event)">' +
                rewindIcon() +
            '</button>';

        var isSlashCmd = content.startsWith('/');
        var bubbleClass = 'user-bubble' + (isSlashCmd ? ' slash-command-message' : '');

        div.innerHTML =
            '<div class="message-header">' +
                '<span class="rewind-container">' + rewindHtml + '</span>' +
            '</div>' +
            '<div class="' + bubbleClass + '">' + escapeHtml(content) + '</div>';
    } else if (sender === 'ASSISTANT') {
        // Assistant message: timeline layout, no bubble
        div.className = 'message message-assistant';
        div.innerHTML = '<div class="message-content">' + renderMarkdown(content) + '</div>' +
            createHoverActions(id);
    } else if (sender === 'ERROR') {
        div.className = 'message message-error';
        div.innerHTML = '<div class="message-content">' + escapeHtml(content) + '</div>';
    } else {
        // System message
        div.className = 'message message-system';
        div.innerHTML = '<div class="message-content">' + renderMarkdown(content) + '</div>';
    }

    container.appendChild(div);
    if (sender !== 'USER') {
        updateTimelineClasses();
    }
    if (sender === 'ASSISTANT' || sender === 'SYSTEM') {
        postProcessFilePaths(div.querySelector('.message-content'));
    }
    scrollToBottom();
}

// ── Streaming animation state ────────────────────────────────────
var _streamAnim = {};

/**
 * Update a streaming assistant message with cumulative markdown text.
 * Text is revealed at a constant rate via requestAnimationFrame,
 * decoupling bursty network data from smooth visual display.
 */
function updateStreamingMessage(id, markdown) {
    showEmptyState(false);
    var state = _streamAnim[id];
    if (!state) {
        // Create the message div — timeline layout
        var container = document.getElementById('messages');
        var div = document.createElement('div');
        div.id = 'msg-' + id;
        div.className = 'message message-assistant streaming';
        div.setAttribute('data-raw', '');
        div.innerHTML = '<div class="message-content"></div>';
        container.appendChild(div);
        updateTimelineClasses();

        state = { target: '', revealed: 0, raf: null };
        _streamAnim[id] = state;
    }

    state.target = markdown;

    // Start animation if not already running
    if (!state.raf) {
        state.raf = requestAnimationFrame(function() { _tickStream(id); });
    }
}

/**
 * Animation tick: reveal a few more characters each frame at ~60fps.
 */
function _tickStream(id) {
    var state = _streamAnim[id];
    if (!state) return;

    var gap = state.target.length - state.revealed;
    if (gap <= 0) {
        // Caught up — pause animation until more text arrives
        state.raf = null;
        return;
    }

    // Adaptive speed: ~2-10 chars/frame. Faster when further behind.
    var step = Math.max(2, Math.min(10, Math.ceil(gap / 20)));
    state.revealed = Math.min(state.revealed + step, state.target.length);

    var msg = document.getElementById('msg-' + id);
    if (msg) {
        msg.setAttribute('data-raw', state.target);
        var contentDiv = msg.querySelector('.message-content');
        contentDiv.innerHTML = renderStreamingText(state.target.substring(0, state.revealed));
        scrollToBottom();
    }

    state.raf = requestAnimationFrame(function() { _tickStream(id); });
}

/**
 * Finalize a streaming message — reveal any remaining text, then do
 * full markdown render with syntax highlighting and file path detection.
 */
function finalizeMessage(id) {
    // Stop animation
    var state = _streamAnim[id];
    if (state) {
        if (state.raf) cancelAnimationFrame(state.raf);
        delete _streamAnim[id];
    }

    var msg = document.getElementById('msg-' + id);
    if (!msg) return;
    msg.classList.remove('streaming');

    var rawMarkdown = msg.getAttribute('data-raw');
    if (rawMarkdown) {
        var contentDiv = msg.querySelector('.message-content');
        contentDiv.innerHTML = renderMarkdown(rawMarkdown);
        postProcessFilePaths(contentDiv);
        // Add hover actions if not already present
        if (!msg.querySelector('.message-hover-actions')) {
            msg.insertAdjacentHTML('beforeend', createHoverActions(id));
        }
        updateTimelineClasses();
        scrollToBottom();
    }
}

/**
 * Lightweight streaming render: full markdown but no syntax highlighting.
 */
function renderStreamingText(text) {
    if (!text) return '';
    var origCode = renderer.code;
    renderer.code = _streamingCodeRenderer;
    var result;
    try {
        result = marked.parse(text);
    } catch (e) {
        result = '<p>' + escapeHtml(text) + '</p>';
    }
    renderer.code = origCode;
    return result;
}

function _streamingCodeRenderer(code, language) {
    var langLabel = language || '';
    return '<div class="code-block">' +
        '<div class="code-header">' +
            (langLabel ? '<span class="code-language">' + escapeHtml(langLabel) + '</span>' : '<span class="code-language"></span>') +
            '<span class="copy-button" style="opacity:0.3">Copy</span>' +
        '</div>' +
        '<pre><code>' + escapeHtml(code) + '</code></pre>' +
    '</div>';
}

/**
 * Show or hide the thinking indicator.
 */
function showThinking(visible) {
    var el = document.getElementById('thinking');
    if (el) {
        el.style.display = visible ? 'flex' : 'none';
        if (visible) {
            updateTimelineClasses();
            scrollToBottom();
        }
    }
}

/**
 * Update CSS variables for theme switching.
 */
function setThemeVars(varsJson) {
    var vars = JSON.parse(varsJson);
    var root = document.documentElement;
    for (var key in vars) {
        root.style.setProperty(key, vars[key]);
    }

    // Toggle highlight.js themes
    var lightTheme = document.getElementById('highlight-light-theme');
    var darkTheme = document.getElementById('highlight-dark-theme');
    if (lightTheme && darkTheme) {
        var isDark = vars['--app-primary-background'] && isColorDark(vars['--app-primary-background']);
        // Fall back to legacy var
        if (!isDark && vars['--bg-primary']) {
            isDark = isColorDark(vars['--bg-primary']);
        }
        lightTheme.disabled = isDark;
        darkTheme.disabled = !isDark;
    }
}

/**
 * Clear all messages.
 */
function clearMessages() {
    // Cancel all running animations
    for (var id in _streamAnim) {
        if (_streamAnim[id].raf) cancelAnimationFrame(_streamAnim[id].raf);
    }
    _streamAnim = {};
    _timelineCount = 0;
    _thinkingBlocks = {};

    var container = document.getElementById('messages');
    container.innerHTML = '';
    _codeBlocks = {};
    showThinking(false);
    showEmptyState(true);
}

// ── Tool Use Blocks ─────────────────────────────────────────────

function toolIcon(category) {
    switch (category) {
        case 'READ':
            return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">' +
                '<path d="M3 2h7l3 3v9H3V2z"/><path d="M10 2v3h3"/><path d="M5 8h6M5 10.5h6"/></svg>';
        case 'WRITE':
            return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">' +
                '<path d="M11 2l3 3-8 8H3v-3l8-8z"/><path d="M9 4l3 3"/></svg>';
        case 'BASH':
            return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">' +
                '<rect x="1" y="2" width="14" height="12" rx="2"/><path d="M4 6l3 2.5L4 11"/><path d="M9 11h3"/></svg>';
        default:
            return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">' +
                '<circle cx="8" cy="8" r="5.5"/><path d="M8 5.5v5M5.5 8h5"/></svg>';
    }
}

function addToolBlock(id, name, category, summary, isRunning) {
    showEmptyState(false);
    var container = document.getElementById('messages');

    // Tool blocks are wrapped in a timeline message
    var wrapper = document.createElement('div');
    wrapper.id = 'tool-wrapper-' + id;
    wrapper.className = 'message message-assistant' + (isRunning ? ' dot-progress' : ' dot-success');

    var inner = document.createElement('div');
    inner.id = 'tool-' + id;
    inner.className = 'tool-block';

    var statusHtml = isRunning
        ? '<div class="tool-spinner"></div>'
        : '<span class="tool-check">\u2713</span>';

    inner.innerHTML =
        '<div class="tool-header" onclick="toggleToolBlock(\'' + escapeHtml(id) + '\')">' +
            '<span class="tool-icon">' + toolIcon(category) + '</span>' +
            '<span class="tool-name">' + escapeHtml(name) + '</span>' +
            '<span class="tool-summary">' + escapeHtml(summary) + '</span>' +
            '<span class="tool-status">' + statusHtml + '</span>' +
            '<span class="tool-chevron">\u25B6</span>' +
        '</div>' +
        '<div class="tool-body"><pre></pre></div>';

    wrapper.appendChild(inner);
    container.appendChild(wrapper);
    updateTimelineClasses();
    scrollToBottom();
}

function updateToolBlock(id, summary, isRunning) {
    var el = document.getElementById('tool-' + id);
    if (!el) return;

    var summaryEl = el.querySelector('.tool-summary');
    if (summaryEl) summaryEl.textContent = summary;

    var statusEl = el.querySelector('.tool-status');
    if (statusEl) {
        statusEl.innerHTML = isRunning
            ? '<div class="tool-spinner"></div>'
            : '<span class="tool-check">\u2713</span>';
    }

    // Update dot class on wrapper
    var wrapper = document.getElementById('tool-wrapper-' + id);
    if (wrapper) {
        wrapper.classList.remove('dot-progress', 'dot-success', 'dot-failure');
        wrapper.classList.add(isRunning ? 'dot-progress' : 'dot-success');
    }

    scrollToBottom();
}

function setToolDiffStats(id, additions, deletions, filePath) {
    var el = document.getElementById('tool-' + id);
    if (!el) return;
    var header = el.querySelector('.tool-header');
    if (!header) return;
    // Remove existing diff stats
    var existing = header.querySelector('.tool-diff-stats');
    if (existing) existing.remove();
    var stats = document.createElement('span');
    stats.className = 'tool-diff-stats';
    stats.innerHTML = '<span class="diff-add">+' + additions +
        '</span> <span class="diff-del">-' + deletions + '</span>';
    // Insert before chevron
    var chevron = header.querySelector('.tool-chevron');
    if (chevron) {
        header.insertBefore(stats, chevron);
    } else {
        header.appendChild(stats);
    }
    // Add View Diff button if we have a file path
    if (filePath) {
        var bodyEl = el.querySelector('.tool-body');
        if (bodyEl) {
            var existing2 = bodyEl.querySelector('.tool-diff-btn');
            if (!existing2) {
                var btn = document.createElement('button');
                btn.className = 'tool-diff-btn';
                btn.textContent = 'View Diff';
                btn.onclick = function() {
                    if (sendToKotlin) {
                        sendToKotlin(JSON.stringify({
                            action: 'openFile',
                            path: filePath
                        }));
                    }
                };
                bodyEl.appendChild(btn);
            }
        }
    }
}

function setToolBlockBody(id, text) {
    var el = document.getElementById('tool-' + id);
    if (!el) return;

    // Try to parse as JSON and show as grid
    var bodyEl = el.querySelector('.tool-body');
    try {
        var parsed = JSON.parse(text);
        if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
            var gridHtml = '<div class="tool-body-grid">';
            var keys = Object.keys(parsed);
            for (var i = 0; i < keys.length; i++) {
                var key = keys[i];
                var val = typeof parsed[key] === 'string' ? parsed[key] : JSON.stringify(parsed[key], null, 2);
                gridHtml += '<div class="tool-body-row">' +
                    '<div class="tool-body-row-label">' + escapeHtml(key) + '</div>' +
                    '<div class="tool-body-row-content">' + escapeHtml(val) + '</div>' +
                '</div>';
            }
            gridHtml += '</div>';
            bodyEl.innerHTML = gridHtml;
            return;
        }
    } catch (e) {
        // Not JSON, fall through to plain text
    }

    var pre = bodyEl.querySelector('pre');
    if (pre) pre.textContent = text;
}

function toggleToolBlock(id) {
    var el = document.getElementById('tool-' + id);
    if (!el) return;
    el.classList.toggle('expanded');
}

// ── Permission Cards ─────────────────────────────────────────────

function permissionIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">' +
        '<path d="M8 1L2 4v4c0 3.3 2.6 6.4 6 7 3.4-.6 6-3.7 6-7V4L8 1z"/>' +
        '<path d="M6 8l2 2 4-4" stroke-linecap="round" stroke-linejoin="round"/></svg>';
}

function addPermissionCard(requestId, toolName, category, argumentsSummary, riskLevel) {
    showEmptyState(false);
    var container = document.getElementById('messages');

    // Permission cards are wrapped in a timeline message
    var wrapper = document.createElement('div');
    wrapper.id = 'perm-wrapper-' + requestId;
    wrapper.className = 'message message-assistant dot-warning';

    var div = document.createElement('div');
    div.id = 'perm-' + requestId;
    div.className = 'permission-card permission-pending';

    var riskClass = riskLevel === 'high' ? 'risk-high' : 'risk-normal';
    var riskLabel = riskLevel === 'high' ? 'High Risk' : 'Normal';

    div.innerHTML =
        '<div class="perm-content">' +
            '<div class="perm-header">' +
                '<span class="perm-icon">' + permissionIcon() + '</span>' +
                '<span class="perm-title">Permission Required</span>' +
                '<span class="risk-badge ' + riskClass + '">' + riskLabel + '</span>' +
            '</div>' +
            '<div class="perm-body">' +
                '<div class="perm-tool-name">' +
                    '<span class="tool-icon">' + toolIcon(category) + '</span>' +
                    escapeHtml(toolName) +
                '</div>' +
                (argumentsSummary ? '<pre class="perm-args">' + escapeHtml(argumentsSummary) + '</pre>' : '') +
            '</div>' +
            '<div class="perm-actions">' +
                '<button class="perm-btn focused" onclick="respondPermission(\'' + escapeHtml(requestId) + '\', \'allow\')">' +
                    '<span class="shortcut-num">1</span> Allow' +
                '</button>' +
                '<button class="perm-btn" onclick="respondPermission(\'' + escapeHtml(requestId) + '\', \'allowSession\')">' +
                    '<span class="shortcut-num">2</span> Allow for Session' +
                '</button>' +
                '<button class="perm-btn" onclick="respondPermission(\'' + escapeHtml(requestId) + '\', \'deny\')">' +
                    '<span class="shortcut-num">3</span> Deny' +
                '</button>' +
            '</div>' +
            '<div class="keyboard-hints">Press 1-3 to select</div>' +
        '</div>';

    wrapper.appendChild(div);
    container.appendChild(wrapper);
    updateTimelineClasses();
    scrollToBottom();
}

function respondPermission(requestId, decision) {
    var el = document.getElementById('perm-' + requestId);
    if (!el) return;

    // Disable all buttons immediately
    var buttons = el.querySelectorAll('.perm-btn');
    for (var i = 0; i < buttons.length; i++) {
        buttons[i].disabled = true;
    }

    // Update card state
    el.classList.remove('permission-pending');
    var isAllowed = decision === 'allow' || decision === 'allowSession';
    el.classList.add(isAllowed ? 'permission-allowed' : 'permission-denied');

    // Update dot colour on wrapper
    var wrapper = document.getElementById('perm-wrapper-' + requestId);
    if (wrapper) {
        wrapper.classList.remove('dot-warning', 'dot-progress');
        wrapper.classList.add(isAllowed ? 'dot-success' : 'dot-failure');
    }

    // Replace buttons with status text
    var actionsEl = el.querySelector('.perm-actions');
    var hintsEl = el.querySelector('.keyboard-hints');
    if (hintsEl) hintsEl.remove();
    if (actionsEl) {
        var statusClass = isAllowed ? 'perm-status-allowed' : 'perm-status-denied';
        var statusText = isAllowed ? 'Allowed' : 'Denied';
        if (decision === 'allowSession') statusText = 'Allowed (for session)';
        actionsEl.innerHTML = '<div class="perm-status ' + statusClass + '">' + statusText + '</div>';
    }

    // Update header title
    var titleEl = el.querySelector('.perm-title');
    if (titleEl) {
        titleEl.textContent = isAllowed ? 'Permission Granted' : 'Permission Denied';
    }

    // Notify Kotlin
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({
            action: 'permissionResponse',
            requestId: requestId,
            decision: decision
        }));
    }
}

// ── Helpers ──────────────────────────────────────────────────────

function renderMarkdown(text) {
    if (!text) return '';
    try {
        return marked.parse(text);
    } catch (e) {
        return '<p>' + escapeHtml(text) + '</p>';
    }
}

function scrollToBottom() {
    var container = document.getElementById('messages');
    // Use requestAnimationFrame to ensure DOM has updated
    requestAnimationFrame(function() {
        container.scrollTop = container.scrollHeight;
    });
}

function copyCode(blockId, button) {
    var code = _codeBlocks[blockId];
    if (!code) return;

    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'copy', code: code }));
    } else {
        // Fallback: use clipboard API
        navigator.clipboard.writeText(code).catch(function() {});
    }

    button.textContent = 'Copied!';
    button.classList.add('copied');
    setTimeout(function() {
        button.textContent = 'Copy';
        button.classList.remove('copied');
    }, 2000);
}

/**
 * Detect file-like paths in text nodes and wrap them in clickable spans.
 * Only processes text outside of <code>, <pre>, and <a> tags.
 */
function postProcessFilePaths(element) {
    if (!element) return;

    // File path pattern: sequences like src/foo/bar.kt, ./path/file.py, /abs/path.txt
    var filePathRegex = /(?:^|[\s(["'])(((?:\.{1,2}\/|\/|[a-zA-Z]:\\)?(?:[a-zA-Z0-9_\-]+[\/\\])+[a-zA-Z0-9_\-]+\.[a-zA-Z0-9]+))(?=[\s)"\]',;:.]|$)/g;

    var walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, {
        acceptNode: function(node) {
            var parent = node.parentElement;
            if (!parent) return NodeFilter.FILTER_REJECT;
            var tag = parent.tagName.toLowerCase();
            if (tag === 'code' || tag === 'pre' || tag === 'a' || parent.classList.contains('file-path')) {
                return NodeFilter.FILTER_REJECT;
            }
            return NodeFilter.FILTER_ACCEPT;
        }
    });

    var nodesToProcess = [];
    while (walker.nextNode()) {
        if (filePathRegex.test(walker.currentNode.textContent)) {
            nodesToProcess.push(walker.currentNode);
        }
        filePathRegex.lastIndex = 0;
    }

    nodesToProcess.forEach(function(textNode) {
        var text = textNode.textContent;
        filePathRegex.lastIndex = 0;
        var parts = [];
        var lastIndex = 0;
        var match;

        while ((match = filePathRegex.exec(text)) !== null) {
            var path = match[1];
            var matchStart = match.index + match[0].indexOf(path);

            if (matchStart > lastIndex) {
                parts.push(document.createTextNode(text.substring(lastIndex, matchStart)));
            }

            var span = document.createElement('span');
            span.className = 'file-path';
            span.textContent = path;
            span.onclick = (function(p) {
                return function() { openFile(p); };
            })(path);
            parts.push(span);

            lastIndex = matchStart + path.length;
        }

        if (parts.length > 0) {
            if (lastIndex < text.length) {
                parts.push(document.createTextNode(text.substring(lastIndex)));
            }
            var parent = textNode.parentNode;
            parts.forEach(function(part) {
                parent.insertBefore(part, textNode);
            });
            parent.removeChild(textNode);
        }
    });
}

function openFile(path) {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'openFile', path: path }));
    }
}

function isColorDark(color) {
    // Simple heuristic: parse hex color and check luminance
    var hex = color.replace('#', '');
    if (hex.length === 3) {
        hex = hex[0]+hex[0] + hex[1]+hex[1] + hex[2]+hex[2];
    }
    if (hex.length !== 6) return false;
    var r = parseInt(hex.substr(0, 2), 16);
    var g = parseInt(hex.substr(2, 2), 16);
    var b = parseInt(hex.substr(4, 2), 16);
    var luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    return luminance < 0.5;
}

// ── Error Banner ─────────────────────────────────────────────────

function showErrorBanner(message) {
    dismissErrorBanner();
    var banner = document.createElement('div');
    banner.id = 'error-banner';
    banner.className = 'error-banner';
    banner.innerHTML =
        '<div class="error-message">' + escapeHtml(message) + '</div>' +
        '<button class="error-dismiss" onclick="dismissErrorBanner()">\u00D7</button>';
    // Insert at top of body
    document.body.insertBefore(banner, document.body.firstChild);
}

function dismissErrorBanner() {
    var existing = document.getElementById('error-banner');
    if (existing) existing.remove();
}

// ── File Attachments & Drop Overlay ──────────────────────────────

var _attachedFiles = [];

function showDropOverlay(visible) {
    var existing = document.getElementById('drop-overlay');
    if (visible) {
        if (!existing) {
            var overlay = document.createElement('div');
            overlay.id = 'drop-overlay';
            overlay.className = 'drop-overlay';
            overlay.innerHTML = '<div class="drop-overlay-label">' +
                'Drop files to add</div>';
            document.body.appendChild(overlay);
        }
    } else {
        if (existing) existing.remove();
    }
}

function addAttachedFile(name, path) {
    if (_attachedFiles.some(function(f) { return f.path === path; })) {
        return;
    }
    _attachedFiles.push({ name: name, path: path });
    _renderAttachmentChips();
}

function removeAttachedFile(path) {
    _attachedFiles = _attachedFiles.filter(function(f) {
        return f.path !== path;
    });
    _renderAttachmentChips();
}

function clearAttachedFiles() {
    _attachedFiles = [];
    _renderAttachmentChips();
}

function getAttachedFiles() {
    return _attachedFiles.slice();
}

function _renderAttachmentChips() {
    var container = document.getElementById('attachment-chips');
    if (!container) return;
    container.innerHTML = '';
    if (_attachedFiles.length === 0) {
        container.style.display = 'none';
        return;
    }
    container.style.display = 'flex';
    for (var i = 0; i < _attachedFiles.length; i++) {
        var file = _attachedFiles[i];
        var chip = document.createElement('div');
        chip.className = 'attachment-chip';
        chip.innerHTML = '<span class="attachment-name">' +
            escapeHtml(file.name) + '</span>' +
            '<button class="attachment-remove" ' +
            'onclick="removeAttachedFile(\'' +
            escapeHtml(file.path).replace(/'/g, "\\'") +
            '\')">\u00d7</button>';
        container.appendChild(chip);
    }
}

function _setupDragAndDrop() {
    var dragCounter = 0;
    document.addEventListener('dragenter', function(e) {
        e.preventDefault();
        dragCounter++;
        if (dragCounter === 1) showDropOverlay(true);
    });
    document.addEventListener('dragleave', function(e) {
        e.preventDefault();
        dragCounter--;
        if (dragCounter <= 0) {
            dragCounter = 0;
            showDropOverlay(false);
        }
    });
    document.addEventListener('dragover', function(e) {
        e.preventDefault();
    });
    document.addEventListener('drop', function(e) {
        e.preventDefault();
        dragCounter = 0;
        showDropOverlay(false);
        var files = e.dataTransfer.files;
        for (var i = 0; i < files.length; i++) {
            var file = files[i];
            if (sendToKotlin) {
                sendToKotlin(JSON.stringify({
                    action: 'attachFile',
                    name: file.name,
                    path: file.name,
                    type: file.type
                }));
            }
        }
    });
}

function _setupClipboardPaste() {
    var input = document.getElementById('message-input');
    if (!input) return;
    input.addEventListener('paste', function(e) {
        var items = (e.clipboardData || {}).items || [];
        for (var i = 0; i < items.length; i++) {
            if (items[i].type.indexOf('image') !== -1) {
                e.preventDefault();
                var file = items[i].getAsFile();
                if (file && sendToKotlin) {
                    sendToKotlin(JSON.stringify({
                        action: 'attachFile',
                        name: file.name || 'pasted-image.png',
                        path: 'clipboard-image',
                        type: file.type
                    }));
                }
                return;
            }
        }
    });
}

// ── Rewind / Checkpoint ──────────────────────────────────────────

function rewindIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" ' +
        'width="14" height="14">' +
        '<circle cx="8" cy="8" r="6"/>' +
        '<path d="M8 4.5v4l2.5 1.5"/>' +
        '<path d="M3.5 3L5.5 5 3.5 7" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';
}

var _activeRewindMenu = null;

function showRewindMenu(messageId, event) {
    event.stopPropagation();
    dismissRewindMenu();

    var btn = event.currentTarget;
    var rect = btn.getBoundingClientRect();

    var menu = document.createElement('div');
    menu.className = 'rewind-menu';
    menu.innerHTML =
        '<div class="rewind-option" onclick="triggerRewind(\'' + escapeHtml(messageId) + '\', \'codeAndConversation\')">' +
            '<span class="rewind-option-icon">' + rewindCodeConvoIcon() + '</span>' +
            'Restore code and conversation' +
        '</div>' +
        '<div class="rewind-option" onclick="triggerRewind(\'' + escapeHtml(messageId) + '\', \'conversationOnly\')">' +
            '<span class="rewind-option-icon">' + rewindConvoIcon() + '</span>' +
            'Restore conversation only' +
        '</div>' +
        '<div class="rewind-option" onclick="triggerRewind(\'' + escapeHtml(messageId) + '\', \'codeOnly\')">' +
            '<span class="rewind-option-icon">' + rewindCodeIcon() + '</span>' +
            'Restore code only' +
        '</div>' +
        '<div class="rewind-option" onclick="triggerRewind(\'' + escapeHtml(messageId) + '\', \'summarize\')">' +
            '<span class="rewind-option-icon">' + summarizeIcon() + '</span>' +
            'Summarize from here' +
        '</div>' +
        '<div class="rewind-divider"></div>' +
        '<div class="rewind-option rewind-dismiss" onclick="dismissRewindMenu()">' +
            'Never mind' +
        '</div>';

    document.body.appendChild(menu);
    _activeRewindMenu = menu;

    // Position below the button
    var menuRect = menu.getBoundingClientRect();
    var top = rect.bottom + 4;
    var left = rect.left;

    // Clamp to viewport
    if (left + menuRect.width > window.innerWidth - 8) {
        left = window.innerWidth - menuRect.width - 8;
    }
    if (top + menuRect.height > window.innerHeight - 8) {
        top = rect.top - menuRect.height - 4;
    }

    menu.style.top = top + 'px';
    menu.style.left = left + 'px';

    // Dismiss on click outside
    setTimeout(function() {
        document.addEventListener('click', _onDocClickRewind);
    }, 0);
}

function _onDocClickRewind(e) {
    if (_activeRewindMenu && !_activeRewindMenu.contains(e.target)) {
        dismissRewindMenu();
    }
}

function dismissRewindMenu() {
    if (_activeRewindMenu) {
        _activeRewindMenu.remove();
        _activeRewindMenu = null;
    }
    document.removeEventListener('click', _onDocClickRewind);
}

function triggerRewind(messageId, action) {
    dismissRewindMenu();
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({
            action: 'rewind',
            messageId: messageId,
            rewindAction: action
        }));
    }
}

function rewindCodeConvoIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14">' +
        '<path d="M2 4l3-2v4L2 4z" fill="currentColor" stroke="none"/>' +
        '<path d="M6 4l3-2v4L6 4z" fill="currentColor" stroke="none"/>' +
        '<rect x="9" y="2" width="5" height="12" rx="1"/>' +
    '</svg>';
}

function rewindConvoIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14">' +
        '<path d="M3 4h10M3 8h7M3 12h5"/>' +
    '</svg>';
}

function rewindCodeIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14">' +
        '<path d="M5 3L2 8l3 5M11 3l3 5-3 5"/>' +
    '</svg>';
}

function summarizeIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14">' +
        '<rect x="2" y="2" width="12" height="12" rx="2"/>' +
        '<path d="M5 5h6M5 8h4M5 11h2"/>' +
    '</svg>';
}

// ── Thinking Blocks ──────────────────────────────────────────────

var _thinkingBlocks = {};

/**
 * Add a collapsible thinking block in the timeline.
 */
function addThinkingBlock(id) {
    showEmptyState(false);
    var container = document.getElementById('messages');

    var wrapper = document.createElement('div');
    wrapper.id = 'thinking-wrapper-' + id;
    wrapper.className = 'message message-assistant dot-progress';

    var block = document.createElement('div');
    block.id = 'thinking-block-' + id;
    block.className = 'thinking-block';
    block.innerHTML =
        '<div class="thinking-header" onclick="toggleThinkingBlock(\'' + escapeHtml(id) + '\')">' +
            '<span class="thinking-chevron">\u25B6</span>' +
            '<span class="thinking-label">Thinking\u2026</span>' +
        '</div>' +
        '<div class="thinking-content"></div>';

    wrapper.appendChild(block);
    container.appendChild(wrapper);
    _thinkingBlocks[id] = { text: '' };

    // Hide the 3-dot thinking indicator when we have a real thinking block
    showThinking(false);

    updateTimelineClasses();
    scrollToBottom();
}

/**
 * Append text to a thinking block's content.
 */
function updateThinkingBlock(id, text) {
    var state = _thinkingBlocks[id];
    if (!state) return;
    state.text = text;

    var block = document.getElementById('thinking-block-' + id);
    if (!block) return;

    var contentEl = block.querySelector('.thinking-content');
    if (contentEl) {
        contentEl.textContent = text;
    }

    // Update label with preview
    var labelEl = block.querySelector('.thinking-label');
    if (labelEl) {
        var preview = text.substring(0, 80).replace(/\n/g, ' ');
        if (text.length > 80) preview += '\u2026';
        labelEl.textContent = 'Thinking: ' + preview;
    }

    scrollToBottom();
}

/**
 * Finalize a thinking block — change dot from blinking to green.
 */
function _generateThinkingSummary(text) {
    if (!text || text.trim().length === 0) return '';
    // Extract first meaningful sentence as summary
    var cleaned = text.replace(/^[\s\n]+/, '').replace(/\n/g, ' ');
    // Find first sentence boundary
    var match = cleaned.match(/^(.{10,120}?[.!?])\s/);
    if (match) return match[1];
    // Fall back to first N chars
    if (cleaned.length > 100) return cleaned.substring(0, 100) + '\u2026';
    return cleaned;
}

function finalizeThinkingBlock(id) {
    var state = _thinkingBlocks[id];
    if (!state) return;

    var wrapper = document.getElementById('thinking-wrapper-' + id);
    if (wrapper) {
        wrapper.classList.remove('dot-progress');
        wrapper.classList.add('dot-success');
    }

    var block = document.getElementById('thinking-block-' + id);
    if (block) {
        var labelEl = block.querySelector('.thinking-label');
        if (labelEl) {
            var wordCount = state.text.split(/\s+/).filter(
                function(w) { return w.length > 0; }
            ).length;
            var summary = _generateThinkingSummary(state.text);
            if (summary) {
                labelEl.textContent = 'Thought for ' + wordCount +
                    ' words \u2014 ' + summary;
            } else {
                labelEl.textContent = 'Thought for ' + wordCount +
                    ' words';
            }
        }
    }

    updateTimelineClasses();
}

/**
 * Toggle expand/collapse of a thinking block.
 */
function toggleThinkingBlock(id) {
    var block = document.getElementById('thinking-block-' + id);
    if (!block) return;
    block.classList.toggle('expanded');
}

// ── Message Hover Actions ────────────────────────────────────────

function copyIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">' +
        '<rect x="5" y="5" width="8" height="8" rx="1"/>' +
        '<path d="M3 11V3h8"/></svg>';
}

function retryIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">' +
        '<path d="M3 8a5 5 0 019.54-2M13 8a5 5 0 01-9.54 2"/>' +
        '<path d="M12 3v3h-3M4 13v-3h3" stroke-linecap="round" stroke-linejoin="round"/></svg>';
}

function forkIcon() {
    return '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">' +
        '<circle cx="5" cy="4" r="2"/><circle cx="11" cy="4" r="2"/><circle cx="5" cy="12" r="2"/>' +
        '<path d="M5 6v4M11 6c0 3-6 3-6 6"/></svg>';
}

function createHoverActions(messageId) {
    return '<div class="message-hover-actions">' +
        '<button class="hover-action-btn" title="Copy" onclick="copyMessageContent(\'' + escapeHtml(messageId) + '\')">' +
            copyIcon() +
        '</button>' +
        '<button class="hover-action-btn" title="Fork" onclick="forkFromMessage(\'' + escapeHtml(messageId) + '\')">' +
            forkIcon() +
        '</button>' +
        '<button class="hover-action-btn" title="Retry" onclick="retryFromMessage(\'' + escapeHtml(messageId) + '\')">' +
            retryIcon() +
        '</button>' +
    '</div>';
}

function copyMessageContent(messageId) {
    var msg = document.getElementById('msg-' + messageId);
    if (!msg) return;
    var raw = msg.getAttribute('data-raw');
    var text = raw || (msg.querySelector('.message-content') || {}).textContent || '';
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'copy', code: text }));
    }
}

function retryFromMessage(messageId) {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'retryMessage', messageId: messageId }));
    }
}

function forkFromMessage(messageId) {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'forkMessage', messageId: messageId }));
    }
}

// ── Empty State ──────────────────────────────────────────────────

function showEmptyState(visible) {
    var el = document.getElementById('empty-state');
    if (el) {
        el.style.display = visible ? 'flex' : 'none';
    }
    var msgs = document.getElementById('messages');
    if (msgs) {
        msgs.style.display = visible ? 'none' : 'flex';
    }
}

// ── Header Functions ────────────────────────────────────────────────

function handleSessionsClick() {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'sessionsClick' }));
    }
}

function handleNewConversation() {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'newConversation' }));
    }
}

function handleSettings() {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'settings' }));
    }
}

function setSessionTitle(title) {
    var el = document.getElementById('session-title');
    if (el) el.textContent = title;
}

function setStatusDot(state) {
    var el = document.getElementById('status-dot');
    if (!el) return;
    el.className = 'status-dot';
    if (state) el.classList.add(state);
}

// ── Input Functions ────────────────────────────────────────────────

function handleSend() {
    var input = document.getElementById('message-input');
    if (!input) return;
    var text = (input.innerText || input.textContent || '').trim();
    if (!text) return;
    // Dismiss slash dropdown
    _dismissSlashDropdown();
    input.textContent = '';
    _updateSendButtonState();
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'sendMessage', text: text }));
    }
}

function handleInputKeyDown(e) {
    // Slash command dropdown navigation
    var dropdown = document.getElementById('slash-dropdown');
    if (dropdown && dropdown.classList.contains('visible')) {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            _slashDropdownMove(1);
            return;
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            _slashDropdownMove(-1);
            return;
        } else if (e.key === 'Enter') {
            e.preventDefault();
            _slashDropdownSelect();
            return;
        } else if (e.key === 'Escape') {
            e.preventDefault();
            _dismissSlashDropdown();
            return;
        }
    }

    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
    }
}

function setInputEnabled(enabled) {
    var input = document.getElementById('message-input');
    var btn = document.getElementById('send-btn');
    if (input) {
        input.contentEditable = enabled ? 'true' : 'false';
        input.style.opacity = enabled ? '1' : '0.6';
    }
    if (btn) {
        btn.disabled = !enabled;
    }
}

function focusInput() {
    var input = document.getElementById('message-input');
    if (input) {
        input.focus();
    }
}

function setInputText(text) {
    var input = document.getElementById('message-input');
    if (input) {
        input.textContent = text;
        // Move cursor to end
        var range = document.createRange();
        range.selectNodeContents(input);
        range.collapse(false);
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        _updateSendButtonState();
    }
}

function insertTextAtCursor(text) {
    var input = document.getElementById('message-input');
    if (!input) return;
    input.focus();
    document.execCommand('insertText', false, text);
    _updateSendButtonState();
}

function _updateSendButtonState() {
    var input = document.getElementById('message-input');
    var btn = document.getElementById('send-btn');
    if (input && btn) {
        var text = (input.innerText || input.textContent || '').trim();
        btn.disabled = text.length === 0;
    }
}

// ── Footer Update Functions ────────────────────────────────────────

function setModelLabel(text) {
    var el = document.getElementById('model-label');
    if (el) el.textContent = text;
}

function setCostLabel(text) {
    var el = document.getElementById('cost-label');
    if (el) el.textContent = text;
}

function setPermissionModeLabel(text) {
    var el = document.getElementById('permission-mode-label');
    if (el) el.textContent = text;
}

function setThinkingLabel(text) {
    var el = document.getElementById('thinking-label');
    if (el) el.textContent = text;
}

function setSpinnerColor(cssColor) {
    document.documentElement.style.setProperty(
        '--app-spinner-foreground', cssColor
    );
}

function handleModelClick() {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'modelClick' }));
    }
}

function handlePermissionModeClick() {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'permissionModeClick' }));
    }
}

function handleThinkingClick() {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'thinkingClick' }));
    }
}

function setBranchPill(branchName) {
    var el = document.getElementById('branch-pill');
    if (!el) return;
    if (branchName) {
        el.textContent = '\uD83C\uDF3F ' + branchName;
        el.style.display = '';
        el.title = 'Current branch: ' + branchName;
    } else {
        el.style.display = 'none';
    }
}

function setSendHint(useCtrlEnter) {
    var el = document.getElementById('send-hint');
    if (!el) return;
    var isMac = navigator.platform.indexOf('Mac') > -1;
    if (useCtrlEnter) {
        el.textContent = (isMac ? '\u2318' : 'Ctrl') +
            '+Enter to send';
    } else {
        el.textContent = 'Enter to send';
    }
}

// ── Slash Command Dropdown ─────────────────────────────────────────

var _slashCommands = [];
var _slashSelectedIndex = 0;

function _initSlashCommands(commandsJson) {
    try {
        _slashCommands = JSON.parse(commandsJson);
    } catch (e) {
        _slashCommands = [];
    }
}

function _onInputChange() {
    var input = document.getElementById('message-input');
    if (!input) return;
    _updateSendButtonState();

    var text = (input.innerText || input.textContent || '').trim();
    if (text.startsWith('/') && text.indexOf('\n') === -1) {
        _showSlashDropdown(text);
    } else {
        _dismissSlashDropdown();
    }
}

function _showSlashDropdown(query) {
    var dropdown = document.getElementById('slash-dropdown');
    if (!dropdown) return;

    var normalized = query.replace(/^\//, '').toLowerCase();
    var filtered = _slashCommands.filter(function(cmd) {
        return cmd.name.replace(/^\//, '').toLowerCase().indexOf(normalized) === 0;
    });

    if (filtered.length === 0) {
        _dismissSlashDropdown();
        return;
    }

    _slashSelectedIndex = 0;
    dropdown.innerHTML = '';
    for (var i = 0; i < filtered.length; i++) {
        var item = document.createElement('div');
        item.className = 'slash-item' + (i === 0 ? ' selected' : '');
        item.setAttribute('data-index', i);
        item.setAttribute('data-name', filtered[i].name);
        item.innerHTML =
            '<span class="slash-item-name">' + escapeHtml(filtered[i].name) + '</span>' +
            '<span class="slash-item-desc">' + escapeHtml(filtered[i].description) + '</span>';
        item.onclick = (function(idx) {
            return function() {
                _slashSelectedIndex = idx;
                _slashDropdownSelect();
            };
        })(i);
        dropdown.appendChild(item);
    }
    dropdown.classList.add('visible');
}

function _dismissSlashDropdown() {
    var dropdown = document.getElementById('slash-dropdown');
    if (dropdown) {
        dropdown.classList.remove('visible');
        dropdown.innerHTML = '';
    }
}

function _slashDropdownMove(delta) {
    var dropdown = document.getElementById('slash-dropdown');
    if (!dropdown) return;
    var items = dropdown.querySelectorAll('.slash-item');
    if (items.length === 0) return;
    items[_slashSelectedIndex].classList.remove('selected');
    _slashSelectedIndex = (_slashSelectedIndex + delta + items.length) % items.length;
    items[_slashSelectedIndex].classList.add('selected');
    items[_slashSelectedIndex].scrollIntoView({ block: 'nearest' });
}

function _slashDropdownSelect() {
    var dropdown = document.getElementById('slash-dropdown');
    if (!dropdown) return;
    var items = dropdown.querySelectorAll('.slash-item');
    if (items.length === 0 || _slashSelectedIndex >= items.length) return;
    var name = items[_slashSelectedIndex].getAttribute('data-name');
    _dismissSlashDropdown();
    // Clear input and send the command to Kotlin
    var input = document.getElementById('message-input');
    if (input) input.textContent = '';
    _updateSendButtonState();
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({ action: 'slashCommand', command: name }));
    }
}

// ── Usage Bar ────────────────────────────────────────────────────

function updateUsageBar(percent, label) {
    var bar = document.getElementById('usage-bar');
    if (!bar) return;
    if (percent > 0) {
        bar.style.display = '';
    }
    var fill = bar.querySelector('.usage-fill');
    if (!fill) return;
    fill.style.width = Math.min(percent, 100) + '%';
    fill.className = 'usage-fill';
    if (percent >= 80) fill.classList.add('danger');
    else if (percent >= 60) fill.classList.add('warning');
    bar.title = label || (percent + '% context used');
}

/**
 * Remove all message elements from the given message ID onwards.
 * Used when rewinding conversation.
 */
function removeMessagesFrom(messageId) {
    var container = document.getElementById('messages');
    var target = document.getElementById('msg-' + messageId);
    if (!target) return;

    var toRemove = [];
    var found = false;
    var children = container.children;
    for (var i = 0; i < children.length; i++) {
        if (children[i] === target) {
            found = true;
        }
        if (found) {
            toRemove.push(children[i]);
        }
    }
    for (var j = 0; j < toRemove.length; j++) {
        // Also clean up any streaming animations
        var id = toRemove[j].id;
        if (id && id.startsWith('msg-')) {
            var msgId = id.substring(4);
            if (_streamAnim[msgId]) {
                if (_streamAnim[msgId].raf) {
                    cancelAnimationFrame(_streamAnim[msgId].raf);
                }
                delete _streamAnim[msgId];
            }
        }
        container.removeChild(toRemove[j]);
    }
    updateTimelineClasses();
}

/**
 * Replace all messages from the given ID with a summary message.
 */
function replaceMessagesWithSummary(messageId, summaryText) {
    removeMessagesFrom(messageId);

    // Add a system message with the summary
    var container = document.getElementById('messages');
    var div = document.createElement('div');
    div.className = 'message message-system';
    div.innerHTML =
        '<div class="message-content">' +
            renderMarkdown(summaryText) +
        '</div>';
    container.appendChild(div);
    updateTimelineClasses();
    scrollToBottom();
}

/**
 * Update file-change badge on a rewind button.
 */
function setRewindBadge(messageId, fileCount) {
    var msg = document.getElementById('msg-' + messageId);
    if (!msg) return;
    var btn = msg.querySelector('.rewind-btn');
    if (!btn) return;

    var existing = btn.querySelector('.rewind-badge');
    if (fileCount > 0) {
        if (!existing) {
            var badge = document.createElement('span');
            badge.className = 'rewind-badge';
            badge.textContent = fileCount;
            btn.appendChild(badge);
        } else {
            existing.textContent = fileCount;
        }
    } else if (existing) {
        existing.remove();
    }
}

// ── Page Initialization ─────────────────────────────────────────────

// ── Banner system ──────────────────────────────────────────────

/**
 * Shows a dismissible banner above the messages area.
 * @param {string} id - Unique banner identifier (used for persistence)
 * @param {string} content - HTML or text content to display
 * @param {string} type - 'info' | 'warning' | 'error' | 'success'
 */
function showBanner(id, content, type) {
    type = type || 'info';
    var container = document.getElementById('banner-container');
    if (!container) return;

    // Don't add duplicates
    if (document.getElementById('banner-' + id)) return;

    var banner = document.createElement('div');
    banner.id = 'banner-' + id;
    banner.className = 'banner banner-' + type;

    var contentEl = document.createElement('div');
    contentEl.className = 'banner-content';
    contentEl.innerHTML = content;

    var closeBtn = document.createElement('button');
    closeBtn.className = 'banner-close';
    closeBtn.innerHTML = '&#215;';
    closeBtn.setAttribute('aria-label', 'Dismiss');
    closeBtn.addEventListener('click', function() {
        dismissBanner(id);
    });

    banner.appendChild(contentEl);
    banner.appendChild(closeBtn);
    container.appendChild(banner);
}

/**
 * Dismisses a banner by id and notifies Kotlin for persistence.
 * @param {string} id - The banner identifier to dismiss
 */
function dismissBanner(id) {
    var el = document.getElementById('banner-' + id);
    if (el) {
        el.style.animation = 'none';
        el.style.opacity = '0';
        el.style.transition = 'opacity 0.15s ease-out';
        setTimeout(function() { el.remove(); }, 160);
    }
    // Notify Kotlin to persist the dismissal
    if (typeof sendToKotlin === 'function') {
        sendToKotlin(JSON.stringify({
            action: 'dismissBanner',
            bannerId: id
        }));
    }
}

/**
 * Set up all event listeners for header, input, and footer buttons.
 * Called once from the HTML after all scripts are loaded.
 * Uses addEventListener instead of inline onclick for JCEF compatibility.
 */
function _initPage() {
    // Header buttons
    var sessionsBtn = document.getElementById('sessions-button');
    if (sessionsBtn) sessionsBtn.addEventListener('click', handleSessionsClick);

    var newConvBtn = document.getElementById('new-conversation-btn');
    if (newConvBtn) newConvBtn.addEventListener('click', handleNewConversation);

    var settingsBtn = document.getElementById('settings-btn');
    if (settingsBtn) settingsBtn.addEventListener('click', handleSettings);

    var teleportBtn = document.getElementById('teleport-btn');
    if (teleportBtn) teleportBtn.addEventListener('click', handleTeleport);

    // Input area
    var messageInput = document.getElementById('message-input');
    if (messageInput) {
        messageInput.addEventListener('keydown', handleInputKeyDown);
        messageInput.addEventListener('input', _onInputChange);
    }

    // Footer buttons
    var permModeBtn = document.getElementById('permission-mode-btn');
    if (permModeBtn) permModeBtn.addEventListener('click', handlePermissionModeClick);

    var modelBtn = document.getElementById('model-btn');
    if (modelBtn) modelBtn.addEventListener('click', handleModelClick);

    var thinkingBtn = document.getElementById('thinking-btn');
    if (thinkingBtn) thinkingBtn.addEventListener('click', handleThinkingClick);

    // Send button
    var sendBtn = document.getElementById('send-btn');
    if (sendBtn) sendBtn.addEventListener('click', handleSend);

    // File attachments
    _setupDragAndDrop();
    _setupClipboardPaste();
}

// ── Session Teleport ──────────────────────────────────────────────

// Current teleport target label (set from Kotlin)
var _teleportTarget = 'tab'; // 'tab' or 'sidebar'

/**
 * Shows or hides the teleport button and sets its label/tooltip.
 * Called from Kotlin: setTeleportLabel("tab") or setTeleportLabel("sidebar")
 * Pass null or empty to hide the button.
 */
function setTeleportLabel(target) {
    var btn = document.getElementById('teleport-btn');
    if (!btn) return;
    if (!target) {
        btn.style.display = 'none';
        return;
    }
    _teleportTarget = target;
    btn.style.display = '';
    if (target === 'tab') {
        btn.title = 'Move to Editor Tab';
    } else {
        btn.title = 'Move to Sidebar';
    }
}

function handleTeleport() {
    if (sendToKotlin) {
        sendToKotlin(JSON.stringify({
            action: 'teleportSession',
            target: _teleportTarget
        }));
    }
}
