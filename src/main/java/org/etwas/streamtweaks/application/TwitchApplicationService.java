package org.etwas.streamtweaks.application;

import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import org.etwas.streamtweaks.twitch.auth.AuthenticationOrchestrator;
import org.etwas.streamtweaks.twitch.auth.AuthenticationResult;
import org.etwas.streamtweaks.twitch.core.Login;
import org.etwas.streamtweaks.twitch.core.TwitchApiClient;
import org.etwas.streamtweaks.twitch.subscription.EventSubOrchestrator;

public final class TwitchApplicationService {
    private final AuthenticationOrchestrator authService;
    private final EventSubOrchestrator subscriptionService;
    private final TwitchApiClient apiClient;
    private final Set<Login> subscribedLogins = new CopyOnWriteArraySet<>();

    public TwitchApplicationService(
            AuthenticationOrchestrator authService,
            EventSubOrchestrator subscriptionService,
            TwitchApiClient apiClient) {
        this.authService = authService;
        this.subscriptionService = subscriptionService;
        this.apiClient = apiClient;
    }

    public CompletableFuture<AuthenticationResult> login(Consumer<URI> onUriReady) {
        return authService.startAuthentication(onUriReady);
    }

    public boolean isAuthenticated() {
        return authService.hasCredential();
    }

    /**
     * Returns the login name of the authenticated user, or null if not authenticated.
     */
    public String getAuthenticatedLogin() {
        return authService.getAuthenticatedDisplayName().orElse(null);
    }

    public CompletableFuture<Void> connect(Login login) {
        return apiClient
                .getUserId(login)
                .thenCompose(subscriptionService::subscribe)
                .thenApply(v -> {
                    subscribedLogins.add(login);
                    return v;
                });
    }

    public CompletableFuture<Void> disconnect(Login login) {
        return apiClient
                .getUserId(login)
                .thenCompose(subscriptionService::unsubscribe)
                .thenApply(v -> {
                    subscribedLogins.remove(login);
                    return v;
                });
    }

    public CompletableFuture<Void> logout() {
        return subscriptionService.unsubscribeAll().thenRun(() -> {
            subscribedLogins.clear();
            subscriptionService.close();
            authService.logout();
        });
    }

    public Set<Login> getSubscribedLogins() {
        return Collections.unmodifiableSet(subscribedLogins);
    }
}
