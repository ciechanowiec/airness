package com.example.starter.identity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.starter.Caller;
import com.example.starter.DatabaseTest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@DatabaseTest
class UserPagesTest {

    private static final String USERS = "/users";

    private static final String USER = "USER";

    private final Users users;

    private final UserRepository repository;

    UserPagesTest(Users users, UserRepository repository) {
        this.users = users;
        this.repository = repository;
    }

    private static String landing(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION).orElse("");
    }

    private UUID reference(String email) {
        return this.repository.findByProfileEmail(email).orElseThrow().getReference();
    }

    private static String form(String fullName, String email, String role, String password) {
        return "profile.fullName=%s&profile.email=%s&role=%s&password=%s".formatted(
            Caller.encoded(fullName), Caller.encoded(email), role, Caller.encoded(password)
        );
    }

    @Test
    void showsEveryAccountToAnAdministrator(@LocalServerPort int port) {
        assertTrue(Caller.administrator(port).get(USERS).body().contains(Caller.ADMINISTRATOR));
    }

    @Test
    void refusesTheAccountsToAUser(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "curious", Role.USER);
        HttpResponse<String> answer = Caller.signedIn(port, email, Accounts.SECRET).get(USERS);
        assertEquals(HttpStatus.FORBIDDEN.value(), answer.statusCode());
    }

    @Test
    void admitsAnAccountThatCanThenSignIn(@LocalServerPort int port) {
        String email = Accounts.fresh("admitted");
        HttpResponse<String> answer = Caller.administrator(port)
            .post(USERS, form("Ada Lovelace", email, USER, Accounts.SECRET));
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/users?notice=CREATED")),
            () -> assertTrue(landing(new Caller(port).signIn(email, Accounts.SECRET)).endsWith("/"))
        );
    }

    @Test
    void refusesAFormWithEveryMistakeAtOnce(@LocalServerPort int port) {
        String page = Caller.administrator(port).post(USERS, form("", "not-an-address", "", "short")).body();
        assertAll(
            () -> assertTrue(page.contains("Give the name the account is shown under.")),
            () -> assertTrue(page.contains("This is not an email address.")),
            () -> assertTrue(page.contains("Choose what the account may do.")),
            () -> assertTrue(page.contains("A password is at least twelve characters long."))
        );
    }

    @Test
    void refusesAnAddressAnotherAccountSignsInWith(@LocalServerPort int port) {
        String page = Caller.administrator(port)
            .post(USERS, form("Copy", Caller.ADMINISTRATOR, USER, Accounts.SECRET))
            .body();
        assertTrue(page.contains("Another account already signs in with this address."));
    }

    @Test
    void opensTheFormOfAnAccount(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "opened", Role.USER);
        String page = Caller.administrator(port).get("/users/%s/edit".formatted(this.reference(email))).body();
        assertTrue(page.contains(email));
    }

    @Test
    void answersAnAccountNobodyCarriesAsMissing(@LocalServerPort int port) {
        HttpResponse<String> answer = Caller.administrator(port).get("/users/%s/edit".formatted(UUID.randomUUID()));
        assertEquals(HttpStatus.NOT_FOUND.value(), answer.statusCode());
    }

    @Test
    void restatesAnAccount(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "renamed", Role.USER);
        Caller.administrator(port)
            .post("/users/%s".formatted(this.reference(email)), form("Grace Hopper", email, USER, ""));
        assertEquals("Grace Hopper", this.repository.findByProfileEmail(email).orElseThrow().getProfile().fullName());
    }

    @Test
    void refusesARestatementToAnAddressAnotherAccountSignsInWith(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "clashing", Role.USER);
        String page = Caller.administrator(port)
            .post("/users/%s".formatted(this.reference(email)), form("Clash", Caller.ADMINISTRATOR, USER, ""))
            .body();
        assertTrue(page.contains("Another account already signs in with this address."));
    }

    @Test
    void keepsTheLastAdministratorAnAdministrator(@LocalServerPort int port) {
        UUID administrator = this.reference(Caller.ADMINISTRATOR);
        String page = Caller.administrator(port)
            .post("/users/%s".formatted(administrator), form("Administrator", Caller.ADMINISTRATOR, USER, ""))
            .body();
        assertTrue(page.contains("This is the last administrator who may sign in, so they stay one."));
    }

    @Test
    void offersNoControlThatWouldStopTheLastAdministrator(@LocalServerPort int port) {
        assertTrue(Caller.administrator(port).get(USERS).body().contains("The last administrator"));
    }

    @Test
    void stopsAnAccountSigningIn(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "disabled", Role.USER);
        HttpResponse<String> answer = Caller.administrator(port)
            .post("/users/%s/access".formatted(this.reference(email)), "allowed=false");
        assertAll(
            () -> assertTrue(landing(answer).endsWith("/users?notice=DISABLED")),
            () -> assertTrue(landing(new Caller(port).signIn(email, Accounts.SECRET)).endsWith("/login?failed=true"))
        );
    }

    @Test
    void letsAStoppedAccountSignInAgain(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "readmitted", Role.USER);
        Caller administrator = Caller.administrator(port);
        String access = "/users/%s/access".formatted(this.reference(email));
        administrator.post(access, "allowed=false");
        administrator.post(access, "allowed=true");
        assertTrue(landing(new Caller(port).signIn(email, Accounts.SECRET)).endsWith("/"));
    }

    @Test
    void endsTheSessionsOfAStoppedAccountAtOnce(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "ended", Role.USER);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        Caller.administrator(port).post("/users/%s/access".formatted(this.reference(email)), "allowed=false");
        assertTrue(landing(reader.get("/tasks")).endsWith("/login?ended=true"));
    }

    @Test
    void endsTheSessionsOfAnAccountWhoseAddressChanged(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "readdressed", Role.USER);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        Caller.administrator(port)
            .post("/users/%s".formatted(this.reference(email)), form("Moved", Accounts.fresh("moved"), USER, ""));
        assertTrue(landing(reader.get("/tasks")).endsWith("/login?ended=true"));
    }

    @Test
    void keepsTheSessionsOfAnAccountWhoseNameAloneChanged(@LocalServerPort int port) {
        String email = Accounts.admitted(this.users, "renamedOnly", Role.USER);
        Caller reader = Caller.signedIn(port, email, Accounts.SECRET);
        Caller.administrator(port).post(
            "/users/%s".formatted(this.reference(email)), form("Renamed", email, USER, "")
        );
        assertEquals(HttpStatus.OK.value(), reader.get("/tasks").statusCode());
    }

    @Test
    void refusesAPostThatCarriesNoToken(@LocalServerPort int port) {
        HttpResponse<String> answer = Caller.administrator(port)
            .forged(USERS, form("Forged", Accounts.fresh("forged"), USER, Accounts.SECRET));
        assertEquals(HttpStatus.FORBIDDEN.value(), answer.statusCode());
    }
}
