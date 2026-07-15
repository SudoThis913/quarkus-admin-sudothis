package com.sudothis.security;

import com.sudothis.model.AppUser;
import com.sudothis.model.ImpersonationSession;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class ImpersonationContext {

    private AppUser impersonator;
    private AppUser target;
    private ImpersonationSession session;

    public boolean isActive() {
        return session != null;
    }

    public AppUser getImpersonator() { return impersonator; }
    public AppUser getTarget() { return target; }
    public ImpersonationSession getSession() { return session; }

    public void setImpersonator(AppUser impersonator) { this.impersonator = impersonator; }
    public void setTarget(AppUser target) { this.target = target; }
    public void setSession(ImpersonationSession session) { this.session = session; }
}
