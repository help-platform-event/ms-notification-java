package com.maxime.help.msnotification.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** The decoder against real HS256 tokens, signed the way ms-auth signs them. */
class JwtDecoderTest {

    private static final String SECRET = "c2FtcGxlLWRldi1vbmx5LXNlY3JldC1kby1ub3QtdXNlLWluLXByb2QtMzJieXRlcyE=";
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("another-secret-that-is-32-bytes!".getBytes());

    private final JwtDecoder decoder = new SecurityConfig().jwtDecoder(SECRET);

    @Test
    void aTokenSignedWithTheSharedSecret_isAccepted() throws Exception {
        assertThat(decoder.decode(token(SECRET, Instant.now().plusSeconds(60))).getSubject()).isEqualTo("user-1");
    }

    @Test
    void aTokenSignedWithAnotherSecret_isRejected() throws Exception {
        String forged = token(OTHER_SECRET, Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void anExpiredToken_isRejected() throws Exception {
        String expired = token(SECRET, Instant.now().minusSeconds(3600));

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void withoutASecret_theServiceRefusesToStart() {
        assertThatThrownBy(() -> new SecurityConfig().jwtDecoder(" ")).isInstanceOf(IllegalStateException.class);
    }

    private static String token(String base64Secret, Instant expiresAt) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().subject("user-1").expirationTime(Date.from(expiresAt)).build());
        jwt.sign(new MACSigner(Base64.getDecoder().decode(base64Secret)));
        return jwt.serialize();
    }
}
