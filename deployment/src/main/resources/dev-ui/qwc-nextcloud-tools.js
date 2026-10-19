import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/text-field';
import '@vaadin/progress-bar';
import './qwc-nextcloud-mode-badge.js';

/**
 * Tools working on the Nextcloud dev service container: run the webhook
 * background job and an occ console. Only available for the container started
 * by this extension - never for an external Nextcloud instance.
 */
export class QwcNextcloudTools extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 10px;
            padding: 10px;
        }
        .row {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        vaadin-text-field {
            flex: 1;
            font-family: monospace;
        }
        pre {
            background: var(--lumo-contrast-5pct);
            padding: 10px;
            margin: 0;
            white-space: pre-wrap;
            font-size: var(--lumo-font-size-s);
            max-height: 60vh;
            overflow: auto;
        }
        pre.stderr {
            color: var(--lumo-error-text-color);
        }
        .hint {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
        }
        h4 {
            margin: 10px 0 0 0;
        }`;

    static properties = {
        _status: { state: true },
        _instance: { state: true },
        _command: { state: true },
        _running: { state: true },
        _result: { state: true },
        _history: { state: true }
    };

    constructor() {
        super();
        this._command = '';
        this._history = [];
        this._historyIndex = -1;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getInstanceInfo().then(r => this._instance = r.result);
        this._loadStatus();
    }

    render() {
        return html`
            <qwc-nextcloud-mode-badge .info=${this._instance}></qwc-nextcloud-mode-badge>
            ${this._status && !this._status.available
                ? html`<span class="hint">${this._status.reason}</span>` : ''}
            <h4>Webhook worker</h4>
            <div class="row">
                <vaadin-button ?disabled=${!this._available() || this._running} @click=${() => this._runWebhookWorker()}>
                    <vaadin-icon icon="font-awesome-solid:play" slot="prefix"></vaadin-icon>Run webhook worker now
                </vaadin-button>
                <span class="hint">Nextcloud sends queued webhook events (runs up to 20 seconds).
                    Useful if nextcloud.dev-services.enable-webhook-worker is off.</span>
            </div>
            <h4>occ console</h4>
            <div class="row">
                <vaadin-text-field placeholder="e.g. app:list or webhook_listeners:list"
                    ?disabled=${!this._available()}
                    .value=${this._command}
                    @value-changed=${e => this._command = e.detail.value}
                    @keydown=${e => this._onKey(e)}>
                    <span slot="prefix">occ</span>
                </vaadin-text-field>
                <vaadin-button theme="primary" ?disabled=${!this._available() || this._running || !this._command.trim()}
                    @click=${() => this._runOcc()}>Run</vaadin-button>
            </div>
            ${this._running ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>` : ''}
            ${this._renderResult()}`;
    }

    _renderResult() {
        const r = this._result;
        if (!r) {
            return '';
        }
        if (!r.available) {
            return html`<pre class="stderr">${r.reason}</pre>`;
        }
        return html`
            <span class="hint"><code>${r.command}</code> exited with code ${r.exitCode}</span>
            ${r.stdout ? html`<pre>${r.stdout}</pre>` : ''}
            ${r.stderr ? html`<pre class="stderr">${r.stderr}</pre>` : ''}`;
    }

    _available() {
        return this._status?.available;
    }

    _loadStatus() {
        return this.jsonRpc.getContainerStatus().then(r => this._status = r.result);
    }

    _onKey(e) {
        if (e.key === 'Enter') {
            this._runOcc();
        } else if (e.key === 'ArrowUp' && this._history.length) {
            this._historyIndex = Math.min(this._historyIndex + 1, this._history.length - 1);
            this._command = this._history[this._historyIndex];
        } else if (e.key === 'ArrowDown' && this._historyIndex > 0) {
            this._historyIndex--;
            this._command = this._history[this._historyIndex];
        }
    }

    _runOcc() {
        const command = this._command.trim();
        if (!command || this._running) {
            return;
        }
        this._history = [command, ...this._history.filter(c => c !== command)].slice(0, 50);
        this._historyIndex = -1;
        this._execute(this.jsonRpc.runOcc({ command: command }), 'occ ' + command);
    }

    _runWebhookWorker() {
        this._execute(this.jsonRpc.runWebhookWorker(), 'occ background-job:worker (WebhookCall)').then(r => {
            if (r?.available && r.exitCode === 0) {
                notifier.showSuccessMessage('Webhook worker finished');
            }
        });
    }

    _execute(promise, label) {
        this._running = true;
        return promise.then(r => {
            this._result = { ...r.result, command: label };
            if (!r.result.available) {
                this._loadStatus();
            }
            return r.result;
        }).catch(err => {
            this._result = { available: false, reason: err?.error?.message ?? String(err) };
        }).finally(() => this._running = false);
    }
}
customElements.define('qwc-nextcloud-tools', QwcNextcloudTools);
