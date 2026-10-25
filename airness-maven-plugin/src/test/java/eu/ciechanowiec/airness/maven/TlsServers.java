package eu.ciechanowiec.airness.maven;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;

/**
 * Local HTTPS servers on the loopback address, each on a port of its own, sharing one self-signed certificate
 * that a client built with {@link #context()} trusts.
 */
final class TlsServers implements AutoCloseable {

    private static final String PASSWORD = "loopback";
    private static final byte[] LOOPBACK = {127, 0, 0, 1};
    private static final Duration KEYTOOL_TIME = Duration.ofSeconds(60);

    private final SSLContext context;
    private final List<RecordingServer> servers;

    private TlsServers(SSLContext context) {
        this.context = context;
        this.servers = new ArrayList<>();
    }

    /**
     * Generates the certificate, for 127.0.0.1, into a key store in the directory.
     *
     * @param directory where the key store is written
     * @return the servers, none started yet
     */
    @SneakyThrows
    static TlsServers of(Path directory) {
        Path store = directory.resolve("loopback.p12");
        Process keytool = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair", "-alias", "loopback",
            "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "1", "-dname", "CN=127.0.0.1",
            "-ext", "SAN=ip:127.0.0.1", "-storetype", "PKCS12", "-keystore", store.toString(),
            "-storepass", PASSWORD, "-keypass", PASSWORD
        ).redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        if (!keytool.waitFor(KEYTOOL_TIME) || keytool.exitValue() != 0) {
            keytool.destroyForcibly();
            throw new IllegalStateException("keytool failed: " + Files.readString(directory.resolve("keytool.log")));
        }
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keys.load(input, PASSWORD.toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, PASSWORD.toCharArray());
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(keys);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        return new TlsServers(context);
    }

    /**
     * The TLS context a client trusting these servers uses.
     *
     * @return the context
     */
    SSLContext context() {
        return this.context;
    }

    /**
     * Starts one more server, on a port of its own.
     *
     * @return the server, which these servers close
     * @throws IOException when it cannot bind
     */
    RecordingServer server() throws IOException {
        HttpsServer server = HttpsServer.create(
            new InetSocketAddress(InetAddress.getByAddress(LOOPBACK), 0), 0
        );
        server.setHttpsConfigurator(new HttpsConfigurator(this.context));
        RecordingServer recording = new RecordingServer(server);
        this.servers.add(recording);
        return recording;
    }

    @Override
    public void close() {
        this.servers.forEach(RecordingServer::stop);
    }
}
