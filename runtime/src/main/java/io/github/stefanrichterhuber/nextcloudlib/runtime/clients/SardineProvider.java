package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import com.github.sardine.Sardine;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.impl.CustomHeaderSardineImpl;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUserCredentials;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;

public class SardineProvider {

    @Inject
    NextcloudAuthProvider auth;

    @Inject
    @NextcloudAdmin
    NextcloudAuthProvider adminAuth;

    @Inject
    NextcloudExappAppConfig appConfig;

    @Produces
    @RequestScoped
    @DefaultBean
    public Sardine getSardineInstance() {
        return buildSardine(auth);
    }

    @Produces
    @RequestScoped
    @NextcloudAdmin
    @DefaultBean
    public Sardine getSardineAdminInstance() {
        return buildSardine(adminAuth);
    }

    /**
     * Builds a Sardine instance with the appropriate authentication headers based
     * on the provided NextcloudAuthProvider. The authentication mode is determined
     * by the NextcloudAuthProvider's getMode() method, which can return one of the
     * following modes: OIDC_TOKEN, EXAPP_API, or APP_PASSWORD. Depending on the
     * mode, the Sardine instance will be configured with the appropriate
     * credentials and headers for making requests to the Nextcloud server.
     * 
     * @param authProvider
     * @return
     */
    private Sardine buildSardine(NextcloudAuthProvider authProvider) {
        final Sardine sardine = new CustomHeaderSardineImpl(authProvider.getCredentials().getRequiredHeaders());
        if (authProvider.getCredentials().mode() == NextcloudUserCredentials.Mode.APP_PASSWORD) {
            sardine.enablePreemptiveAuthentication(authProvider.getServer());
            sardine.enablePreemptiveAuthentication(
                    authProvider.getServer().replace("https://", "").replace("http://", ""));
        }
        sardine.enableCompression();
        return sardine;
    }

}
