package com.sudothis.service;

import com.sudothis.model.AppUser;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.*;

@ApplicationScoped
public class UserService {

    private static final Argon2 A2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private static final int ITER = 4;          // time cost
    private static final int MEM  = 1 << 16;    // 65 536 KiB
    private static final int PAR  = 2;          // parallelism

    @Inject EntityManager em;

    /* ---------- AUTH ---------- */
    @Transactional
    public AppUser authenticate(String username, char[] pw) {
        AppUser u = findByUsername(username).orElseThrow(() -> new SecurityException("Bad credentials"));
        if (!verify(pw, u.passwordHash)) throw new SecurityException("Bad credentials");
        return u;
    }

    /* --------- REGISTER -------- */
    @Transactional
    public AppUser registerUser(char[] pw, String username, String email, AppUser.UserType type) {
        AppUser u = new AppUser();
        u.username = username;
        u.email    = email;
        u.passwordHash = hash(pw);
        u.userType = type;
        em.persist(u);
        return u;
    }

    /* ---------- HELPERS -------- */
    public Optional<AppUser> findByUsername(String u) {
        return em.createQuery("FROM AppUser WHERE username = :u", AppUser.class)
                 .setParameter("u", u)
                 .getResultStream()
                 .findFirst();
    }
    public Optional<AppUser> findByEmail(String e) {
        return em.createQuery("FROM AppUser WHERE email = :e", AppUser.class)
                 .setParameter("e", e)
                 .getResultStream()
                 .findFirst();
    }
    public Optional<AppUser> findByIdOptional(Long id) {
        return Optional.ofNullable(em.find(AppUser.class, id));
    }
    @Transactional public void persist(AppUser u) { em.persist(u); }

    /* ------- HASH / VERIFY ----- */
    public String hash(char[] pw) {
        try {
            String raw = A2.hash(ITER, MEM, PAR, pw);
            return String.format("argon2id$%d$%d$%d$%s", ITER, MEM, PAR, raw);
        } finally { Arrays.fill(pw, '\0'); }
    }
    private boolean verify(char[] pw, String stored) {
        try {
            String[] p = stored.split("\\$", 5);
            return A2.verify(p[4], pw);
        } finally { Arrays.fill(pw, '\0'); }
    }
}