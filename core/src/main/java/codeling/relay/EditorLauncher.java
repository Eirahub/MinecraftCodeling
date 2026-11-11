package codeling.relay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Opens the editor without the token in any process command line (issue #191). The editor URL holds the token, and
 * xdg-open, open, rundll32 and the browser would get it in argv, which other local users can read with ps. Instead a
 * one-time page in a directory only this user can read redirects the browser to the URL, and the browser is given the
 * file path. Jupyter's redirect file works the same way.
 */
public final class EditorLauncher {
    /** Where launchers go: the user's temp directory (per user on Windows and macOS, shared /tmp on Linux). */
    public static final Path DIR = Path.of(System.getProperty("java.io.tmpdir"));
    static final String PREFIX = "codeling-editor-";
    static final String FILE = "open.html";
    /** Long enough for a browser that starts slowly; the file is deleted then. */
    static final long LIFETIME_SECONDS = 60;

    private EditorLauncher() {
    }

    /** Writes a launcher for url under parent, hands its path to open and deletes it after {@link #LIFETIME_SECONDS}. */
    public static Path launch(Path parent, String url, Consumer<Path> open) throws IOException {
        Path file = write(parent, url);
        open.accept(file);
        CompletableFuture.delayedExecutor(LIFETIME_SECONDS, TimeUnit.SECONDS).execute(() -> delete(file));
        return file;
    }

    /**
     * A new launcher page for url, in a new directory under parent that only its owner can read: 0700 and 0600 set at
     * creation on POSIX, an owner-only ACL on the still empty directory on Windows. The file is created new, so a file
     * someone else put there first fails the launch instead of receiving the token.
     */
    static Path write(Path parent, String url) throws IOException {
        boolean posix = parent.getFileSystem().supportedFileAttributeViews().contains("posix");
        Path dir;
        if (posix) {
            dir = Files.createTempDirectory(parent, PREFIX, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } else {
            dir = Files.createTempDirectory(parent, PREFIX);
            ownerOnly(dir);
        }
        Path file = dir.resolve(FILE);
        if (posix) Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        else Files.createFile(file); // inherits the directory's owner-only ACL
        Files.write(file, html(url).getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** The page: replaces itself with url (no history entry), a meta refresh without script, and a link as the last resort. */
    static String html(String url) {
        String attr = escapeHtml(url);
        return "<!doctype html>\n<html><head><meta charset=\"utf-8\">\n"
                + "<meta name=\"referrer\" content=\"no-referrer\">\n"
                + "<meta http-equiv=\"refresh\" content=\"0;url=" + attr + "\">\n"
                + "<title>Codeling</title>\n"
                + "<script>location.replace(\"" + escapeJs(url) + "\");</script>\n"
                + "</head><body><p><a href=\"" + attr + "\">Open the Codeling editor</a></p></body></html>\n";
    }

    // Windows: replaces the ACL inherited from %TEMP% (which may grant more than the user, SYSTEM and Administrators)
    // with one entry for the owner, inherited by the file made inside.
    private static void ownerOnly(Path dir) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(dir, AclFileAttributeView.class);
        if (view == null) return;
        view.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(view.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build()));
    }

    /** Deletes a launcher and its directory. Never fails: a file that is gone already is fine. */
    public static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        } catch (IOException | RuntimeException ignored) {
            // another user's or already gone; the next start tries again
        }
    }

    /** Deletes every launcher left under parent, for example by a crash. Other users' launchers cannot be deleted and stay. */
    public static void deleteAll(Path parent) {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(parent, PREFIX + "*")) {
            // deleteIfExists does not follow links, so a planted link removes only itself.
            for (Path dir : dirs) if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) delete(dir.resolve(FILE));
        } catch (IOException | RuntimeException ignored) {
            // no temp directory: nothing to clean
        }
    }

    static String escapeHtml(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    // Everything but plain URL characters as \\uXXXX, so no quote, backslash, < or line break can end the string or the script.
    static String escapeJs(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || ":/.#=_-?".indexOf(c) >= 0) b.append(c);
            else b.append(String.format("\\u%04x", (int) c));
        }
        return b.toString();
    }
}
