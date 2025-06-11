package com.sudothis.controller;

import com.sudothis.model.AppUser;
import com.sudothis.service.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.security.SecureRandom;
import java.util.*;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthController {

    @Inject UserService userService;
    @Inject SessionService sessions;

    private static final SecureRandom RAND = new SecureRandom();

    /* ----- LOGIN ----- */
    @POST @Path("/login")
    public Response login(Credentials c, @Context HttpHeaders hdr) {
        AppUser user = userService.authenticate(c.username, c.password);
        String csrf = Base64.getUrlEncoder().withoutPadding().encodeToString(RAND.generateSeed(16));
        String ip   = Optional.ofNullable(hdr.getHeaderString("X-Forwarded-For")).orElse("0.0.0.0");
        String sid  = sessions.createSession(user, ip, csrf);
        String setCookie = String.format(
            "sudothis_session=%s; Path=/; Max-Age=%d; Secure; HttpOnly; SameSite=Strict",
            sid, SessionService.TTL.toSeconds());
        return Response.ok(Map.of("csrf", csrf)).header("Set-Cookie", setCookie).build();
    }

    /* ----- LOGOUT ---- */
    @POST @Path("/logout")
    public Response logout(@CookieParam("sudothis_session") String sid) {
        if (sid != null) sessions.invalidate(sid);
        String expired = "sudothis_session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict";
        return Response.noContent().header("Set-Cookie", expired).build();
    }

    public static class Credentials { public String username; public char[] password; }
}