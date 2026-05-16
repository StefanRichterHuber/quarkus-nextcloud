package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.clients.impl.AppApiAuthenticatedSardineImpl;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappAppConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
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
    NextcloudExappConfig exappConfig;

    @Inject
    NextcloudExappAppConfig appConfig;

    @Produces
    @RequestScoped
    @DefaultBean
    public Sardine getSardineInstance() {
        final Sardine sardine;
        if (exappConfig.enabled()) {
            sardine = new AppApiAuthenticatedSardineImpl(auth.getUser(), appConfig.secret().get());
        } else {
            sardine = SardineFactory.begin(auth.getUser(), auth.getPassword());
        }

        sardine.enablePreemptiveAuthentication(auth.getServer());
        sardine.enablePreemptiveAuthentication(auth.getServer().replace("https://", "").replace("http://", ""));
        return sardine;
    }

    @Produces
    @RequestScoped
    @NextcloudAdmin
    @DefaultBean
    public Sardine getSardineAdminInstance() {
        final Sardine sardine;
        if (exappConfig.enabled()) {
            sardine = new AppApiAuthenticatedSardineImpl(adminAuth.getUser(), appConfig.secret().get());
        } else {
            sardine = SardineFactory.begin(adminAuth.getUser(), auth.getPassword());
        }

        sardine.enablePreemptiveAuthentication(adminAuth.getServer());
        sardine.enablePreemptiveAuthentication(adminAuth.getServer().replace("https://", "").replace("http://", ""));
        return sardine;
    }

}
