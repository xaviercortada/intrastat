package cat.alkaid.intrastat.auth;


import cat.alkaid.intrastat.model.Account;
import cat.alkaid.intrastat.service.AccountService;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Set;

/**
 * Created by xavier on 21/07/15.
 */

@Stateless
@Local
public class AuthService {
    @EJB
    private AccountService service;

    private static RsaJsonWebKey webKey = null;

    private RsaJsonWebKey getWebKeyInstance() throws JoseException {
        if(webKey == null) {
            webKey = RsaJwkGenerator.generateJwk(2048);
            webKey.setKeyId("k1");
        }

        return webKey;

    }

    public AuthAccessElement Login(AuthLoginElement loginElement) throws JoseException {
        Account accountRegistration = service.findByUsername(loginElement.getUsername());
        if(accountRegistration != null && accountRegistration.getActivated() != null){
            if(accountRegistration.validate(loginElement.getPassword())){
                AuthAccessElement accessElement = new AuthAccessElement(loginElement.getUsername());
                String token = getToken(accountRegistration);
                //accountRegistration.putToken(token);
                accessElement.setAuthToken(token);
                service.update(accountRegistration);
                return accessElement;
            }
        }
        return null;
    }


    public void isAuthorized(String authId, String authToken, Set<String> role) throws JoseException {
        try {
            JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                    .setRequireExpirationTime() // the JWT must have an expiration time
                    .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                    .setRequireSubject() // the JWT must have a subject claim
                    .setExpectedIssuer(authId) // whom the JWT needs to have been issued by
                    .setExpectedAudience("Audience") // to whom the JWT is intended for
                    .setVerificationKey(getWebKeyInstance().getKey()) // verify the signature with the public key
                    .build(); //

            JwtClaims jwtClaims = jwtConsumer.processToClaims(authToken);


        } catch (InvalidJwtException e) {
            e.printStackTrace();


        }



    }

    public SecurityContext  getSecurityContext(String authId, String authToken, Set<String> rolesAllowed) {
        final Account accountRegistration = service.findByUsername(authId);
        if(accountRegistration != null){
            if(accountRegistration.isAuthorized(authToken)){
                SecurityContext securityCtx = new SecurityContext() {
                    @Override
                    public Principal getUserPrincipal() {
                        return new Principal() {
                            @Override
                            public String getName() {
                                return accountRegistration.getName();
                            }
                        };
                    }

                    @Override
                    public boolean isUserInRole(String s) {
                        return true;
                    }

                    @Override
                    public boolean isSecure() {
                        return true;
                    }

                    @Override
                    public String getAuthenticationScheme() {
                        return "authSchema";
                    }
                };
                return securityCtx;
            }
        }
        return null;
    }

    private String getToken(Account account) throws JoseException {

        JwtClaims claims = new JwtClaims();
        claims.setIssuer(account.getUsername());
        claims.setAudience("Audience");
        claims.setExpirationTimeMinutesInTheFuture(60);
        claims.setGeneratedJwtId();
        claims.setIssuedAtToNow();
        claims.setSubject("intrastat");

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(getWebKeyInstance().getPrivateKey());
        jws.setKeyIdHeaderValue(getWebKeyInstance().getKeyId());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime() // the JWT must have an expiration time
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setExpectedIssuer(account.getUsername()) // whom the JWT needs to have been issued by
                .setExpectedAudience("Audience") // to whom the JWT is intended for
                .setVerificationKey(getWebKeyInstance().getKey()) // verify the signature with the public key
                .build(); // create the JwtConsumer instance

            String token = jws.getCompactSerialization();

        try
        {
            //  Validate the JWT and process it to the Claims
            JwtClaims jwtClaims = jwtConsumer.processToClaims(token);
            System.out.println("JWT validation succeeded! " + jwtClaims);
        }
        catch (InvalidJwtException e)
        {
            // InvalidJwtException will be thrown, if the JWT failed processing or validation in anyway.
            // Hopefully with meaningful explanations(s) about what went wrong.
            System.out.println("Invalid JWT! " + e);
        }

        return token;
    }

}
