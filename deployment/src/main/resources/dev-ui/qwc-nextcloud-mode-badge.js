import { LitElement, html, css } from 'lit';
import '@qomponent/qui-badge';

/**
 * Badge showing whether the application is connected to the Nextcloud dev
 * service, to an external instance or to no reachable instance.
 *
 * The page passes the result of getInstanceInfo as property 'info'. Note: the
 * JsonRpc instance of a page must not be passed to sub components via a lit
 * property binding - it is a Proxy that answers every property (also lit's
 * internal directive marker), which breaks rendering.
 */
export class QwcNextcloudModeBadge extends LitElement {
    static styles = css`
        :host {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 5px 10px;
        }
        .hint {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
        }`;

    static properties = {
        info: { attribute: false }
    };

    render() {
        const info = this.info;
        if (!info) {
            return html`<qui-badge small>Checking Nextcloud instance...</qui-badge>`;
        }
        switch (info.mode) {
            case 'LOCAL_DEV_SERVICE':
                return html`<qui-badge level="success" small>Local dev service</qui-badge>
                    <span class="hint">${info.url} ${this._version(info)}</span>`;
            case 'EXTERNAL':
                return html`<qui-badge level="warning" small>External: ${info.host}</qui-badge>
                    <span class="hint">Actions run against an external Nextcloud instance ${this._version(info)}</span>`;
            default:
                return html`<qui-badge level="error" small>${info.url ? 'Not reachable' : 'Not configured'}</qui-badge>
                    <span class="hint">${info.url ?? 'nextcloud.url is not set'} ${info.status?.error ?? ''}</span>`;
        }
    }

    _version(info) {
        return info.status?.version ? html`(Nextcloud ${info.status.version})` : '';
    }
}
customElements.define('qwc-nextcloud-mode-badge', QwcNextcloudModeBadge);
