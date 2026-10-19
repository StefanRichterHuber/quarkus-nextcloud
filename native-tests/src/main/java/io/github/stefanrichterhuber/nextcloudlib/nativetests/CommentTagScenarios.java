package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.ROOT_DIR;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.TEXT1;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.newFileName;
import static io.github.stefanrichterhuber.nextcloudlib.nativetests.TestData.stream;

import java.util.List;
import java.util.UUID;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCommentService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudSystemTagService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.Comment;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.SystemTag;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * Comment and system tag scenarios: Sardine REPORT / PROPPATCH with OC / NC
 * namespaced properties.
 */
@Path("/native")
public class CommentTagScenarios {
    @Inject
    NextcloudFileService fileService;

    @Inject
    NextcloudCommentService commentService;

    @Inject
    NextcloudSystemTagService tagService;

    @POST
    @Path("/comments")
    public void comments() throws Exception {
        fileService.createDirectories(ROOT_DIR);
        final String filename = newFileName();
        fileService.uploadFile(filename, "text/markdown", stream(TEXT1));
        try {
            final NextcloudFile file = fileService.getFile(filename);
            Check.isTrue(commentService.getCommentsOfFile(file).isEmpty(), "New file has comments");

            final String message = "Native comment " + UUID.randomUUID();
            commentService.addCommentToFile(file, message);

            final List<Comment> comments = commentService.getCommentsOfFile(file);
            Check.isTrue(comments.stream().anyMatch(c -> c.message().equals(message)), "Added comment not found");

            for (Comment c : comments) {
                commentService.deleteComment(c);
            }
            Check.isTrue(commentService.getCommentsOfFile(file).isEmpty(), "Deleted comments still found");
        } finally {
            fileService.deleteFile(filename);
        }
    }

    @POST
    @Path("/system-tags")
    public void systemTags() throws Exception {
        final String tagName = "native-" + UUID.randomUUID();
        final SystemTag tag = Check.notNull(tagService.addSystemTag(tagName, true, true, true), "Created tag");
        Check.isTrue(tagService.listSystemTags().stream().anyMatch(t -> t.displayName().equals(tagName)),
                "Created tag not listed");

        fileService.createDirectories(ROOT_DIR);
        final String filename = newFileName();
        fileService.uploadFile(filename, "text/markdown", stream(TEXT1));
        try {
            final NextcloudFile file = fileService.getFile(filename);
            tagService.addTagToFile(file, tag);
            Check.isTrue(tagService.listSystemTagsOfFile(file).contains(tag), "Tag not assigned to file");
            Check.isTrue(fileService.listFilesBySystemTag(ROOT_DIR, -1, tag).stream()
                    .anyMatch(f -> f.path().endsWith(filename.substring(1))), "File not found by tag");

            tagService.removeTagFromFile(file, tag);
            Check.isFalse(tagService.listSystemTagsOfFile(file).contains(tag), "Tag still assigned to file");
        } finally {
            fileService.deleteFile(filename);
        }
    }
}
