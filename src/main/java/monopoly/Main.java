package monopoly;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import monopoly.web.GameServer;

/** Entry point. Deliberately tiny: all the logic lives in the classes it creates. */
public final class Main {
    private Main() { }

    public static void main(String[] args) throws IOException {
        boolean lan = Arrays.asList(args).contains("--lan");
        boolean openBrowser = !Arrays.asList(args).contains("--no-browser");
        int port = Arrays.stream(args).filter(a -> a.startsWith("--port="))
                .map(a -> Integer.parseInt(a.substring(7))).findFirst().orElse(8080);
        new GameServer(lan, port).start();
        String url = "http://localhost:" + port;
        System.out.println("Monopoly España -> " + url);
        if (lan) {
            Collections.list(NetworkInterface.getNetworkInterfaces()).forEach(nic -> Collections.list(nic.getInetAddresses()).stream()
                    .filter(a -> a instanceof Inet4Address && !a.isLoopbackAddress())
                    .forEach(a -> System.out.println("Desde el móvil (misma wifi): http://" + a.getHostAddress() + ":" + port)));
        }
        System.out.println("Cierra esta ventana para parar el servidor. Tu partida se guarda sola.");
        if (openBrowser && !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (IOException | UnsupportedOperationException e) {
                // no browser available: the URL printed above is enough
            }
        }
    }
}
