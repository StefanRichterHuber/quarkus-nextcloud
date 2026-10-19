package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.ROOT_DIR;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.TEXT1;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.newFileName;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.stream;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileDiffService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudUser;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * Scenarios with a lower risk, but cheap to cover: diffs (java-diff-utils,
 * Tika) and the user info mapped into a security identity (manual Jackson
 * conversion).
 */
@Path("/native")
public class DiffUserScenarios {
    @Inject
    NextcloudFileService fileService;

    @Inject
    NextcloudFileDiffService diffService;

    @Inject
    NextcloudUserService userService;

    @Inject
    NextcloudAuthProvider authProvider;

    @POST
    @Path("/diff")
    public void diff() throws Exception {
        fileService.createDirectories(ROOT_DIR);
        final String changed = TEXT1.replace("# Ode to the Cloud", "# Hello to the cloud");
        final String filename1 = newFileName();
        final String filename2 = newFileName();
        fileService.uploadFile(filename1, "text/markdown", stream(TEXT1));
        fileService.uploadFile(filename2, "text/markdown", stream(changed));
        try {
            final NextcloudFile file1 = fileService.getFile(filename1);
            final Patch<String> patch = diffService.getContentPatch(file1, fileService.getFile(filename2));
            final String gitDiff = diffService.deltasToGitPatch(patch, filename1, filename2);

            diffService.applyContentPatch(file1, UnifiedDiffUtils.parseUnifiedDiff(List.of(gitDiff.split("\n"))), 1);
            Check.equal(changed.trim(),
                    fileService.getFile(filename1).readToString(StandardCharsets.UTF_8).trim(), "Patched content");
        } finally {
            fileService.deleteFile(filename1);
            fileService.deleteFile(filename2);
        }
    }

    @POST
    @Path("/user")
    public void user() throws Exception {
        final String user = Check.notNull(authProvider.getUser(), "Configured user");
        final NextcloudUser info = Check.notNull(userService.getCurrentUserInfo(), "Current user info");
        Check.equal(user, info.id(), "Id of current user");
        Check.equal(user, userService.getUserInfo(user).id(), "Id of user info");

        final SecurityIdentity identity = userService.getSecurityIdentity(info, authProvider.getCredentials());
        Check.equal(user, identity.getPrincipal().getName(), "Principal name");
        Check.isTrue(identity.hasRole("admin"), "Dev service user is not in group admin");
    }
}
