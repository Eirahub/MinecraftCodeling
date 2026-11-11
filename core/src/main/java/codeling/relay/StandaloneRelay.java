package codeling.relay;

import java.nio.file.Path;

/**
 * Runs the editor + relay server without Minecraft, for headless tests and target development.
 * Port: first argument or system property codeling.port (default {@value RelayServer#DEFAULT_PORT}).
 * With --print-url it prints the editor URL including this run's token to stdout, the stand-in for
 * the in-game "open editor" action. The token dies with the process. With --print-launcher it writes the launcher
 * page that /codeling opens (issue #191) and prints its path, so the editor suite can open it as the game does.
 */
public final class StandaloneRelay {
    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("codeling.port", RelayServer.DEFAULT_PORT);
        boolean printUrl = false;
        boolean printLauncher = false;
        for (String a : args) {
            if (a.equals("--print-url")) printUrl = true;
            else if (a.equals("--print-launcher")) printLauncher = true;
            else port = Integer.parseInt(a);
        }
        RelayServer server = new RelayServer(port).start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("Codeling relay on http://127.0.0.1:" + port + "/ (token " + server.tokenHint() + ")");
        if (printUrl) System.out.println("EDITOR_URL " + server.editorUrl());
        if (printLauncher) {
            Path launcher = EditorLauncher.launch(EditorLauncher.DIR, server.editorUrl(), path -> { });
            Runtime.getRuntime().addShutdownHook(new Thread(() -> EditorLauncher.delete(launcher)));
            System.out.println("EDITOR_LAUNCHER " + launcher);
        }
        Thread.currentThread().join();
    }
}
