import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';
import { handlerMethods } from 'build-time-data';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/dialog';
import { dialogRenderer, dialogFooterRenderer } from '@vaadin/dialog/lit.js';
import '@vaadin/text-area';
import '@vaadin/password-field';
import '@qomponent/qui-badge';
import 'qui-themed-code-block';
import './qwc-nextcloud-mode-badge.js';

/**
 * Lists the Nextcloud webhook events received in dev mode, live. Events can be
 * replayed as received or after editing their JSON.
 */
export class QwcNextcloudEvents extends LitElement {
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
        .content {
            display: flex;
            flex-direction: column;
            flex: 1;
            min-height: 0;
        }
        vaadin-grid {
            flex: 1;
            min-height: 200px;
        }
        .details {
            border-top: 1px solid var(--lumo-contrast-10pct);
            padding: 10px;
            max-height: 50%;
            overflow: auto;
        }
        .details-header {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
        }
        pre.error {
            color: var(--lumo-error-text-color);
            white-space: pre-wrap;
            font-size: var(--lumo-font-size-xs);
        }
        .hint {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
            padding: 10px;
        }
        code {
            font-size: var(--lumo-font-size-s);
        }`;

    static properties = {
        _events: { state: true },
        _selected: { state: true },
        _instance: { state: true },
        _handlerNames: { state: true },
        _editOpen: { state: true },
        _editJson: { state: true },
        _editError: { state: true },
        _passwordRequest: { state: true },
        _password: { state: true }
    };

    constructor() {
        super();
        this._events = [];
        this._handlerNames = {};
        this._editOpen = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getInstanceInfo().then(r => this._instance = r.result);
        this._refresh();
        this._loadHandlerNames();
        this._stream = this.jsonRpc.streamEvents().onNext(r => this._upsert(r.result));
    }

    disconnectedCallback() {
        this._stream?.cancel();
        super.disconnectedCallback();
    }

    render() {
        return html`
            <qwc-nextcloud-mode-badge .info=${this._instance}></qwc-nextcloud-mode-badge>
            <div class="menubar">
                <vaadin-button theme="small tertiary" @click=${() => this._refresh()}>
                    <vaadin-icon icon="font-awesome-solid:rotate" slot="prefix"></vaadin-icon>Refresh
                </vaadin-button>
                <vaadin-button theme="small tertiary" @click=${() => this._clear()}>
                    <vaadin-icon icon="font-awesome-solid:trash-can" slot="prefix"></vaadin-icon>Clear
                </vaadin-button>
                <span class="hint">${this._events.length} event(s), kept in memory in dev mode only</span>
            </div>
            <div class="content">
                ${this._events.length === 0
                    ? html`<span class="hint">No events received yet. Trigger an action in Nextcloud
                        (e.g. upload a file). If the webhook worker is not enabled, run it on the Tools page.</span>`
                    : this._renderGrid()}
                ${this._selected ? this._renderDetails(this._selected) : ''}
            </div>
            ${this._renderEditDialog()}
            ${this._renderPasswordDialog()}`;
    }

    _renderGrid() {
        return html`<vaadin-grid .items=${this._events} theme="no-border row-stripes"
                .selectedItems=${this._selected ? [this._selected] : []}
                @active-item-changed=${e => { if (e.detail.value) this._selected = e.detail.value; }}>
            <vaadin-grid-column header="Time" auto-width flex-grow="0"
                ${columnBodyRenderer(e => html`${new Date(e.timestamp).toLocaleTimeString()}`, [])}></vaadin-grid-column>
            <vaadin-grid-column header="Status" auto-width flex-grow="0"
                ${columnBodyRenderer(e => this._statusBadge(e), [])}></vaadin-grid-column>
            <vaadin-grid-column header="Event" auto-width
                ${columnBodyRenderer(e => html`<code title="${e.eventClass ?? ''}">${this._shortClass(e.eventClass)}</code>`, [])}></vaadin-grid-column>
            <vaadin-grid-column header="Handler" auto-width
                ${columnBodyRenderer(e => html`<code>${this._handlerName(e.handlerId)}</code>`, [this._handlerNames])}></vaadin-grid-column>
            <vaadin-grid-column header="User" auto-width flex-grow="0"
                ${columnBodyRenderer(e => html`${e.triggerUser ?? '-'}`, [])}></vaadin-grid-column>
            <vaadin-grid-column header="Duration" auto-width flex-grow="0"
                ${columnBodyRenderer(e => html`${e.durationMs != null ? e.durationMs + ' ms' : ''}`, [])}></vaadin-grid-column>
            <vaadin-grid-column header="" auto-width flex-grow="0"
                ${columnBodyRenderer(e => e.replayOf ? html`<qui-badge small title="Replay of #${e.replayOf}">replay</qui-badge>` : '', [])}></vaadin-grid-column>
        </vaadin-grid>`;
    }

    _renderDetails(e) {
        return html`<div class="details">
            <div class="details-header">
                <strong>#${e.id}</strong>
                ${this._statusBadge(e)}
                <code>${e.eventClass ?? 'unknown event'}</code>
                <span class="hint">credentials: ${e.credentialSource}${e.tokenRevoked ? ' (token deleted)' : ''}
                    ${e.replayOf ? ` · replay of #${e.replayOf}` : ''}</span>
                <vaadin-button theme="small primary" @click=${() => this._replay(e, null)}>
                    <vaadin-icon icon="font-awesome-solid:play" slot="prefix"></vaadin-icon>Replay
                </vaadin-button>
                <vaadin-button theme="small" @click=${() => this._openEdit(e)}>
                    <vaadin-icon icon="font-awesome-solid:pen" slot="prefix"></vaadin-icon>Edit &amp; Replay
                </vaadin-button>
                <vaadin-button theme="small tertiary" @click=${() => this._selected = null}>
                    <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                </vaadin-button>
            </div>
            ${e.error ? html`<pre class="error">${e.error}</pre>` : ''}
            <qui-themed-code-block mode="json" content="${e.json ?? ''}" showLineNumbers></qui-themed-code-block>
        </div>`;
    }

    _renderPasswordDialog() {
        const request = this._passwordRequest;
        return html`<vaadin-dialog header-title="Password required for replay"
            .opened=${!!request}
            @opened-changed=${e => { if (!e.detail.value) this._passwordRequest = null; }}
            ${dialogRenderer(() => request ? html`
                <div style="display: flex; flex-direction: column; gap: 8px; max-width: 500px;">
                    <span>${request.message}</span>
                    <span class="hint" style="padding:0">The password is used to create a temporary app password
                        for <b>${request.user}</b>, which is deleted after the replay. It is kept in memory of the
                        running application for further replays.</span>
                    <vaadin-password-field label="Password of ${request.user}" autofocus
                        .value=${this._password ?? ''}
                        @value-changed=${ev => this._password = ev.detail.value}
                        @keydown=${ev => { if (ev.key === 'Enter') this._submitPassword(); }}></vaadin-password-field>
                </div>` : '', [request, this._password])}
            ${dialogFooterRenderer(() => html`
                <vaadin-button @click=${() => this._passwordRequest = null}>Cancel</vaadin-button>
                <vaadin-button theme="primary" ?disabled=${!this._password}
                    @click=${() => this._submitPassword()}>Replay</vaadin-button>`, [this._password])}
        ></vaadin-dialog>`;
    }

    _submitPassword() {
        const request = this._passwordRequest;
        if (!request || !this._password) {
            return;
        }
        const password = this._password;
        this._password = '';
        this._passwordRequest = null;
        this._replay(request.event, request.editedJson, password, true).then(success => {
            if (success && request.editedJson != null) {
                this._editOpen = false;
            }
        });
    }

    _renderEditDialog() {
        return html`<vaadin-dialog header-title="Edit & Replay event"
            .opened=${this._editOpen}
            @opened-changed=${e => this._editOpen = e.detail.value}
            ${dialogRenderer(() => html`
                <div style="width: 70vw; max-width: 900px;">
                    <vaadin-text-area style="width: 100%; min-height: 50vh; font-family: monospace;"
                        .value=${this._editJson}
                        @value-changed=${ev => { this._editJson = ev.detail.value; this._editError = null; }}
                        error-message=${this._editError ?? ''}
                        ?invalid=${!!this._editError}></vaadin-text-area>
                    <span class="hint">Masked tokens (********) are replaced by the original token on replay.</span>
                </div>`, [this._editJson, this._editError])}
            ${dialogFooterRenderer(() => html`
                <vaadin-button @click=${() => this._editOpen = false}>Cancel</vaadin-button>
                <vaadin-button theme="primary" @click=${() => this._replayEdited()}>Replay</vaadin-button>`, [this._editJson])}
        ></vaadin-dialog>`;
    }

    _statusBadge(e) {
        const levels = { OK: 'success', FAILED: 'error', NO_HANDLER: 'error', PENDING: 'contrast', UNTRACKED: 'contrast' };
        return html`<qui-badge small level="${levels[e.status] ?? 'contrast'}">${e.status}</qui-badge>`;
    }

    _shortClass(className) {
        if (!className) {
            return '?';
        }
        const parts = className.split('\\');
        return parts[parts.length - 1];
    }

    _handlerName(handlerId) {
        return this._handlerNames[handlerId] ?? (handlerId ? handlerId.substring(0, 12) + '…' : '?');
    }

    _loadHandlerNames() {
        this.jsonRpc.getHandlers().then(r => {
            const names = {};
            for (const h of r.result.handlers ?? []) {
                const key = h.invokerClassName.replace(/_ClientProxy$/, '');
                const m = handlerMethods?.[key];
                names[h.id] = m ? `${m.declaringClass.split('.').pop()}#${m.method}` : key;
            }
            this._handlerNames = names;
        });
    }

    _refresh() {
        this.jsonRpc.getEvents().then(r => {
            this._events = r.result;
            if (this._selected) {
                this._selected = this._events.find(e => e.id === this._selected.id) ?? null;
            }
        });
    }

    _clear() {
        this.jsonRpc.clearEvents().then(() => {
            this._events = [];
            this._selected = null;
        });
    }

    _upsert(event) {
        const index = this._events.findIndex(e => e.id === event.id);
        if (index >= 0) {
            const copy = [...this._events];
            copy[index] = event;
            this._events = copy;
        } else {
            this._events = [event, ...this._events];
        }
        if (this._selected?.id === event.id) {
            this._selected = event;
        }
    }

    _openEdit(e) {
        this._editJson = e.json ?? '';
        this._editError = null;
        this._editTarget = e;
        this._editOpen = true;
    }

    _replayEdited() {
        try {
            JSON.parse(this._editJson);
        } catch (err) {
            this._editError = 'Invalid JSON: ' + err.message;
            return;
        }
        this._replay(this._editTarget, this._editJson).then(success => {
            if (success) {
                this._editOpen = false;
            }
        });
    }

    _replay(e, editedJson, password = '', alreadyConfirmed = false) {
        const external = !this._instance?.localDevService;
        if (external && !alreadyConfirmed && !confirm(`Handlers may modify data on the external Nextcloud instance `
                + `${this._instance?.host ?? ''}. Replay event #${e.id}?`)) {
            return Promise.resolve(false);
        }
        return this.jsonRpc.replay({
            id: e.id, editedJson: editedJson ?? '', confirmed: !!external, password: password ?? ''
        }).then(r => {
            const result = r.result;
            if (result.success) {
                notifier.showSuccessMessage(result.message);
            } else if (result.passwordRequired) {
                // The original token was deleted: ask for the password of the event's user
                this._password = '';
                this._passwordRequest = { event: e, editedJson: editedJson, user: result.user, message: result.message };
            } else {
                notifier.showErrorMessage(result.message);
            }
            return result.success;
        });
    }
}
customElements.define('qwc-nextcloud-events', QwcNextcloudEvents);
