package codeling.relay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Issue #191: the editor opens through a one-time launcher file, so the token is never in a command line. */
class EditorLauncherTest {
    @TempDir
    Path tmp;

    @Test
    void launcherRedirectsToTheEditorUrlAndIsHandedOverAsAPath() throws Exception {
        String url = new RelayServer(25600).editorUrl();
        AtomicReference<Path> opened = new AtomicReference<>();
        Path file = EditorLauncher.launch(tmp, url, opened::set);
        assertEquals(file, opened.get());
        assertFalse(file.toUri().toString().contains("local_token"), "the opened path must not hold the token");
        assertTrue(file.getParent().getFileName().toString().startsWith(EditorLauncher.PREFIX));
        String html = Files.readString(file, StandardCharsets.UTF_8);
        // A real editor URL has only URL-safe characters, so it is written unchanged in all three places.
        assertTrue(html.contains("<meta http-equiv=\"refresh\" content=\"0;url=" + url + "\">"), html);
        assertTrue(html.contains("location.replace(\"" + url + "\")"), html);
        assertTrue(html.contains("<a href=\"" + url + "\">"), html);
    }

    @Test
    void urlIsEscapedForHtmlAndScript() {
        String html = EditorLauncher.html("http://x/#a\"b'<c>&d\\e</script>\n");
        assertTrue(html.contains("content=\"0;url=http://x/#a&quot;b&#39;&lt;c&gt;&amp;d\\e&lt;/script&gt;\n\""), html);
        assertTrue(html.contains("location.replace(\"http://x/#a\\u0022b\\u0027\\u003cc\\u003e\\u0026d\\u005ce\\u003c/script\\u003e\\u000a\")"), html);
        assertEquals(1, html.split("</script>", -1).length - 1, "only the page's own script end tag");
    }

    @Test
    void onlyTheOwnerCanReadTheLauncher() throws Exception {
        Path file = EditorLauncher.write(tmp, "http://127.0.0.1:1/#local_token=t");
        if (tmp.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.getParent())));
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        } else {
            // Windows: one entry, for the owner, on the directory and (inherited) on the file.
            for (Path p : List.of(file.getParent(), file)) {
                AclFileAttributeView view = Files.getFileAttributeView(p, AclFileAttributeView.class);
                assertEquals(List.of(view.getOwner()), view.getAcl().stream().map(AclEntry::principal).toList(), p.toString());
            }
        }
    }

    @Test
    void deleteRemovesFileAndDirectoryAndDeleteAllCleansLeftovers() throws Exception {
        Path one = EditorLauncher.write(tmp, "http://127.0.0.1:1/");
        EditorLauncher.delete(one);
        assertFalse(Files.exists(one.getParent()));
        EditorLauncher.delete(one); // twice is fine

        Path left = EditorLauncher.write(tmp, "http://127.0.0.1:1/");
        Path other = Files.createDirectory(tmp.resolve("not-ours"));
        EditorLauncher.deleteAll(tmp);
        assertFalse(Files.exists(left.getParent()));
        assertTrue(Files.exists(other), "only launcher directories are cleaned");
    }
}
