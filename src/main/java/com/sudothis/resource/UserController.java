// path: src/main/java/com/sudothis/resource/UserController.java
package com.sudothis.resource;

import com.sudothis.model.AppUser;
import com.sudothis.model.AppUser.UserType;
import com.sudothis.service.UserService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Path("/user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserController {

    @Inject
    UserService userService;

    @Inject
    RedisDataSource redisDS;

    private static final Pattern EMAIL_REGEX = Pattern.compile("^(?:[a-zA-Z0-9_'^&amp;/+-])+(?:\\.(?:[a-zA-Z0-9_'^&amp;/+-]+))*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$");
    private static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(48);
    private static final Duration EMAIL_RESEND_LIMIT = Duration.ofMinutes(15);

    public static class RegistrationRequest {
        public String username;
        public String email;
        public String password;
    }

    public static class ResendRequest {
        public String email;
    }

    @POST
    @Path("/register")
    @Transactional
    public Response registerUser(RegistrationRequest request, @Context HttpHeaders headers) {
        if (request.username == null || request.email == null || request.password == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing fields").build();
        }

        if (!EMAIL_REGEX.matcher(request.email).matches()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid email format").build();
        }

        String ip = headers.getHeaderString("X-Forwarded-For");
        if (ip == null) ip = "unknown";
        String rateKey = "register_attempts:" + ip;

        ValueCommands<String, String> redis = redisDS.value(String.class);
        String val = redis.get(rateKey);
        int attempts = val == null ? 0 : Integer.parseInt(val);

        if (attempts >= 20) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS).entity("Rate limit exceeded. Try again later.").build();
        }
        redis.setex(rateKey, 60, String.valueOf(attempts + 1));

        if (userService.findByUsername(request.username).isPresent()) {
            return Response.status(Response.Status.CONFLICT).entity("Username already exists").build();
        }
        if (userService.findByEmail(request.email).isPresent()) {
            return Response.status(Response.Status.CONFLICT).entity("Email already exists").build();
        }

        AppUser user = userService.registerUser(
                request.password.toCharArray(),
                request.username,
                request.email,
                UserType.USER
        );

        String token = UUID.randomUUID().toString();
        String redisKey = "verify_email:" + token;
        redis.setex(redisKey, EMAIL_TOKEN_TTL.getSeconds(), String.valueOf(user.id));
        redis.setex("verify_lookup:uid:" + user.id, EMAIL_TOKEN_TTL.getSeconds(), token);
        redis.setex("verify_cooldown:" + user.id, EMAIL_RESEND_LIMIT.getSeconds(), "1");

        sendStubValidationEmail(user.email, token);
        return Response.status(Response.Status.CREATED).entity("User registered. Check your email to verify.").build();
    }

    @POST
    @Path("/resend-email")
    @Transactional
    public Response resendEmailVerification(ResendRequest request) {
        if (request.email == null || !EMAIL_REGEX.matcher(request.email).matches()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid email").build();
        }

        Optional<AppUser> userOpt = userService.findByEmail(request.email);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity("Email not found").build();
        }

        AppUser user = userOpt.get();
        if (user.enabled) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Account already verified").build();
        }

        ValueCommands<String, String> redis = redisDS.value(String.class);
        String cooldownKey = "verify_cooldown:" + user.id;
        if (redis.get(cooldownKey) != null) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS).entity("Wait before resending.").build();
        }

        String priorTokenKey = "verify_lookup:uid:" + user.id;
        String oldToken = redis.get(priorTokenKey);
        if (oldToken != null) {
            redis.setex("verify_email:" + oldToken, 1, ""); // expire old token
        }

        String newToken = UUID.randomUUID().toString();
        redis.setex("verify_email:" + newToken, EMAIL_TOKEN_TTL.getSeconds(), String.valueOf(user.id));
        redis.setex(priorTokenKey, EMAIL_TOKEN_TTL.getSeconds(), newToken);
        redis.setex(cooldownKey, EMAIL_RESEND_LIMIT.getSeconds(), "1");

        sendStubValidationEmail(user.email, newToken);
        return Response.ok("Verification email resent").build();
    }

    @GET
    @Path("/emailverify")
    @Transactional
    public Response verifyEmail(@QueryParam("token") String token) {
        if (token == null || token.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing token").build();
        }

        ValueCommands<String, String> redis = redisDS.value(String.class);
        String userIdStr = redis.get("verify_email:" + token);
        if (userIdStr == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid or expired token").build();
        }

        Long userId = Long.parseLong(userIdStr);
        Optional<AppUser> userOpt = userService.findByIdOptional(userId);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        AppUser user = userOpt.get();
        user.enabled = true;
       userService.persist(user);

        redis.setex("verify_email:" + token, 1, ""); // expire

        return Response.ok("Email verified. You may now log in.").build();
    }

    private void sendStubValidationEmail(String email, String token) {
        String link = "https://sudothis.com/verify?token=" + token;
        System.out.printf("Stub: send verification link to %s\nLink: %s%n", email, link);
    }
}
