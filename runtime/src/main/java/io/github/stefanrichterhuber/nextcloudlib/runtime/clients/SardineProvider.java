package io.github.stefanrichterhuber.nextcloudlib.runtime.clients;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAdmin;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
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

    @Produces
    @RequestScoped
    @DefaultBean
    public Sardine getSardineInstance() {
        Sardine sardine = SardineFactory.begin(auth.getUser(), auth.getPassword());
        sardine.enablePreemptiveAuthentication(auth.getServer());
        sardine.enablePreemptiveAuthentication(auth.getServer().replace("https://", "").replace("http://", ""));
        return sardine;
    }

    @Produces
    @RequestScoped
    @NextcloudAdmin
    @DefaultBean
    public Sardine getSardineAdminInstance() {
        Sardine sardine = SardineFactory.begin(adminAuth.getUser(), adminAuth.getPassword());
        sardine.enablePreemptiveAuthentication(adminAuth.getServer());
        sardine.enablePreemptiveAuthentication(adminAuth.getServer().replace("https://", "").replace("http://", ""));
        return sardine;
    }

}
