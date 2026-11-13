package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

// What a browser asks for before anybody signs in. A request the chain closes is saved as the page a sign-in
// returns to, so an asset the page links that only a signed-in reader may fetch sends the reader to the asset.
@DatabaseTest
class PublicAddressesTest {

    private static final Pattern ICON = Pattern.compile("<link rel=\"icon\"[^>]*href=\"([^\"]+)\"");

    @Test
    void servesTheIconThePageLinksToSomebodyWhoHasNotSignedIn(@LocalServerPort int port) {
        Caller caller = new Caller(port);
        Matcher icon = ICON.matcher(caller.get(LoginPages.LOGIN).body());
        assertTrue(icon.find());
        assertEquals(HttpStatus.OK.value(), caller.get(icon.group(1)).statusCode());
    }
}
