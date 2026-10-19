import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import { handlerMethods } from 'build-time-data';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/button';
import '@vaadin/icon';
import '@qomponent/qui-badge';
import './qwc-nextcloud-mode-badge.js';

/**
 * Lists the @OnNextcloudEvent handlers and the webhooks registered for them in
 * Nextcloud.
 */
export class QwcNextcloudHandlers extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            height: 100%;
        }
        .menubar {
            display: flex;
            align-items: center;
            gap: 5px;
            padding: 0 10px;
        }
        vaadin-grid {
            flex: 1;
        }
        .cell {
            display: flex;
            flex-direction: column;
            gap: 2px;
        }
        code {
            font-size: var(--lumo-font-size-s);
        }
        .hint {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
            padding: 10px;
        }`;

    static properties = {
        _handlers: { state: true },
        _eventsEnabled: { state: true },
        _instance: { state: true },
        _busy: { state: true }
    };

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getInstanceInfo().then(r => this._instance = r.result);
        this._refresh();
    }

    render() {
        return html`
            <qwc-nextcloud-mode-badge .info=${this._instance}></qwc-nextcloud-mode-badge>
            <div class="menubar">
                <vaadin-button theme="small tertiary" @click=${() => this._refresh()}>
                    <vaadin-icon icon="font-awesome-solid:rotate" slot="prefix"></vaadin-icon>Refresh
                </vaadin-button>
                <vaadin-button theme="small" ?disabled=${this._busy || !this._eventsEnabled}
                    @click=${() => this._reregister()}>
                    <vaadin-icon icon="font-awesome-solid:arrows-rotate" slot="prefix"></vaadin-icon>Re-register webhooks
                </vaadin-button>
            </div>
            ${this._renderContent()}`;
    }

    _renderContent() {
        if (!this._handlers) {
            return html`<span class="hint">Loading...</span>`;
        }
        if (!this._eventsEnabled || this._handlers.length === 0) {
            return html`<span class="hint">No methods annotated with @OnNextcloudEvent found.</span>`;
        }
        return html`<vaadin-grid .items=${this._handlers} theme="no-border row-stripes">
            <vaadin-grid-column header="Handler" auto-width
                ${columnBodyRenderer(h => this._methodRenderer(h), [])}></vaadin-grid-column>
            <vaadin-grid-column header="Events" auto-width
                ${columnBodyRenderer(h => html`<div class="cell">${h.events.map(e => html`<code>${e}</code>`)}</div>`, [])}></vaadin-grid-column>
            <vaadin-grid-column header="Filter" auto-width
                ${columnBodyRenderer(h => h.filter ? html`<code>${h.filter}</code>` : '-', [])}></vaadin-grid-column>
            <vaadin-grid-column header="Auth token" auto-width flex-grow="0"
                ${columnBodyRenderer(h => h.requestAuthToken ? html`<qui-badge small>requested</qui-badge>` : '-', [])}></vaadin-grid-column>
            <vaadin-grid-column header="Registered webhooks" auto-width
                ${columnBodyRenderer(h => this._webhooksRenderer(h), [])}></vaadin-grid-column>
        </vaadin-grid>`;
    }

    _methodRenderer(h) {
        const m = handlerMethods?.[h.invokerClassName.replace(/_ClientProxy$/, '')];
        return html`<div class="cell">
            <code>${m ? `${m.declaringClass}#${m.method}` : h.invokerClassName}</code>
            <span class="hint" style="padding:0">id ${h.id.substring(0, 12)}…</span>
        </div>`;
    }

    _webhooksRenderer(h) {
        if (!h.registeredWebhooks?.length) {
            return html`<qui-badge small level="warning">none</qui-badge>`;
        }
        return html`<div class="cell">${h.registeredWebhooks.map(w =>
            html`<span><qui-badge small level="success">#${w.id}</qui-badge> <code title="${w.uri}">${w.event.split('\\').pop()}</code></span>`)}</div>`;
    }

    _refresh() {
        this.jsonRpc.getHandlers().then(r => {
            this._eventsEnabled = r.result.eventsEnabled;
            this._handlers = r.result.handlers;
        });
    }

    _reregister() {
        const external = !this._instance?.localDevService;
        if (external && !confirm(`This deletes and re-creates the webhooks of this application on the external `
                + `Nextcloud instance ${this._instance?.host ?? ''}. Continue?`)) {
            return;
        }
        this._busy = true;
        this.jsonRpc.reregisterWebhooks({ confirmed: external }).then(r => {
            if (r.result.success) {
                notifier.showSuccessMessage(r.result.message);
            } else {
                notifier.showErrorMessage(r.result.message);
            }
            this._refresh();
        }).finally(() => this._busy = false);
    }
}
customElements.define('qwc-nextcloud-handlers', QwcNextcloudHandlers);
