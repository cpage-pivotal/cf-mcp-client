package org.tanzu.mcpclient.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    @GetMapping("/status")
    public Map<String, Object> getAuthStatus(
            @AuthenticationPrincipal Object principal) {
        Map<String, Object> status = new HashMap<>();

        if (principal instanceof OAuth2User oAuth2User) {
            status.put("authenticated", true);
            status.put("username", oAuth2User.getName());
            status.put("email", oAuth2User.getAttribute("email"));
            String displayName = oAuth2User.getAttribute("name");
            if (displayName == null) {
                displayName = oAuth2User.getAttribute("given_name");
            }
            status.put("displayName", displayName);
        } else if (principal instanceof UserDetails userDetails) {
            status.put("authenticated", true);
            status.put("username", userDetails.getUsername());
            status.put("email", null);
            status.put("displayName", userDetails.getUsername());
        } else {
            status.put("authenticated", false);
        }

        return status;
    }

    @GetMapping("/provider")
    public Map<String, Object> getProvider() {
        Map<String, Object> result = new HashMap<>();

        if (clientRegistrationRepository != null) {
            // Check for common CF identity provider registration IDs
            for (String registrationId : new String[]{"sso", "uaa", "pivotal-sso"}) {
                try {
                    ClientRegistration registration =
                            clientRegistrationRepository.findByRegistrationId(registrationId);
                    if (registration != null) {
                        result.put("provider", registration.getClientName());
                        result.put("registrationId", registrationId);
                        return result;
                    }
                } catch (Exception ignored) {
                    // Registration not found, continue checking
                }
            }
        }

        result.put("provider", null);
        result.put("registrationId", null);
        return result;
    }
}
