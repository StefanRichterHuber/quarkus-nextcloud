import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import '@vaadin/button';
import '@vaadin/icon';
import '@qomponent/qui-badge';
import './qwc-nextcloud-mode-badge.js';

/**
 * Shows the connected Nextcloud instance and - for the local dev service only -
 * the credentials to log in.
 */
export class QwcNextcloudInstance extends LitElement {
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
            vertical-align: top;
        }
        td.key {
            color: var(--lumo-secondary-text-color);
            white-space: nowrap;
        }
        code {
            font-size: var(--lumo-font-size-s);
        }
        .apps {
            display: flex;
            flex-wrap: wrap;
            gap: 4px;
        }
        .hint {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
        }`;

    static properties = {
        _info: { state: true },
        _revealed: { state: true }
    };

    constructor() {
        super();
        this._revealed = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getInstanceInfo().then(r => this._info = r.result);
    }

    render() {
        return html`
            <qwc-nextcloud-mode-badge .info=${this._info}></qwc-nextcloud-mode-badge>
            ${this._info ? this._renderInfo() : html`<span>Loading...</span>`}`;
    }

    _renderInfo() {
        const i = this._info;
        const local = i.mode === 'LOCAL_DEV_SERVICE' || i.localDevService;
        return html`
            <div>
                <vaadin-button theme="primary" ?disabled=${!i.url} @click=${() => window.open(i.url, '_blank')}>
                    <vaadin-icon icon="font-awesome-solid:arrow-up-right-from-square" slot="prefix"></vaadin-icon>
                    Open Nextcloud
                </vaadin-button>
            </div>
            <table>
                ${this._row('URL', i.url, true)}
                ${this._row('User', i.user, true)}
                ${local ? this._passwordRow('Password', i.password) : this._externalPasswordRow(i)}
                ${i.adminUser && i.adminUser !== i.user ? this._row('Admin user', i.adminUser, true) : ''}
                ${local && i.adminPassword && i.adminPassword !== i.password
                    ? this._passwordRow('Admin password', i.adminPassword) : ''}
                ${this._row('Auth mode', i.authMode)}
                ${this._row('ExApp', i.exAppEnabled ? 'enabled' : 'disabled')}
                ${i.status?.version ? this._row('Version', i.status.version) : ''}
                ${i.status?.maintenance ? this._row('Maintenance', 'Maintenance mode is active') : ''}
                ${local && i.containerId ? this._row('Container', i.containerId, true) : ''}
                ${local && i.apps?.length ? html`<tr><td class="key">Installed apps</td><td>
                    <div class="apps">${i.apps.map(a => html`<qui-badge small>${a}</qui-badge>`)}</div></td></tr>` : ''}
            </table>
            ${local ? html`<span class="hint">The dev service instance is discarded when the application stops.
                Use these credentials to log in, e.g. to test your ExApp or trigger events.</span>` : ''}`;
    }

    _row(key, value, copy = false) {
        if (value === null || value === undefined) {
            return '';
        }
        return html`<tr>
            <td class="key">${key}</td>
            <td><code>${value}</code> ${copy ? this._copyButton(value) : ''}</td>
        </tr>`;
    }

    _passwordRow(key, value) {
        if (!value) {
            return '';
        }
        return html`<tr>
            <td class="key">${key}</td>
            <td>
                <code>${this._revealed ? value : '••••••••'}</code>
                <vaadin-button theme="small tertiary" title="${this._revealed ? 'Hide' : 'Reveal'}"
                    @click=${() => this._revealed = !this._revealed}>
                    <vaadin-icon icon="font-awesome-solid:${this._revealed ? 'eye-slash' : 'eye'}"></vaadin-icon>
                </vaadin-button>
                ${this._copyButton(value)}
            </td>
        </tr>`;
    }

    _externalPasswordRow(info) {
        if (!info.passwordConfigured) {
            return '';
        }
        return html`<tr>
            <td class="key">Password</td>
            <td><code>••••••••</code>
                <span class="hint">Configured credentials of an external instance are not shown.</span></td>
        </tr>`;
    }

    _copyButton(value) {
        return html`<vaadin-button theme="small tertiary" title="Copy" @click=${() => this._copy(value)}>
            <vaadin-icon icon="font-awesome-solid:copy"></vaadin-icon>
        </vaadin-button>`;
    }

    _copy(value) {
        navigator.clipboard.writeText(value).then(
            () => notifier.showInfoMessage('Copied to clipboard'),
            () => notifier.showErrorMessage('Failed to copy to clipboard'));
    }
}
customElements.define('qwc-nextcloud-instance', QwcNextcloudInstance);
