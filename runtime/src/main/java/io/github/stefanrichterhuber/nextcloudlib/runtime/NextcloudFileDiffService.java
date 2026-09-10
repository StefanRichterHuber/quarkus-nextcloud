package io.github.stefanrichterhuber.nextcloudlib.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.jboss.logging.Logger;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;

import io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService.NextCloudFileLock;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudFile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.tools.LineSeparatorDetector;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.tools.LineSeparatorDetector.LineSeparator;
import jakarta.activation.DataSource;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NextcloudFileDiffService {
    @Inject
    Logger logger;

    @Inject
    NextcloudFileService fileService;

    /**
     * Utility method to the detect the Charset of a file
     * 
     * @param content binary file content
     * @return Charset or null, if none detected
     */
    private Optional<Charset> detectCharset(byte[] content) {
        final CharsetDetector cd = new CharsetDetector();
        cd.setText(content);
        final CharsetMatch cm = cd.detect();
        if (cm != null) {
            final String cs = cm.getName();
            return Optional.ofNullable(Charset.forName(cs));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Utiltiy method to the detect the line seperator of a file
     * 
     * @param content
     * @return
     */
    private String detectLineSeparator(final String content) {
        LineSeparator ls = LineSeparatorDetector.detectDominantLineSeparator(content);
        switch (ls) {
            case UNIX:
                return "\n";
            case WINDOWS:
                return "\r\n";
            case OLD_MAC:
                return "\r";
            case MIXED:
                return "\n";
            case NONE:
                return "\n";
            default:
                return "\n";
        }
    }

    /**
     * Returns a git-style unified diff for the content differences between two
     * files.
     *
     * @param f1 First file
     * @param f2 Second file
     * @return Git-style unified diff string
     */
    public String getContentGitDiff(NextcloudFile f1, NextcloudFile f2) {
        Patch<String> patch = getContentPatch(f1, f2);
        return deltasToGitPatch(patch, f1.path(), f2.path());
    }

    /**
     * Return the differences in the file content (for text-files!) between two
     * files
     * 
     * @param f1 First file
     * @param f2 Second file
     * @return Patch found
     */
    public Patch<String> getContentPatch(NextcloudFile f1, NextcloudFile f2) {
        if (Objects.equals(f1, f2) || f1 == null || f2 == null) {
            return DiffUtils.diff(Collections.emptyList(), Collections.emptyList());
        }
        if (Objects.equals(f1.fileId(), f2.fileId()) && Objects.equals(f1.etag(), f2.etag())) {
            return DiffUtils.diff(Collections.emptyList(), Collections.emptyList());
        }

        final DataSource d1 = f1.dataSource();
        final DataSource d2 = f2.dataSource();

        final String contentType1 = d1.getContentType();
        final String contentType2 = d2.getContentType();

        if (contentType1.startsWith("text/") && contentType2.startsWith("text/")) {
            try (InputStream is1 = d1.getInputStream(); InputStream is2 = d2.getInputStream()) {
                final String c1 = IOUtils.toString(is1, StandardCharsets.UTF_8);
                final String c2 = IOUtils.toString(is2, StandardCharsets.UTF_8);

                final List<String> c1Lines = List.of(c1.split("\r?\n|\r"));
                final List<String> c2Lines = List.of(c2.split("\r?\n|\r"));

                final Patch<String> patch = DiffUtils.diff(c1Lines, c2Lines);
                return patch;
            } catch (Exception e) {
                throw new RuntimeException(e);

            }

        } else {
            this.logger.warnf("Tried to create patch for files (1: %s 2: %s) with content type `%s` and `%s`",
                    f1.path(), f2.path(),
                    d1.getContentType(), d2.getContentType());
            return DiffUtils.diff(Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * Serialises a list of diff deltas into a unified (git-style) patch string.
     *
     * <p>
     * The output follows the standard unified diff format:
     * 
     * <pre>
     * --- fileName1
     * +++ fileName2
     * &#64;@ -&lt;src-pos&gt;,&lt;src-size&gt; +&lt;tgt-pos&gt;,&lt;tgt-size&gt; @@
     * -removed line
     * +added line
     * </pre>
     *
     * @param patch     the patch object containing the differences
     * @param fileName1 label for the original file (used in the {@code ---} header
     *                  line).
     * @param fileName2 label for the modified file (used in the {@code +++} header
     *                  line).
     * @return the complete unified diff as a string.
     */
    public String deltasToGitPatch(Patch<String> patch, String fileName1, String fileName2) {
        return deltasToGitPatch(patch.getDeltas(), fileName1, fileName2);
    }

    /**
     * Serialises a list of diff deltas into a unified (git-style) patch string.
     *
     * <p>
     * The output follows the standard unified diff format:
     * 
     * <pre>
     * --- fileName1
     * +++ fileName2
     * &#64;@ -&lt;src-pos&gt;,&lt;src-size&gt; +&lt;tgt-pos&gt;,&lt;tgt-size&gt; @@
     * -removed line
     * +added line
     * </pre>
     *
     * @param deltas    the list of change deltas produced
     * @param fileName1 label for the original file (used in the {@code ---} header
     *                  line).
     * @param fileName2 label for the modified file (used in the {@code +++} header
     *                  line).
     * @return the complete unified diff as a string.
     */
    public String deltasToGitPatch(List<AbstractDelta<String>> deltas, String fileName1, String fileName2) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(fileName1).append("\n");
        sb.append("+++ ").append(fileName2).append("\n");

        for (AbstractDelta<String> delta : deltas) {
            sb.append("@@ -").append(delta.getSource().getPosition() + 1).append(",").append(delta.getSource().size())
                    .append(" +").append(delta.getTarget().getPosition() + 1).append(",")
                    .append(delta.getTarget().size()).append(" @@\n");
            for (String line : delta.getSource().getLines()) {
                sb.append("-").append(line).append("\n");
            }
            for (String line : delta.getTarget().getLines()) {
                sb.append("+").append(line).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Applies a git-style unified diff to the given file and uploads the file again
     * 
     * @param file      File to patch
     * @param gitDiff   Git-style unified diff string
     * @param fuzz      Fuzz factor for the patch. Roughly how many lines the patch
     *                  definition can be off from the actual source
     * @param lockToken Optional lock token to update a locked file
     * @throws IOException
     * @throws PatchFailedException
     */
    public void applyGitDiff(NextcloudFile file, String gitDiff, int fuzz, @Nullable String lockToken)
            throws IOException, PatchFailedException {
        final List<String> patchContent = List.of(gitDiff.split("\n"));
        final Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchContent);
        applyContentPatch(file, patch, fuzz, lockToken);
    }

    /**
     * Applies the given patch to the given file and uploads the file again
     * 
     * @param file      File to patch
     * @param patch     Patch to apply
     * @param lockToken Optional lock token to update a locked file
     * @param fuzz      Fuzz factor for the patch. Roughly how many
     *                  lines the patch definition can be off from the actual source
     * @throws IOException
     * @throws PatchFailedException
     */
    public void applyContentPatch(NextcloudFile file, Patch<String> patch, int fuzz,
            @Nullable String lockToken) throws IOException, PatchFailedException {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(patch, "patch must not be null");

        final DataSource ds = file.dataSource();
        final String fileName = file.path();
        final String contentType = ds.getContentType();
        final String etag = file.etag();

        if (contentType != null && contentType.startsWith("text/")) {
            try (InputStream is = ds.getInputStream()) {
                final byte[] rawContent = is.readAllBytes();
                final Charset charset = detectCharset(rawContent).orElseGet(() -> {
                    logger.warnf("Unable to detect charset of text file %s -> fall back to UTF-8", fileName);
                    return StandardCharsets.UTF_8;
                });
                final String content = new String(rawContent, charset);
                final String lineSplitter = detectLineSeparator(content);
                final List<String> contentLines = List.of(content.split("\r?\n|\r"));
                final List<String> patchedContentLines = fuzz > 0 ? patch.applyFuzzy(contentLines, fuzz)
                        : patch.applyTo(contentLines);
                // Write back file
                final String patchedContent = patchedContentLines.stream().collect(Collectors.joining(lineSplitter));
                final InputStream ois = new ByteArrayInputStream(patchedContent.getBytes(charset));

                fileService.uploadFile(fileName, contentType, ois, etag, lockToken);
            }
        } else {
            throw new PatchFailedException("File " + file.path() + " is not of content type text/*");
        }
    }

    /**
     * Applies the given patch to the given file and uploads the file again
     * 
     * @param file  File to patch
     * @param patch Patch to apply
     * @param fuzz  Fuzz factor for the patch. Roughly how many
     *              lines the patch definition can be off from the actual source
     * @throws IOException
     * @throws PatchFailedException
     */
    public void applyContentPatch(NextcloudFile file, Patch<String> patch, int fuzz)
            throws IOException, PatchFailedException {
        applyContentPatch(file, patch, fuzz, (String) null);
    }

    /**
     * Applies the given patch to the given file and uploads the file again
     * 
     * @param file  File to patch
     * @param patch Patch to apply
     * @param fuzz  Fuzz factor for the patch. Roughly how many
     *              lines the patch definition can be off from the actual
     *              source
     * @param lock  Optional Lock to transmit
     * @throws IOException
     * @throws PatchFailedException
     */
    public void applyContentPatch(NextcloudFile file, Patch<String> patch, int fuzz,
            @Nullable NextCloudFileLock lock) throws IOException, PatchFailedException {
        final String lockTocken = lock != null ? lock.token() : null;
        applyContentPatch(file, patch, fuzz, lockTocken);
    }
}
