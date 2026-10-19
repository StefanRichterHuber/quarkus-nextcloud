import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/progress-bar';
import '@qomponent/qui-badge';
import './qwc-nextcloud-mode-badge.js';

/**
 * Shows the ExApp lifecycle state (heartbeat, init progress, enabled state) and
 * allows enabling / disabling the ExApp in the Nextcloud dev service.
 */
export class QwcNextcloudExapp extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 10px;
            padding: 10px;
        }
        table {
            border-collapse: collapse;
        }
        td {
            padding: 6px 12px 6px 0;
            vertical-align: middle;
        }
        td.key {
            color: var(--lumo-secondary-text-color);
            white-space: nowrap;
        }
        .row {
            display: flex;
            gap: 10px;
            align-items: center;
        }
        vaadin-progress-bar {
            width: 200px;
        }
        pre {
            background: var(--lumo-contrast-5pct);
            padding: 10px;
            margin: 0;
            white-space: pre-wrap;
            font-size: var(--lumo-font-size-s);
        }
        .hint {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
        }`;

    static properties = {
        _status: { state: true },
        _instance: { state: true },
        _container: { state: true },
        _apps: { state: true },
        _busy: { state: true }
    };

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getInstanceInfo().then(r => this._instance = r.result);
        this._refresh();
        this._timer = setInterval(() => this._loadStatus(), 5000);
    }

    disconnectedCallback() {
        clearInterval(this._timer);
        super.disconnectedCallback();
    }

    render() {
        return html`
            <qwc-nextcloud-mode-badge .info=${this._instance}></qwc-nextcloud-mode-badge>
            ${this._status ? this._renderStatus() : html`<span>Loading...</span>`}
            ${this._renderActions()}
            ${this._apps ? html`<pre>${this._apps}</pre>` : ''}`;
    }

    _renderStatus() {
        const s = this._status;
        const st = s.state ?? {};
        if (!s.exAppEnabled) {
            return html`<span class="hint">ExApp support is disabled (nextcloud.exapp.enabled=false).</span>`;
        }
        return html`<table>
            <tr><td class="key">App id</td><td><code>${s.appId}</code> ${s.appVersion ?? ''}</td></tr>
            <tr><td class="key">Enabled</td><td>${st.enabled == null
                ? html`<qui-badge small>unknown</qui-badge>`
                : st.enabled ? html`<qui-badge small level="success">enabled</qui-badge>`
                    : html`<qui-badge small level="warning">disabled</qui-badge>`}
                ${st.lastEnabledChange ? html`<span class="hint">since ${this._time(st.lastEnabledChange)}</span>` : ''}</td></tr>
            <tr><td class="key">Last heartbeat</td><td>${st.lastHeartbeat
                ? html`${this._time(st.lastHeartbeat)} <span class="hint">(${st.heartbeatCount} total)</span>`
                : html`<span class="hint">none received yet</span>`}</td></tr>
            <tr><td class="key">Init progress</td><td>${st.initProgress != null
                ? html`<div class="row"><vaadin-progress-bar value=${st.initProgress / 100}></vaadin-progress-bar>${st.initProgress}%</div>`
                : html`<span class="hint">not reported (no NextcloudExAppInitProgress bean or /init not called yet)</span>`}
                ${st.initError ? html`<div style="color: var(--lumo-error-text-color)">${st.initError}</div>` : ''}</td></tr>
        </table>`;
    }

    _renderActions() {
        const available = this._container?.available && this._container?.exAppId;
        return html`<div class="row">
                <vaadin-button ?disabled=${!available || this._busy} @click=${() => this._setEnabled(true)}>
                    <vaadin-icon icon="font-awesome-solid:toggle-on" slot="prefix"></vaadin-icon>Enable
                </vaadin-button>
                <vaadin-button ?disabled=${!available || this._busy} @click=${() => this._setEnabled(false)}>
                    <vaadin-icon icon="font-awesome-solid:toggle-off" slot="prefix"></vaadin-icon>Disable
                </vaadin-button>
                <vaadin-button theme="tertiary" ?disabled=${!this._container?.available || this._busy}
                    @click=${() => this._listApps()}>
                    <vaadin-icon icon="font-awesome-solid:list" slot="prefix"></vaadin-icon>List ExApps
                </vaadin-button>
            </div>
            ${this._container && !this._container.available
                ? html`<span class="hint">${this._container.reason}</span>`
                : this._container && !this._container.exAppId
                    ? html`<span class="hint">No ExApp was registered in the dev service.</span>` : ''}`;
    }

    _time(millis) {
        return new Date(millis).toLocaleTimeString();
    }

    _refresh() {
        this._loadStatus();
        this.jsonRpc.getContainerStatus().then(r => this._container = r.result);
    }

    _loadStatus() {
        this.jsonRpc.getExAppStatus().then(r => this._status = r.result);
    }

    _setEnabled(enabled) {
        this._busy = true;
        this.jsonRpc.setExAppEnabled({ enabled: String(enabled) }).then(r => {
            const res = r.result;
            if (res.available && res.exitCode === 0) {
                notifier.showSuccessMessage(`ExApp ${enabled ? 'enabled' : 'disabled'}`);
            } else {
                notifier.showErrorMessage(res.reason ?? res.stderr ?? res.stdout ?? 'Failed');
            }
            this._loadStatus();
        }).finally(() => this._busy = false);
    }

    _listApps() {
        this._busy = true;
        this.jsonRpc.listExApps().then(r => {
            const res = r.result;
            this._apps = res.available ? (res.stdout || res.stderr) : res.reason;
        }).finally(() => this._busy = false);
    }
}
customElements.define('qwc-nextcloud-exapp', QwcNextcloudExapp);
