package com.sudothis.security;

import com.sudothis.service.SessionService;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;   // ← correct
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.*;                                // ← resolves with vertx-web
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class SessionAuthenticator implements HttpAuthenticationMechanism {

    private static final SecurityIdentity ANON = QuarkusSecurityIdentity.builder()
            .setAnonymous(true)
            .setPrincipal(() -> "anonymous")
            .build();

    @Inject SessionService sessions;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext ctx, IdentityProviderManager idp) {
        String sid = Optional.ofNullable(ctx.getCookie("sudothis_session"))
                             .map(Cookie::getValue)
                             .orElse(null);
        if (sid == null) return Uni.createFrom().item(ANON);

        SecurityIdentity id = sessions.identityFromRedis(sid);
        if (id != null) return Uni.createFrom().item(id);

        return Uni.createFrom().completionStage(
                sessions.identityFromDatabase(sid).thenApply(i -> {
                    if (i != null) sessions.cacheIdentity(sid, i);
                    return i != null ? i : ANON;
                }));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext ctx) {
        return Uni.createFrom().nullItem();
    }
}